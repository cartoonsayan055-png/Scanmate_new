package com.synthbyte.scanmate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * System-font typography avoids bundled font binaries while keeping a consistent Material 3 scale.
 */
val ScanMateFontFamily: FontFamily = FontFamily.Default

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp),
    displayMedium = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 29.sp, lineHeight = 35.sp),
    displaySmall = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, lineHeight = 31.sp),
    headlineMedium = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleLarge = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = ScanMateFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp)
)
