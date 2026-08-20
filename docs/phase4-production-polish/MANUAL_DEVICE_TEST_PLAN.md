# Manual Device Test Plan — ScanMate AI Pro

This plan is for real Android device validation because camera, widget, file-sharing, OCR, and PDF flows cannot be fully proven by static analysis alone.

## Test devices

Recommended device spread:

- Android 9 low-end phone
- Android 10 or 11 average phone
- Android 12+ phone with Material You
- Android 14/15 modern phone
- Large screen or tablet where possible

## Test session 1: cold-start reliability

- Install debug APK.
- Clear app data.
- Start app.
- Rotate phone.
- Change dark/light mode.
- Background app and reopen.
- Force-close and reopen.

Expected result: no crash, no blank screen, no navigation loop.

## Test session 2: scanning reliability

- Open scanner.
- Grant camera permission.
- Capture one page.
- Capture five pages.
- Capture a dark page.
- Capture a page with a strong shadow.
- Capture a page at an angle.
- Edit captured pages.
- Export PDF.

Expected result: all pages remain editable and exportable.

## Test session 3: gallery import

- Import JPG.
- Import PNG.
- Import a large image.
- Import a screenshot.
- Import multiple images if supported.
- Apply filters.
- Export PDF.

Expected result: no out-of-memory crash.

## Test session 4: OCR and translation

- OCR clean printed text.
- OCR low-quality text.
- OCR receipt text.
- Copy text.
- Share text.
- Translate text without an API key.
- Export TXT and DOCX.

Expected result: OCR failure shows a friendly message, not a crash.

## Test session 5: QR tools

- Scan a website QR.
- Scan a contact QR.
- Scan Wi-Fi QR.
- Generate all supported QR types.
- Save generated QR.
- Share generated QR.

Expected result: malformed QR content is handled safely.

## Test session 6: widgets

- Add each widget to home screen.
- Tap each widget when app data is empty.
- Create documents, then tap recent widget.
- Reboot device if possible and re-check widgets.

Expected result: widgets never crash the launcher or app.

## Test session 7: export stress test

- Create 10-page document.
- Create 25-page document if device memory allows.
- Export PDF.
- Export ZIP.
- Open exported PDF externally.
- Share exported ZIP.

Expected result: exports complete or fail gracefully with a visible error.
