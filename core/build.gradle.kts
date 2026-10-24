plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)

}

android {
    namespace = "com.kurtz.homerecord.core"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isIncludeAndroidResources = true }
}


dependencies {

    api(libs.kotlinx.coroutines.core)
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.security.crypto)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.play.services.location)
    implementation(libs.googleid)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
