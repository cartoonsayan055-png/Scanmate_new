# Changelog

## v1.0.0 — Production Readiness Baseline

- Added Play Store readiness documentation and release workflow structure.
- Confirmed target/compile SDK 35 configuration.
- Added FileProvider sharing, adaptive icon layers, and SplashScreen API theme.
- Added CI stages for lint, unit tests, emulator tests, debug build, signed APK/AAB build, signature verification, and artifacts.

## v1.6.0 — Security, UX, CI, and Export Upgrade

- Added debug-only `SafeLogger` and removed direct release log output paths.
- Added `FLAG_SECURE` protection for vault/settings sensitive surfaces.
- Upgraded vault envelope to a versioned binary AES-256-GCM format while keeping legacy vault read support.
- Added StrongBox-backed key generation attempt with Android Keystore fallback.
- Added Settings security audit card with biometric, encryption, vault count, and last unlock status.
- Added WorkManager OCR worker foundation with progress data and optional progress notification.
- Added ZIP `manifest.json` generation with file type, path, size, timestamp, and OCR confidence placeholder.
- Added Material You dynamic color support and app-level design token objects.
- Added repository/use-case layer scaffolding for document operations.
- Added LeakCanary debug dependency, Jacoco coverage task, JUnit5/MockK/Turbine dependencies, and ktlint plugin configuration.
- Cleaned repository root by moving logs, patches, hashes, TSVs, and historical reports into `/build-history/`.
