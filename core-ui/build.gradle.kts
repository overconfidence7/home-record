plugins {
    id("homerecord.android.library")
    id("homerecord.android.compose")
    id("homerecord.android.screenshot")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kurtz.homerecord.coreui"
}

dependencies {
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.material3)
    api(libs.material.icons.extended)
    api(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    api(project(":core"))
    api(libs.activity.compose)
    api(libs.material3.adaptive.navigation.suite)
    api(libs.navigation3.runtime)
    api(libs.navigation3.ui)
    api(libs.lifecycle.viewmodel.navigation3)
    api(libs.lifecycle.viewmodel.compose)
    api(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.document.scanner)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
