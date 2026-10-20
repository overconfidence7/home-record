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
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl()}\"")
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

dependencies {
    implementation(project(":core"))
    implementation(project(":core-ui"))
    implementation(project(":house-model"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    screenshotTestImplementation(libs.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
}
