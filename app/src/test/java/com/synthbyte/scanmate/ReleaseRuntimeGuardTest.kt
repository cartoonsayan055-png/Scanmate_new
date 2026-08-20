package com.synthbyte.scanmate

import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseRuntimeGuardTest {
    @Test
    fun apachePoiXwpfRuntimeIsNotPackaged() {
        val missing = runCatching {
            Class.forName("org.apache.poi.xwpf.usermodel.XWPFDocument")
        }.exceptionOrNull() is ClassNotFoundException

        assertTrue(
            "DOCX export must not depend on Apache POI/XWPF at runtime because it caused signed APK crashes.",
            missing
        )
    }
}
