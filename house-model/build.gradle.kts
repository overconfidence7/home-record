plugins {
    alias(libs.plugins.android.library)

}

android {
    namespace = "com.kurtz.homerecord.housemodel"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isIncludeAndroidResources = true }
}


dependencies {

    api(project(":core"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
