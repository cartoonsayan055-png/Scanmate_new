# ScanMate AI Pro

ScanMate AI Pro is a premium offline-first Android document scanner built with Kotlin, Jetpack Compose, Material 3, CameraX, Room, ML Kit OCR/barcode scanning, encrypted vault storage, widgets, and export tools.

## Features

- Multi-page document scanning with CameraX
- Real-time scanner guide and edge overlay with animated brackets
- Manual capture, auto-edge capture, flash toggle, gallery import, and batch scan sessions
- Page editor with crop, perspective correction, rotate, filters, watermark, signature, duplicate, delete, and reorder actions
- English OCR with post-processing, reading-order cleanup, and searchable text storage
- PDF export with A4/Letter/Auto sizing, compression options, searchable text layer support, and password protection
- DOCX and TXT export for OCR text
- ZIP backup with `manifest.json` containing file metadata
- QR scanner and QR generator for text, URL, Wi-Fi, contact, email, SMS, and phone payloads
- Encrypted local vault with Android BiometricPrompt and AES-256-GCM Android Keystore storage
- Home screen widgets for quick scan, recent document, and AI action
- Optional user-key Gemini AI workspace; core scanner features work fully offline
- Material 3 UI with light/dark/dynamic color support
- Settings security audit for biometric, vault, encryption, and last unlock status

## Screenshots

Add final Play Store screenshots here before release:

| Home | Scanner | Page Editor | Export | Vault |
| --- | --- | --- | --- | --- |
| `docs/screenshots/home.png` | `docs/screenshots/scanner.png` | `docs/screenshots/editor.png` | `docs/screenshots/export.png` | `docs/screenshots/vault.png` |

## Tech Stack

- Kotlin + Jetpack Compose
- Material 3 / Material You dynamic color
- Hilt dependency injection
- Room with migrations
- DataStore Preferences for app settings
- CameraX
- ML Kit Text Recognition and Barcode Scanning
- WorkManager foundation for background OCR jobs
- FileProvider for safe sharing
- Android Keystore AES-256-GCM vault encryption
- GitHub Actions CI/CD

## Build Instructions

```bash
chmod +x ./gradlew
./gradlew clean :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Release build requires signing secrets or a local `keystore.properties` file:

```properties
storeFile=keystore/scanmate-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=scanmate-key
keyPassword=YOUR_KEY_PASSWORD
```

Then run:

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

## GitHub Actions Signing Setup

Add these repository secrets:

- `SIGNING_KEY`: base64 encoded `.jks`
- `KEY_STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The workflow runs lint, unit tests, emulator tests, debug build, signed release APK/AAB build, signature verification, and artifact upload.

## Privacy

ScanMate AI Pro is offline-first. Scans, OCR text, exports, QR history, and vault files remain on the device unless the user explicitly shares or exports them. Optional Gemini AI calls only run when the user adds their own API key and triggers an AI action.

Privacy policy: <https://ahmedfaizan931star-dev.github.io/scanmate-ai-pro-site/privacy.html>

## Repository Topics

`android`, `kotlin`, `document-scanner`, `ocr`, `material-design`, `jetpack`, `offline-first`

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Code of Conduct

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Licence

Add your chosen licence before publishing the source publicly. If you do not want external reuse, keep the repository private or add an “All rights reserved” notice.
