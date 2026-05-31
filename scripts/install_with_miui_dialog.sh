#!/usr/bin/env bash
# install_with_miui_dialog.sh
# Build + install on a HyperOS / MIUI phone that's auto-denying ADB installs.
#
# Background:
#   Without "Install via USB" enabled in developer options (which itself requires a
#   Xiaomi account + active SIM data), MIUI's `com.miui.securitycenter.AdbInstallActivity`
#   pops a confirmation dialog with a 6-second auto-deny timer. If you don't tap
#   "Install" in time you get `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`.
#
#   This script auto-taps "Remember my choice" + "Install" via `adb shell input tap`.
#   After the first successful install with the box ticked, future installs go silent.
#
# Coordinates below are for the Redmi Note 14 5G (1080×2400). For another phone, run:
#   adb shell uiautomator dump /sdcard/d.xml && adb pull /sdcard/d.xml /tmp/d.xml
#   grep -oE 'text="[^"]+"[^>]*bounds="[^"]+"' /tmp/d.xml | grep -i install

set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK"
  echo "Usage: $0 [path/to.apk]    (default: app/build/outputs/apk/debug/app-debug.apk)"
  exit 1
fi

# Build it if requested
if [[ "${1:-}" == "--build" ]]; then
  ./gradlew assembleDebug
  APK="app/build/outputs/apk/debug/app-debug.apk"
fi

REMOTE=/data/local/tmp/__cc_install.apk
echo "▶ Pushing $(basename "$APK") to phone..."
adb push "$APK" "$REMOTE" > /dev/null

echo "▶ Triggering install (will auto-tap MIUI dialog)..."
(adb shell "pm install -r -t $REMOTE" 2>&1) > /tmp/__cc_install.out &
INSTALL_PID=$!

# Wait for the MIUI dialog to render
sleep 2.2

# Coordinates verified for THIS phone (Redmi Note 14 5G, 1080x2400, HyperOS)
# on 2026-05-30 via uiautomator dump. The portrait "Install via USB" dialog:
#   "Remember my choice" checkbox row : bounds [131,1940][948,2012] -> tap left ~ (165,1976)
#   "Install" button (button2)        : bounds [98,2034][523,2172]  -> center  (310,2103)
#   "Deny (N)" button (button1)       : bounds [556,2034][981,2172]
# Tap "Remember my choice" checkbox (skip if not present — harmless)
adb shell input tap 165 1976 || true
sleep 0.4
# Tap the "Install" button
adb shell input tap 310 2103 || true

wait $INSTALL_PID
OUT=$(cat /tmp/__cc_install.out)
echo "  $OUT"

adb shell rm -f "$REMOTE" > /dev/null
rm -f /tmp/__cc_install.out

if echo "$OUT" | grep -q "Success"; then
  echo "✅ Install succeeded"
  exit 0
else
  echo "❌ Install failed — see output above"
  exit 1
fi
