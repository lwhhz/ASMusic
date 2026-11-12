#!/usr/bin/env bash
# Build + install the App Store replica debug APK.
# Run from Git Bash / MSYS on Windows. Override the environment variables below if needed.
set -euo pipefail

PROJ="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-C:/Users/Administrator/AppData/Local/Android/Sdk}"
ANDROID_SDK_ROOT="$ANDROID_HOME"
JAVA_HOME="${JAVA_HOME:-C:/Users/Administrator/.jdks/jbr-21.0.11}"
GRADLE_BIN="${GRADLE_BIN:-$PROJ/gradlew}"

export ANDROID_HOME ANDROID_SDK_ROOT JAVA_HOME

echo "==> Building debug APK"
"$GRADLE_BIN" -p "$PROJ" :androidApp:assembleDebug --console=plain

APK="$PROJ/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
if [ ! -f "$APK" ]; then
  echo "!! APK not found at $APK" >&2
  exit 1
fi
echo "==> APK: $APK"

if [ "${SKIP_INSTALL:-0}" = "1" ]; then
  echo "==> SKIP_INSTALL=1, done."
  exit 0
fi

ADB="$ANDROID_HOME/platform-tools/adb.exe"
echo "==> Installing to device"
"$ADB" install -r "$APK"
echo "==> Launching"
"$ADB" shell am start -n com.dsh.appstore/com.kyant.backdrop.catalog.MainActivity
echo "==> Done"
