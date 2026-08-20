#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${PROJECT_DIR:-$(pwd)}"
ANDROID_HOME="${ANDROID_HOME:-/content/android-sdk}"
ANDROID_SDK_ROOT="$ANDROID_HOME"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME ANDROID_SDK_ROOT JAVA_HOME
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

cd "$PROJECT_DIR"
chmod +x ./gradlew

echo "== ScanMate AI Pro Colab Doctor =="
java -version
./gradlew --version --no-daemon || true

echo "== Cleaning =="
./gradlew clean --no-daemon

echo "== Building debug APK =="
./gradlew assembleDebug --no-daemon --stacktrace

echo "== Building release APK =="
./gradlew assembleRelease --no-daemon --stacktrace

echo "== Building release AAB =="
./gradlew bundleRelease --no-daemon --stacktrace

echo "== Outputs =="
find app/build/outputs -type f \( -name '*.apk' -o -name '*.aab' \) -print
