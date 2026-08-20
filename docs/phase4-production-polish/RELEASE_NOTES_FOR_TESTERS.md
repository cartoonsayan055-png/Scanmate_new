# Release Notes for Testers — Phase 4 No-Shrink Package

This build focuses on stabilization and production polish rather than random feature expansion.

## Areas to test carefully

- CameraX scanner
- QR scanner
- PDF export
- OCR and OCR translation
- Widget launch actions
- Launcher shortcuts
- Page editor drag/reorder behavior
- Large image import
- ZIP export

## Expected behavior without internet

The app should remain useful offline. OCR should work through local ML Kit behavior where supported. AI features should use offline fallbacks instead of crashing or blocking the user.

## Expected behavior without API key

The AI workspace and translation features should show friendly fallback text and manual tools. They should not crash and should not require a hardcoded key.

## Known validation limits

Static checks can verify file structure and obvious syntax risks, but camera hardware, launcher widgets, Android file providers, and third-party document viewers require real device testing.
