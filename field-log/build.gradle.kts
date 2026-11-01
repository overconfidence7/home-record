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
        // Firebase Cloud Messaging only (AC-B30), set up from these values instead of the google-services plugin.
        // Blank (no Firebase project yet, see BLOCKERS.md) means no push: the app never asks FCM for a token.
        for ((field, key) in listOf(
            "FIREBASE_APP_ID" to "appId", "FIREBASE_API_KEY" to "apiKey",
            "FIREBASE_PROJECT_ID" to "projectId", "FIREBASE_SENDER_ID" to "senderId",
        )) {
            buildConfigField("String", field, "\"${firebaseValue(key, field)}\"")
        }
    }
}

/** `firebase.<key>` in local.properties, else the environment variable named like the field (FIREBASE_APP_ID...). */
fun firebaseValue(key: String, env: String): String =
    localProp("firebase.$key", "").ifEmpty { System.getenv(env).orEmpty() }

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
    implementation(libs.firebase.messaging)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
