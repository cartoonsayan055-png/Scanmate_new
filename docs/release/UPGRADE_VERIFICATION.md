# ScanMate AI Pro v1.6.0 Upgrade Verification

## Applied upgrade scope

This source package includes direct project changes for UI theming, secure vault hardening, background OCR, export reliability, repository/use-case scaffolding, tests, CI/CD, documentation, and Play Store readiness.

## Local verification performed in this sandbox

- Project ZIP extracted successfully.
- Android XML resources parsed successfully with Python XML parsing.
- Raw `android.util.Log` / `Log.*` usage is now isolated to `SafeLogger`, which is guarded by `BuildConfig.DEBUG`.
- Font binaries were removed from the deliverable and typography now uses the Android system font family.
- Root artifact cleanup was applied and historical generated files were moved to `build-history/`.

## Build verification limitation

The Gradle wrapper could not be executed in this sandbox because it needs to download Gradle 8.9 from `services.gradle.org`, and this runtime has no outbound internet for container commands. The new GitHub Actions workflow is configured to run lint, ktlint, unit tests, emulator tests, debug build, signed release APK, signed release AAB, and artifact upload on GitHub where dependencies can be downloaded.

## Recommended GitHub verification command

```bash
./gradlew --no-daemon :app:lintDebug ktlintCheck :app:testDebugUnitTest :app:jacocoTestReport :app:assembleDebug
```

For signed release verification in CI, set these secrets: `ANDROID_KEYSTORE_BASE64` (or legacy `SIGNING_KEY`), `KEY_STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
