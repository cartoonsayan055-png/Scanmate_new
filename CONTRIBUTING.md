# Contributing to ScanMate AI Pro

Thanks for helping improve ScanMate AI Pro.

## Development Rules

- Keep the app offline-first.
- Do not remove existing scanner, OCR, QR, vault, export, widget, or settings features.
- Keep public function names compatible unless a migration is unavoidable.
- Use Kotlin coroutines, StateFlow/SharedFlow, Hilt, Room, and DataStore.
- Perform file I/O on `Dispatchers.IO`.
- Share files only through `FileProvider`.
- Never log API keys, vault metadata, OCR text, or user file paths in release builds.

## Before Opening a PR

```bash
./gradlew ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:jacocoTestReport :app:assembleDebug
```

For release changes, verify:

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

## UI Standards

- Use Material 3 components.
- Keep minimum touch targets at 48dp.
- Add content descriptions for interactive controls.
- Test light mode, dark mode, portrait, and landscape.

## Security Standards

- Vault code must use Android Keystore AES-256-GCM.
- Sensitive screens must apply `FLAG_SECURE`.
- Release logging must stay behind `BuildConfig.DEBUG`.
- New network clients must define explicit TLS policy and avoid cleartext.
