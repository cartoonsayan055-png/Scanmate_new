# Play Store Production QA Checklist — ScanMate AI Pro

Use this checklist before preparing a signed APK or AAB.

## Build verification

- Run GitHub Actions debug APK build.
- Run GitHub Actions release APK build.
- Run GitHub Actions release AAB build.
- Confirm artifacts upload correctly.
- Confirm the workflow does not require signing secrets for unsigned release artifacts.
- Confirm `minSdk`, `targetSdk`, and compile SDK are aligned with Android 9–16 compatibility goals.
- Confirm no hardcoded API keys exist in source files.
- Confirm AndroidManifest permissions are clear and only required permissions are requested.

## First launch

- Install the debug APK on a real Android phone.
- Open the app from launcher.
- Confirm no crash on cold start.
- Confirm Material You dynamic theme does not crash on Android 12+.
- Confirm older Android versions still use safe fallback theme.
- Confirm navigation works from bottom navigation, cards, widgets, and shortcuts.

## Scanner flow

- Grant camera permission.
- Deny camera permission and verify friendly fallback.
- Reopen scanner after denying and granting permission.
- Capture a document page.
- Import from gallery.
- Test low-light photo.
- Test landscape image.
- Test very large image.
- Confirm no crash during capture, save, preview, or edit.

## Editor flow

- Rotate page.
- Crop page.
- Use manual corner correction.
- Apply filters.
- Apply watermark.
- Add note or stamp.
- Duplicate page.
- Replace page.
- Delete page.
- Reorder pages with drag.
- Reorder pages with fallback move buttons.
- Use undo and redo where available.
- Confirm preview updates are stable.

## OCR flow

- Run OCR on a clear page.
- Run OCR on a blurry page.
- Run OCR on a receipt.
- Run OCR on handwritten-style text where possible.
- Copy OCR text.
- Share OCR text.
- Export OCR as TXT.
- Export OCR as DOCX.
- Save OCR text with document.
- Confirm confidence and quality labels are shown safely.

## Translation flow

- Open OCR Translate.
- Pick each language selector option.
- Translate with no API key.
- Translate with empty OCR input.
- Export translated result.
- Share translated result.
- Confirm no internet is required for basic OCR.

## PDF/export flow

- Export one-page PDF.
- Export multi-page PDF.
- Export compressed PDF.
- Export TXT.
- Export DOCX.
- Export ZIP backup.
- Share exported files.
- Open exported files with external apps.
- Confirm generated PDF is not corrupted.
- Confirm filenames are safe and readable.

## QR flow

- Generate URL QR.
- Generate email QR.
- Generate phone QR.
- Generate SMS QR.
- Generate Wi-Fi QR.
- Generate vCard QR.
- Generate MECARD QR.
- Scan URL QR.
- Scan vCard/MECARD QR.
- Test contact preview.
- Test save contact intent.
- Confirm malformed QR data does not crash the app.

## Widgets and shortcuts

- Add Quick Scan widget.
- Add Recent Document widget.
- Add AI Action widget.
- Tap widgets when there are no documents.
- Tap widgets when documents exist.
- Use launcher shortcut for scan.
- Use launcher shortcut for AI.
- Use launcher shortcut for QR.
- Confirm routing works when app is cold-started and already open.

## Organization and analytics

- Create workspace/folder metadata.
- Favorite a document.
- Pin a document.
- Search documents.
- Filter favorites.
- Filter pinned documents.
- Confirm empty states are friendly.
- Confirm analytics screen opens with zero data.
- Confirm analytics screen updates after scans/exports/OCR actions.

## Play Store readiness

- Prepare a privacy policy.
- Explain camera, storage, and optional AI usage.
- Avoid claiming cloud features if cloud sync is not implemented.
- Avoid claiming login/subscription features if not implemented.
- Use accurate screenshots from a real device.
- Test release AAB before upload.
