# ScanMate AI Pro safe release rules.
# Release R8 is enabled for v1.5.0. These rules keep scanner/OCR/Room/Compose integrations stable.

# ============================================================
# App core/domain/data
# ============================================================

-keep class com.synthbyte.scanmate.data.** { *; }
-keep class com.synthbyte.scanmate.domain.** { *; }
-keep class com.synthbyte.scanmate.utils.** { *; }
-keep class com.synthbyte.scanmate.util.** { *; }

# ============================================================
# ZXing / ML Kit / CameraX / Room / Moshi / Retrofit
# ============================================================

-keep class com.google.zxing.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }

-keep class androidx.camera.** { *; }
-keep class androidx.room.** { *; }

-keep class com.squareup.moshi.** { *; }
-keep class retrofit2.** { *; }

-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.**
-dontwarn okio.**

# ============================================================
# Kotlin / annotations / reflection metadata
# ============================================================

-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes InnerClasses,EnclosingMethod

# ============================================================
# Widgets
# ============================================================

-keep class com.synthbyte.scanmate.widgets.** { *; }
-keep class * extends android.appwidget.AppWidgetProvider { *; }

# ============================================================
# Coil / Compose
# ============================================================

-keep class coil.** { *; }
-dontwarn coil.**

-keep class androidx.compose.runtime.** { *; }

-keepclassmembers class * {
    @androidx.compose.runtime.Stable *;
    @androidx.compose.runtime.Immutable *;
}

# ============================================================
# Debug logging
# ============================================================

-assumenosideeffects class okhttp3.logging.HttpLoggingInterceptor {
    public void log(java.lang.String);
}

-dontwarn okhttp3.logging.**

# ============================================================
# SpongyCastle / BouncyCastle / iText PDF security
# ============================================================

-dontwarn org.spongycastle.**
-keep class org.spongycastle.crypto.** { *; }
-keep class org.spongycastle.jce.** { *; }
-keep class org.spongycastle.asn1.** { *; }
-keep class org.spongycastle.** { *; }

-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Security/runtime keep rules for password-protected PDF export and encrypted preferences.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ============================================================
# PDFBox Android
# ============================================================

# Keep PDFBox Android classes used for PDF processing/export.
-keep class com.tom_roush.pdfbox.** { *; }

# PDFBox Android optional JPEG2000 encoder/decoder.
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter

# ============================================================
# DOCX export
# ============================================================

# DOCX export is now generated with a lightweight ZipOutputStream package writer.
# Apache POI is intentionally not used at runtime to avoid signed-release R8/runtime crashes.

# ============================================================
# Java desktop/server APIs referenced by optional document-library paths
# ============================================================

# Java AWT desktop classes referenced by optional rendering/debug paths.
-dontwarn java.awt.**
-dontwarn java.awt.color.**
-dontwarn java.awt.font.**
-dontwarn java.awt.geom.**
-dontwarn java.awt.image.**

# W3C DOM optional desktop/XML APIs referenced by optional signing/SVG paths.
-dontwarn org.w3c.dom.events.**
-dontwarn org.w3c.dom.svg.**
-dontwarn org.w3c.dom.traversal.**

# Batik optional SVG rendering paths.
-dontwarn org.apache.batik.**


# ============================================================
# Log4j optional integrations
# ============================================================

# OSGi optional service loading used by Log4j.
-dontwarn org.osgi.**

# Logging optional integrations.
-dontwarn org.apache.logging.log4j.**

# ============================================================
# WorkManager / workers / security audit
# ============================================================
-keep class com.synthbyte.scanmate.workers.** { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
