plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)

}

android {
    namespace = "com.kurtz.homerecord.housemodel"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}


dependencies {

    api(project(":core"))
    api(project(":core-ui"))
    implementation(platform(libs.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
