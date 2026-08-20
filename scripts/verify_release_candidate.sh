#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

fail() {
  echo "::error::$1" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "Missing required file: $1"
}

require_file app/src/main/java/com/synthbyte/scanmate/utils/DocxExporter.kt
require_file app/src/main/java/com/synthbyte/scanmate/util/EdgeAnalyzer.kt
require_file app/src/main/java/com/synthbyte/scanmate/utils/ImageProcessor.kt
require_file app/src/main/java/com/synthbyte/scanmate/utils/OcrHelper.kt
require_file app/proguard-rules.pro
require_file .github/workflows/android-build.yml

if grep -R -nE 'XWPFDocument|poi-ooxml|org\.apache\.poi' \
  app/src/main app/build.gradle.kts gradle/libs.versions.toml >/tmp/scanmate_poi_refs.txt 2>/dev/null; then
  cat /tmp/scanmate_poi_refs.txt >&2
  fail "Apache POI/XWPF runtime references found in production code or Gradle config."
fi

grep -q 'ZipOutputStream' app/src/main/java/com/synthbyte/scanmate/utils/DocxExporter.kt || fail "DOCX exporter is not using ZipOutputStream."
grep -q 'word/document.xml' app/src/main/java/com/synthbyte/scanmate/utils/DocxExporter.kt || fail "DOCX package writer is missing word/document.xml."
grep -q 'sampleLuma' app/src/main/java/com/synthbyte/scanmate/util/EdgeAnalyzer.kt || fail "EdgeAnalyzer is missing luma-frame analysis."
grep -q 'smoothedCorners' app/src/main/java/com/synthbyte/scanmate/util/EdgeAnalyzer.kt || fail "EdgeAnalyzer is missing temporal smoothing."
grep -q 'runBestTextRecognition' app/src/main/java/com/synthbyte/scanmate/utils/OcrHelper.kt || fail "OCR multi-candidate pipeline missing."
grep -q 'SCANMATE_REQUIRE_RELEASE_SIGNING' .github/workflows/android-build.yml || fail "Strict release signing check missing from workflow."

echo "ScanMate release-candidate sanity checks passed."
