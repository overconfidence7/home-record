import java.util.Properties

// RevenueCat public SDK key lives in the gitignored local.properties (never committed).
val localProps: Properties = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(props::load)
}
// Release key: local.properties, then env, then blank (blank means UnavailableEntitlements).
val revenueCatKey: String = localProps.getProperty("revenuecat.apiKey")
    ?: System.getenv("REVENUECAT_API_KEY")
    ?: ""
// Debug builds prefer the Test Store key, then fall back to the release chain.
val revenueCatDebugKey: String = localProps.getProperty("revenuecat.testApiKey")
    ?: System.getenv("REVENUECAT_TEST_API_KEY")
    ?: revenueCatKey

// versionCode derives from versionName (major*10000 + minor*100 + patch); bump homerecord.versionName in gradle.properties.
val appVersionName: String = providers.gradleProperty("homerecord.versionName").get()
val appVersionCode: Int = appVersionName.split(".").map { it.toInt() }.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

// Release signing is optional: set homerecord.storeFile / storePassword / keyAlias / keyPassword in the
// gitignored local.properties (or HOMERECORD_* env vars). Without them assembleRelease produces an unsigned APK.
fun signingValue(key: String): String? =
    localProps.getProperty("homerecord.$key") ?: System.getenv("HOMERECORD_${key.uppercase()}")


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kurtz.homerecord.housemanual"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kurtz.homerecord.housemanual"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        val storePath = signingValue("storeFile")
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatDebugKey\"")
        }
        release {
            buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatKey\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":core-ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.revenuecat.purchases)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
