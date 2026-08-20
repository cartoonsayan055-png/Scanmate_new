#!/usr/bin/env bash
# Colab-safe Gradle launcher for ScanMate AI Pro.
# It uses the normal Gradle wrapper JAR when valid. If the uploaded ZIP contains
# a corrupted wrapper JAR, it falls back to downloading the Gradle distribution
# declared in gradle/wrapper/gradle-wrapper.properties and runs Gradle directly.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WRAPPER_JAR="$ROOT_DIR/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="$ROOT_DIR/gradle/wrapper/gradle-wrapper.properties"

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java/JDK is not installed or JAVA_HOME is not configured." >&2
  echo "In Google Colab, run: apt-get update && apt-get install -y openjdk-17-jdk" >&2
  exit 1
fi

if [ -f "$WRAPPER_JAR" ] && jar tf "$WRAPPER_JAR" >/dev/null 2>&1; then
  exec java -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi

echo "Gradle wrapper JAR is missing or corrupted." >&2
if command -v gradle >/dev/null 2>&1; then
  echo "Using Gradle installed on this machine." >&2
  exec gradle "$@"
fi

echo "No system Gradle found. Using fallback launcher to download the declared Gradle distribution..." >&2
if [ ! -f "$WRAPPER_PROPS" ]; then
  echo "ERROR: $WRAPPER_PROPS not found." >&2
  exit 1
fi

DIST_URL="$(grep '^distributionUrl=' "$WRAPPER_PROPS" | cut -d= -f2- | sed 's#\\:#:#g')"
DIST_FILE="${DIST_URL##*/}"
DIST_NAME="${DIST_FILE%.zip}"
FALLBACK_HOME="$ROOT_DIR/.gradle-colab"
ZIP_PATH="$FALLBACK_HOME/$DIST_FILE"
GRADLE_BIN="$FALLBACK_HOME/$DIST_NAME/bin/gradle"

mkdir -p "$FALLBACK_HOME"

if [ ! -x "$GRADLE_BIN" ]; then
  echo "Downloading $DIST_URL" >&2
  if command -v curl >/dev/null 2>&1; then
    curl -L --retry 3 --connect-timeout 20 -o "$ZIP_PATH" "$DIST_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP_PATH" "$DIST_URL"
  else
    echo "ERROR: curl or wget is required to download Gradle." >&2
    exit 1
  fi
  unzip -q -o "$ZIP_PATH" -d "$FALLBACK_HOME"
fi

exec "$GRADLE_BIN" "$@"
