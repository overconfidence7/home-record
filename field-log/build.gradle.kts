plugins {
    id("homerecord.android.application")
    id("homerecord.android.compose")
    id("homerecord.android.screenshot")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kurtz.homerecord.fieldlog"
    defaultConfig {
        applicationId = "com.kurtz.homerecord.fieldlog"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":core-ui"))
    implementation(project(":house-model"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.reorderable)
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
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
