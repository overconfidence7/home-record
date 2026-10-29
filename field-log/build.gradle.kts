import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.kurtz.homerecord.fieldlog"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.kurtz.homerecord.fieldlog"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionName = "0.1.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProp("google.webClientId", "")}\"")
    }
    buildTypes {
        debug { buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl()}\"") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiBaseUrl()}\"")
        }
        // QA only (AC-U11 scroll jank, qa/scripts/device-suite.sh): the release code with R8 and the baseline
        // profile, signed with the debug key, plus the debug seed receiver and the dev backend so the suite can
        // fill a 200-row list. Never shipped; `release` stays free of debug code.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl()}\"")
        }
    }
    sourceSets.getByName("benchmark") {
        kotlin.srcDir("src/debug/kotlin")
        assets.srcDir("src/debug/assets")
        res.srcDir("src/debug/res")
        manifest.srcFile("src/debug/AndroidManifest.xml")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}


fun localProp(key: String, default: String): String {
    val f = rootProject.file("local.properties")
    val p = Properties()
    if (f.exists()) f.inputStream().use { p.load(it) }
    return p.getProperty(key, default)
}

fun apiBaseUrl(): String {
    val f = rootProject.file("local.properties")
    val p = Properties()
    if (f.exists()) f.inputStream().use { p.load(it) }
    return p.getProperty("api.baseUrl", "http://10.0.2.2:8787")
}

// Release builds talk to the production Worker over HTTPS only; `api.baseUrl` (the emulator
// host) never reaches them. Override with `api.releaseBaseUrl` or API_RELEASE_BASE_URL.
fun releaseApiBaseUrl(): String {
    val url = localProp("api.releaseBaseUrl", "").ifEmpty { System.getenv("API_RELEASE_BASE_URL").orEmpty() }
        .ifEmpty { "https://api.homerecord.app" }
    require(url.startsWith("https://")) { "release API base URL must be https: $url" }
    return url
}

dependencies {
    implementation(project(":core"))
    implementation(project(":core-ui"))
    implementation(project(":house-model"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    screenshotTestImplementation(libs.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
}
