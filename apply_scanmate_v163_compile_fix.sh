#!/usr/bin/env bash
set -euo pipefail

echo "✅ Applying ScanMate v1.6.3 compile fixes..."

SAFE_LOGGER_SRC="app/src/main/java/com/synthbyte/scanmate/utils/SafeLogger.kt"
QR_FILE="app/src/main/java/com/synthbyte/scanmate/ui/screens/QrScannerScreen.kt"
VM_FILE="app/src/main/java/com/synthbyte/scanmate/ui/viewmodels/DocumentDetailViewModel.kt"
APP_GRADLE_KTS="app/build.gradle.kts"
APP_GRADLE_GROOVY="app/build.gradle"

# 1) Ensure SafeLogger.kt exists
mkdir -p "$(dirname "$SAFE_LOGGER_SRC")"
cat > "$SAFE_LOGGER_SRC" <<'KOTLIN'
package com.synthbyte.scanmate.utils

import android.util.Log

/**
 * Small safe logging wrapper used by UI and scanner screens.
 *
 * Keep this object lightweight and dependency-free so it works in Debug and Release builds.
 * Do not log secrets, API keys, file contents, document OCR text, or user-private data here.
 */
object SafeLogger {
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
KOTLIN

echo "✅ SafeLogger.kt created/updated"

# 2) Ensure QrScannerScreen imports SafeLogger if the file uses it
if [[ -f "$QR_FILE" ]] && grep -q "SafeLogger" "$QR_FILE" && ! grep -q "import com.synthbyte.scanmate.utils.SafeLogger" "$QR_FILE"; then
  python3 - <<'PY'
from pathlib import Path
p = Path("app/src/main/java/com/synthbyte/scanmate/ui/screens/QrScannerScreen.kt")
s = p.read_text()
lines = s.splitlines()
# Insert after the package line and existing blank line, before other imports if possible.
insert = "import com.synthbyte.scanmate.utils.SafeLogger"
if insert not in s:
    idx = 0
    for i, line in enumerate(lines):
        if line.startswith("import "):
            idx = i
            break
    else:
        idx = 1
    lines.insert(idx, insert)
    p.write_text("\n".join(lines) + "\n")
PY
  echo "✅ QrScannerScreen.kt SafeLogger import added"
else
  echo "ℹ️ QrScannerScreen.kt import already OK or file not found"
fi

# 3) Ensure DocumentDetailViewModel has WorkManager KTX import if needed
if [[ -f "$VM_FILE" ]] && grep -q "getWorkInfoByIdFlow" "$VM_FILE" && ! grep -q "import androidx.work.getWorkInfoByIdFlow" "$VM_FILE"; then
  python3 - <<'PY'
from pathlib import Path
p = Path("app/src/main/java/com/synthbyte/scanmate/ui/viewmodels/DocumentDetailViewModel.kt")
s = p.read_text()
lines = s.splitlines()
insert = "import androidx.work.getWorkInfoByIdFlow"
if insert not in s:
    idx = 0
    for i, line in enumerate(lines):
        if line.startswith("import "):
            idx = i
            break
    else:
        idx = 1
    lines.insert(idx, insert)
    p.write_text("\n".join(lines) + "\n")
PY
  echo "✅ DocumentDetailViewModel.kt WorkManager Flow import added"
else
  echo "ℹ️ DocumentDetailViewModel.kt import already OK or file not found"
fi

# 4) Ensure WorkManager KTX dependency exists
if [[ -f "$APP_GRADLE_KTS" ]]; then
  if ! grep -q "androidx.work:work-runtime-ktx" "$APP_GRADLE_KTS"; then
    python3 - <<'PY'
from pathlib import Path
p = Path("app/build.gradle.kts")
s = p.read_text()
dep = '    implementation("androidx.work:work-runtime-ktx:2.10.1")'
if dep.strip() not in s:
    marker = "dependencies {"
    if marker not in s:
        raise SystemExit("❌ dependencies block not found in app/build.gradle.kts")
    s = s.replace(marker, marker + "\n" + dep, 1)
    p.write_text(s)
PY
    echo "✅ WorkManager KTX dependency added to app/build.gradle.kts"
  else
    echo "ℹ️ WorkManager KTX dependency already exists in app/build.gradle.kts"
  fi
elif [[ -f "$APP_GRADLE_GROOVY" ]]; then
  if ! grep -q "androidx.work:work-runtime-ktx" "$APP_GRADLE_GROOVY"; then
    python3 - <<'PY'
from pathlib import Path
p = Path("app/build.gradle")
s = p.read_text()
dep = "    implementation 'androidx.work:work-runtime-ktx:2.10.1'"
if dep.strip() not in s:
    marker = "dependencies {"
    if marker not in s:
        raise SystemExit("❌ dependencies block not found in app/build.gradle")
    s = s.replace(marker, marker + "\n" + dep, 1)
    p.write_text(s)
PY
    echo "✅ WorkManager KTX dependency added to app/build.gradle"
  else
    echo "ℹ️ WorkManager KTX dependency already exists in app/build.gradle"
  fi
else
  echo "❌ app/build.gradle.kts or app/build.gradle not found"
  exit 1
fi

echo "✅ Running quick verification..."
test -f "$SAFE_LOGGER_SRC"
grep -R "androidx.work:work-runtime-ktx" app/build.gradle.kts app/build.gradle 2>/dev/null || { echo "❌ WorkManager dependency missing"; exit 1; }

echo "✅ Done. Now run: ./gradlew :app:lintDebug --stacktrace"
