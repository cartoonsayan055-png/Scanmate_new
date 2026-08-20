package com.synthbyte.scanmate.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CAMERA_SCAN = "camera_scan"
    const val DOC_DETAIL = "document_detail/{docId}"
    const val PAGE_EDITOR = "page_editor/{docId}/{pageId}"
    const val PDF_TOOLS = "pdf_tools"
    const val SIGNATURE = "signature/{docId}"
    const val QR_TOOLS = "qr_tools"
    const val QR_SCANNER = "qr_scanner"
    const val SETTINGS = "settings"
    const val TOOLS = "tools"
    const val ZIP_TOOLS = "zip_tools"
    const val AI_ASSISTANT = "ai_assistant"
    const val OCR_TRANSLATE = "ocr_translate"
    const val FILE_MANAGER = "file_manager"
    const val VAULT = "vault"

    fun docDetail(docId: Long) = "document_detail/$docId"
    fun pageEditor(docId: Long, pageId: Long) = "page_editor/$docId/$pageId"
    fun signature(docId: Long) = "signature/$docId"
}
