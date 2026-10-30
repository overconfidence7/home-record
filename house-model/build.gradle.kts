plugins {
    id("homerecord.android.library")
    id("homerecord.android.compose")
}

android {
    namespace = "com.kurtz.homerecord.housemodel"
}

dependencies {
    api(project(":core"))
    api(project(":core-ui"))
    implementation(platform(libs.compose.bom))
    implementation(libs.coil.compose)
    implementation(libs.mlkit.text.recognition)
}
