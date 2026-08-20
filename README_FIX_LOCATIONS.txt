ScanMate AI Pro v1.6.3 compile fix

Exact error fixed:
1) QrScannerScreen.kt unresolved reference SafeLogger
2) DocumentDetailViewModel.kt unresolved reference getWorkInfoByIdFlow

Files included:
1) ADD/REPLACE:
   app/src/main/java/com/synthbyte/scanmate/utils/SafeLogger.kt

2) PATCH using script:
   apply_scanmate_v163_compile_fix.sh

What the script changes safely:
- Adds/updates SafeLogger.kt
- Adds this import to QrScannerScreen.kt if needed:
  import com.synthbyte.scanmate.utils.SafeLogger
- Adds this import to DocumentDetailViewModel.kt if needed:
  import androidx.work.getWorkInfoByIdFlow
- Adds this dependency to app/build.gradle.kts if missing:
  implementation("androidx.work:work-runtime-ktx:2.10.1")

How to use in Codespaces:
1) Upload/extract this ZIP in repo root.
2) Run:
   bash apply_scanmate_v163_compile_fix.sh
3) Verify:
   ./gradlew :app:lintDebug --stacktrace
4) Commit/push:
   git add .
   git commit -m "Fix v1.6.3 compile errors"
   git push origin main
