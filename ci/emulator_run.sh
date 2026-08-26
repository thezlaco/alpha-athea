#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="emulator_logs"
mkdir -p "$LOG_DIR"

collect_on_exit() {
  adb devices -l > "$LOG_DIR/adb_devices_exit.txt" 2>&1 || true
  timeout 10 adb logcat -d > "$LOG_DIR/logcat.txt" 2>&1 || true
}

trap collect_on_exit EXIT

# Runner (android-emulator-runner) already waits for boot via
# emulator-boot-timeout. Keep only a quick sanity check (10s max)
# instead of the old 300s loop that wasted minutes on every run.
if ! timeout 15 adb wait-for-device; then
  echo "ADB_WAIT_TIMEOUT" > "$LOG_DIR/boot_error.txt"
  exit 1
fi
boot_completed=$(timeout 5 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || echo "")
if [ "$boot_completed" != "1" ]; then
  echo "BOOT_NOT_COMPLETED=$boot_completed" > "$LOG_DIR/boot_error.txt"
  # Don't fail hard — runner already booted, let install try
  sleep 2
fi

APK="${APK_PATH:?APK_PATH_NOT_SET}"

if ! adb install -r "$APK" > "$LOG_DIR/install_log.txt" 2>&1; then
  echo "INSTALL_FAILED" > "$LOG_DIR/install_error.txt"
  exit 1
fi

BADGING="$(aapt2 dump badging "$APK" 2>/dev/null || true)"
PKG="$(printf '%s' "$BADGING" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
MAINACT="$(printf '%s' "$BADGING" | sed -n "s/^launchable-activity: name='\([^']*\)'.*/\1/p")"

# Fallbacks consistent with app/build.gradle.kts.
PKG="${PKG:-com.athea.app}"
MAINACT="${MAINACT:-com.athea.app.MainActivity}"

printf 'PKG=%s\nMAINACT=%s\n' "$PKG" "$MAINACT" > "$LOG_DIR/pkg.txt"

timeout 5 adb logcat -c || true

if timeout 15 adb shell am start -W -n "${PKG}/${MAINACT}" > "$LOG_DIR/launch_log.txt" 2>&1; then
  :
else
  timeout 10 adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 \
    > "$LOG_DIR/launch_log.txt" 2>&1 || true
fi

sleep 5

timeout 10 adb shell screencap -p /sdcard/screen_launch.png || true
timeout 10 adb pull /sdcard/screen_launch.png "$LOG_DIR/screen_launch.png" || true

# Drive a real command through the UI, then dump the app journal:
# use fixed coords with fallback — vm size parsing can hang.
W=1080
H=2400
if WmOut=$(timeout 5 adb shell wm size 2>/dev/null); then
  WmParsed=$(echo "$WmOut" | grep -oE '[0-9]+x[0-9]+' | head -1 || true)
  if [ -n "$WmParsed" ]; then
    W=$(echo "$WmParsed" | cut -d'x' -f1)
    H=$(echo "$WmParsed" | cut -d'x' -f2)
  fi
fi
timeout 5 adb shell input tap "$((W / 2))" "$((H - 220))" || true
sleep 1
timeout 5 adb shell input text "echo%sathea-smoke" || true
sleep 1
timeout 5 adb shell input tap "$((W - 90))" "$((H - 220))" || true
sleep 4
# run-as only works for debuggable builds (debug APK); ignore failure for release
timeout 10 adb shell run-as "$PKG" sh -c 'cat files/sessions/*/journal.log 2>/dev/null; echo "---index---"; cat files/sessions/index.json 2>/dev/null' \
  > "$LOG_DIR/app_journal.txt" 2>&1 || echo "run-as failed (release build or no data)" >> "$LOG_DIR/app_journal.txt"
timeout 10 adb shell screencap -p /sdcard/screen_after.png || true
timeout 10 adb pull /sdcard/screen_after.png "$LOG_DIR/screen_after.png" || true

for i in $(seq 1 10); do
  if ! timeout 5 adb shell pidof "$PKG" > /dev/null 2>&1; then
    echo "CRASH_DETECTED_AT_SECOND_${i}" >> "$LOG_DIR/crash_status.txt"
    break
  fi
  sleep 1
done

[ -f "$LOG_DIR/crash_status.txt" ] || echo "NO_CRASHES_DETECTED" > "$LOG_DIR/crash_status.txt"

exit 0
