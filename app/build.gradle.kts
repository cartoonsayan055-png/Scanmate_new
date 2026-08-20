import java.util.Properties
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    id("jacoco")
    alias(libs.plugins.ktlint)
}

val compileSdkOverride = providers.gradleProperty("SCANMATE_COMPILE_SDK").orElse("35").get().toInt()
val targetSdkOverride = providers.gradleProperty("SCANMATE_TARGET_SDK").orElse("35").get().toInt()
val versionCodeOverride =
    (
        System.getenv("VERSION_CODE")
            ?: providers.gradleProperty("VERSION_CODE").orElse("6").get()
    ).toInt()
val versionNameOverride =
    System.getenv("VERSION_NAME")
        ?: providers.gradleProperty("VERSION_NAME").orElse("1.6.0").get()

val signingProperties =
    Properties().apply {
        val signingFile = rootProject.file("keystore.properties")
        if (signingFile.exists()) {
            signingFile.inputStream().use { input ->
                load(input)
            }
        }
    }

fun String?.isUsableSigningValue(): Boolean =
    !isNullOrBlank() && !trim().startsWith("YOUR_", ignoreCase = true)

val releaseKeystorePath =
    System.getenv("KEYSTORE_PATH")
        ?: signingProperties.getProperty("storeFile")
        ?: (project.findProperty("storeFile") as String?)
        ?: "keystore/scanmate-release.jks"

val releaseStorePassword =
    System.getenv("KEY_STORE_PASSWORD")
        ?: System.getenv("SCANMATE_STORE_PASSWORD")
        ?: System.getenv("STORE_PASSWORD")
        ?: signingProperties.getProperty("storePassword")
        ?: (project.findProperty("storePassword") as String?)

val releaseKeyAlias =
    System.getenv("KEY_ALIAS")
        ?: signingProperties.getProperty("keyAlias")
        ?: (project.findProperty("keyAlias") as String?)
        ?: "scanmate-key"

val releaseKeyPassword =
    System.getenv("KEY_PASSWORD")
        ?: signingProperties.getProperty("keyPassword")
        ?: (project.findProperty("keyPassword") as String?)

val releaseKeystoreFile = file(releaseKeystorePath)

val requireReleaseSigning =
    System.getenv("SCANMATE_REQUIRE_RELEASE_SIGNING")
        ?.equals("true", ignoreCase = true) == true

val hasReleaseSigning =
    releaseKeystoreFile.exists() &&
        releaseStorePassword.isUsableSigningValue() &&
        releaseKeyAlias.isUsableSigningValue() &&
        releaseKeyPassword.isUsableSigningValue()

if (requireReleaseSigning && !hasReleaseSigning) {
    throw GradleException(
        "Release signing is required but not fully configured.\n" +
            "Check KEYSTORE_PATH, KEY_STORE_PASSWORD/STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, " +
            "and the decoded keystore file at: ${releaseKeystoreFile.absolutePath}",
    )
}

android {
    namespace = "com.synthbyte.scanmate"
    compileSdk = compileSdkOverride

    defaultConfig {
        applicationId = "com.synthbyte.scanmate"
        minSdk = 26
        targetSdk = targetSdkOverride
        versionCode = versionCodeOverride
        versionName = versionNameOverride
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "GEMINI_CERT_PINS", "\"\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword.orEmpty()
                keyAlias = releaseKeyAlias.orEmpty()
                keyPassword = releaseKeyPassword.orEmpty()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs +=
            listOf(
                "-Xjvm-default=all",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=kotlinx.coroutines.FlowPreview",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE-notice.md",
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE",
                    "META-INF/LICENSE.txt",
                    "META-INF/NOTICE",
                    "META-INF/NOTICE.txt",
                    "META-INF/INDEX.LIST",
                )
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        disable += setOf("GradleDependency")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.useJUnitPlatform()
                test.testLogging {
                    events("passed", "skipped", "failed")
                }
            }
        }
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    filter {
        exclude { entry ->
            entry.file.path.contains("/build/")
        }
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter =
        listOf(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
        )

    val debugTree =
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile) {
            exclude(fileFilter)
        }

    classDirectories.setFrom(debugTree)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get().asFile) {
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        },
    )
}

dependencies {
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.zxing.core)
    implementation(libs.hilt.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.work.runtime.ktx)

    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.itextpdf:itextg:5.5.10")
    implementation("com.madgag.spongycastle:core:1.58.0.0")
    implementation("com.madgag.spongycastle:prov:1.58.0.0")
    implementation("com.madgag.spongycastle:bcpkix-jdk15on:1.58.0.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.0")

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.logging.interceptor)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.leakcanary.android)

    ksp(libs.androidx.room.compiler)
    ksp(libs.moshi.kotlin.codegen)

    kapt(libs.hilt.compiler)
    kaptTest(libs.hilt.compiler)
    kaptAndroidTest(libs.hilt.compiler)
}
