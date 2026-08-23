/*
 * Minimal PTY bridge for Athea.
 *
 * Android exposes no public API for attaching a child process to a
 * pseudo-terminal, so every terminal emulator ships a tiny native shim.
 * This file is that shim: forkpty + execve of the system shell, plus
 * blocking read/write helpers.
 *
 * Echo is disabled on the master side so the UI can render command
 * bubbles without duplicated input. The shell receives an ENV variable
 * pointing at the app-provided rc file, which installs shell-integration
 * marks (OSC 133) so the app can locate command boundaries.
 */
#include <jni.h>
#include <pty.h>
#include <unistd.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <termios.h>
#include <signal.h>
#include <sys/ioctl.h>

static void throw_io_exception(JNIEnv *env, const char *message) {
    jclass cls = (*env)->FindClass(env, "java/io/IOException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, message);
    }
}

JNIEXPORT jobjectArray JNICALL
Java_com_athea_app_engine_PtyBridge_createPty(JNIEnv *env, jobject thiz,
                                              jint rows, jint cols,
                                              jstring home_path,
                                              jstring rc_path) {
    (void) thiz;
    const char *home = (*env)->GetStringUTFChars(env, home_path, NULL);
    if (home == NULL) {
        return NULL;
    }
    const char *rc = (*env)->GetStringUTFChars(env, rc_path, NULL);
    if (rc == NULL) {
        (*env)->ReleaseStringUTFChars(env, home_path, home);
        return NULL;
    }

    int master = -1;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;

    /*
     * Per-invocation stack storage: after fork() the child owns a private
     * copy of the address space, so parent stack reuse cannot corrupt it.
     */
    char home_entry[512];
    char tmpdir_entry[512];
    char env_entry[512];
    char path_entry[] = "PATH=/data/local/tmp/bin:/system/bin:/system/xbin:/vendor/bin";
    char shell_entry[] = "SHELL=/system/bin/sh";
    char term_entry[] = "TERM=xterm-256color";

    snprintf(home_entry, sizeof(home_entry), "HOME=%s", home);
    snprintf(tmpdir_entry, sizeof(tmpdir_entry), "TMPDIR=%s", home);
    snprintf(env_entry, sizeof(env_entry), "ENV=%s", rc);

    pid_t pid = forkpty(&master, NULL, NULL, &ws);
    if (pid < 0) {
        (*env)->ReleaseStringUTFChars(env, rc_path, rc);
        (*env)->ReleaseStringUTFChars(env, home_path, home);
        throw_io_exception(env, "forkpty failed");
        return NULL;
    }

    if (pid == 0) {
        char *argv[] = { (char *) "sh", (char *) "-l", NULL };
        char *envp[] = {
            home_entry,
            tmpdir_entry,
            env_entry,
            path_entry,
            shell_entry,
            term_entry,
            NULL
        };
        execve("/system/bin/sh", argv, envp);
        _exit(127);
    }

    /* Parent: suppress echo so submitted commands are not doubled. */
    struct termios tio;
    if (tcgetattr(master, &tio) == 0) {
        tio.c_lflag &= ~(ECHO | ECHOE | ECHOK);
        tcsetattr(master, TCSANOW, &tio);
    }

    (*env)->ReleaseStringUTFChars(env, rc_path, rc);
    (*env)->ReleaseStringUTFChars(env, home_path, home);

    jintArray out = (*env)->NewIntArray(env, 2);
    if (out == NULL) {
        return NULL;
    }
    jint vals[2];
    vals[0] = (jint) master;
    vals[1] = (jint) pid;
    (*env)->SetIntArrayRegion(env, out, 0, 2, vals);
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_athea_app_engine_PtyBridge_writePty(JNIEnv *env, jobject thiz,
                                             jint fd, jbyteArray data) {
    (void) thiz;
    jsize len = (*env)->GetArrayLength(env, data);
    if (len <= 0) {
        return JNI_TRUE;
    }
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    if (buf == NULL) {
        return JNI_FALSE;
    }
    jsize off = 0;
    while (off < len) {
        ssize_t n = write((int) fd, buf + off, (size_t)(len - off));
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
            throw_io_exception(env, "write failed");
            return JNI_FALSE;
        }
        off += (jsize) n;
    }
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_athea_app_engine_PtyBridge_readPty(JNIEnv *env, jobject thiz,
                                            jint fd, jbyteArray buffer) {
    (void) thiz;
    jsize cap = (*env)->GetArrayLength(env, buffer);
    if (cap <= 0) {
        return -1;
    }
    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (buf == NULL) {
        return -1;
    }
    ssize_t n;
    do {
        n = read((int) fd, buf, (size_t) cap);
    } while (n < 0 && errno == EINTR);
    /* Mode 0: copy the read bytes back into the Java array. */
    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
    if (n < 0) {
        return -1;
    }
    return (jint) n;
}

JNIEXPORT void JNICALL
Java_com_athea_app_engine_PtyBridge_resizePty(JNIEnv *env, jobject thiz,
                                              jint fd, jint pid,
                                              jint rows, jint cols) {
    (void) env;
    (void) thiz;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    ioctl((int) fd, TIOCSWINSZ, &ws);
    kill((pid_t) pid, SIGWINCH);
}

JNIEXPORT void JNICALL
Java_com_athea_app_engine_PtyBridge_closePty(JNIEnv *env, jobject thiz, jint fd) {
    (void) env;
    (void) thiz;
    close((int) fd);
}
