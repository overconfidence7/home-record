import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.kurtz.homerecord.housemanual"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.kurtz.homerecord.housemanual"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionName = "0.1.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProp("google.webClientId", "")}\"")
    }
    buildTypes {
        // Keys per docs/billing.md; blank means the free tier with a Retry paywall.
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl()}\"")
            buildConfigField("String", "REVENUECAT_API_KEY", "\"${revenueCatKey("revenuecat.testApiKey", "REVENUECAT_TEST_API_KEY").ifEmpty { revenueCatKey("revenuecat.apiKey", "REVENUECAT_API_KEY") }}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiBaseUrl()}\"")
            buildConfigField("String", "REVENUECAT_API_KEY", "\"${revenueCatKey("revenuecat.apiKey", "REVENUECAT_API_KEY")}\"")
        }
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

fun revenueCatKey(prop: String, env: String): String =
    localProp(prop, "").ifEmpty { System.getenv(env).orEmpty() }

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
    implementation(libs.coil.compose)
    implementation(libs.purchases)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
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
