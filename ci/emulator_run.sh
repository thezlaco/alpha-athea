#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="emulator_logs"
mkdir -p "$LOG_DIR"

collect_on_exit() {
  adb devices -l > "$LOG_DIR/adb_devices_exit.txt" 2>&1 || true
  adb logcat -d > "$LOG_DIR/logcat.txt" 2>&1 || true
}

trap collect_on_exit EXIT

adb wait-for-device

boot_completed=""
for i in $(seq 1 300); do
  boot_completed=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  anim_stopped=$(adb shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')
  if [ "$boot_completed" = "1" ] && [ "$anim_stopped" = "stopped" ]; then
    break
  fi
  sleep 1
done

if [ "$boot_completed" != "1" ]; then
  echo "BOOT_TIMEOUT" > "$LOG_DIR/boot_error.txt"
  exit 1
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

adb logcat -c || true

if adb shell am start -W -n "${PKG}/${MAINACT}" > "$LOG_DIR/launch_log.txt" 2>&1; then
  :
else
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 \
    > "$LOG_DIR/launch_log.txt" 2>&1 || true
fi

sleep 10

adb shell screencap -p /sdcard/screen_launch.png
adb pull /sdcard/screen_launch.png "$LOG_DIR/screen_launch.png" || true

# Drive a real command through the UI, then dump the app journal:
# the journal distinguishes "submit broken" (no cmd record) from
# "engine write broken" (cmd without out) from "working" (cmd + out).
W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | cut -d'x' -f1)
H=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | cut -d'x' -f2)
adb shell input tap "$((W / 2))" "$((H - 220))" || true
sleep 2
adb shell input text "echo%sathea-smoke" || true
sleep 1
adb shell input tap "$((W - 90))" "$((H - 220))" || true
sleep 5
adb shell run-as com.athea.app sh -c 'cat files/sessions/*/journal.log 2>/dev/null; echo "---index---"; cat files/sessions/index.json 2>/dev/null' \
  > "$LOG_DIR/app_journal.txt" 2>&1 || echo "run-as failed" >> "$LOG_DIR/app_journal.txt"
adb shell screencap -p /sdcard/screen_after.png
adb pull /sdcard/screen_after.png "$LOG_DIR/screen_after.png" || true

for i in $(seq 1 30); do
  if ! adb shell pidof "$PKG" > /dev/null 2>&1; then
    echo "CRASH_DETECTED_AT_SECOND_${i}" >> "$LOG_DIR/crash_status.txt"
    break
  fi
  sleep 1
done

[ -f "$LOG_DIR/crash_status.txt" ] || echo "NO_CRASHES_DETECTED" > "$LOG_DIR/crash_status.txt"

exit 0
