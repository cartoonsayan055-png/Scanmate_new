#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Project: $(pwd)"
echo "Java:"
java -version || true

echo "Gradle launcher:"
ls -lah ./gradlew gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties || true
jar tf gradle/wrapper/gradle-wrapper.jar >/dev/null 2>&1 && echo "Wrapper JAR: OK" || echo "Wrapper JAR: missing/corrupt; Colab fallback launcher will download Gradle."

echo "Android env:"
echo "ANDROID_HOME=${ANDROID_HOME:-not set}"
echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-not set}"

echo "Expected outputs after build:"
echo "- app/build/outputs/apk/debug/app-debug.apk"
echo "- app/build/outputs/apk/release/app-release.apk or app-release-unsigned.apk"
echo "- app/build/outputs/bundle/release/app-release.aab"
