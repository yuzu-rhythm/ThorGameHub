#!/usr/bin/env bash
set -euo pipefail

if [[ "${THOR_RUN_CONNECTED_TESTS:-0}" != "0" ]]; then
  echo "This portfolio verifier is host-only; connected tests are disabled." >&2
  exit 2
fi

thor_java_home="${JAVA_HOME:-}"
if [[ -z "$thor_java_home" ]] && [[ -x /usr/libexec/java_home ]]; then
  thor_java_home="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
if [[ -z "$thor_java_home" ]] && [[ -d /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ]]; then
  thor_java_home="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
fi

thor_android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$thor_android_home" ]] && [[ -d "$HOME/Library/Android/sdk" ]]; then
  thor_android_home="$HOME/Library/Android/sdk"
fi

if [[ ! -x "$thor_java_home/bin/java" ]]; then
  echo "JDK 17 not found. Set JAVA_HOME to a JDK 17 installation." >&2
  exit 2
fi
if [[ ! -d "$thor_android_home/platforms/android-37.0" ]]; then
  echo "Android SDK Platform 37.0 not found. Set ANDROID_HOME/ANDROID_SDK_ROOT correctly." >&2
  exit 2
fi
if [[ ! -d "$thor_android_home/build-tools/36.0.0" ]]; then
  echo "Android SDK Build Tools 36.0.0 not found." >&2
  exit 2
fi

export JAVA_HOME="$thor_java_home"
export ANDROID_HOME="$thor_android_home"

./gradlew clean \
  :core:model:test \
  :core:database:assembleDebug \
  :core:database:compileDebugAndroidTestKotlin \
  :core:data:testDebugUnitTest \
  :app:testStandardDebugUnitTest \
  :app:compileStandardDebugAndroidTestKotlin \
  :app:lintStandardDebug \
  :app:assembleStandardDebug \
  --no-daemon

echo "Connected tests are not run by this portfolio host verifier."

thor_apk="app/build/outputs/apk/standard/debug/app-standard-debug.apk"
if [[ ! -f "$thor_apk" ]]; then
  echo "Expected APK not found: $thor_apk" >&2
  exit 3
fi

shasum -a 256 "$thor_apk"
echo "Verified APK: $thor_apk"
