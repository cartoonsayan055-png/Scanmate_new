# GitHub Actions Build Guide — ScanMate AI Pro

This guide explains how to build ScanMate AI Pro after uploading the project to GitHub.

## Recommended repository upload structure

The repository root should contain:

- `app/`
- `.github/workflows/android-build.yml`
- `build.gradle` or root Gradle build file
- `settings.gradle`
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/wrapper/gradle-wrapper.jar`

Do not upload the ZIP file alone. Extract the ZIP first and upload the extracted project contents.

## Workflow behavior

The Android workflow is expected to:

1. Check out repository code.
2. Set up a JDK.
3. Set up Gradle caching.
4. Install or use a clean Gradle runtime.
5. Build debug APK.
6. Build release APK.
7. Build release AAB.
8. Upload generated artifacts.

## Why the wrapper was changed

The earlier project had a corrupted wrapper JAR. The Phase 4 package keeps a valid fallback wrapper launcher and keeps GitHub Actions protected by using clean Gradle setup in CI.

## Common build issues and fixes

### Problem: `gradlew` permission denied

Run:

```bash
chmod +x gradlew
```

### Problem: corrupted wrapper JAR

Use GitHub Actions clean Gradle setup or regenerate wrapper from a trusted Gradle installation:

```bash
gradle wrapper --gradle-version 8.9
```

### Problem: Android plugin cannot resolve

Check network access and Gradle repositories. GitHub Actions usually resolves dependencies correctly when internet access is available.

### Problem: Kotlin compile error

Open the exact file and line shown by CI. Do not guess. Fix unresolved imports, changed APIs, or syntax errors.

### Problem: Room migration error

Increment database version only when schema changes. Add safe migrations or destructive fallback only if data loss is acceptable. For production apps, prefer explicit migrations.

## Artifact check

After workflow succeeds, download:

- Debug APK
- Release APK
- Release AAB

Install the debug APK for testing. Keep release artifacts for signing and Play Store preparation.
