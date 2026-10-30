plugins {
    id("homerecord.android.application")
    id("homerecord.android.compose")
    id("homerecord.android.screenshot")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kurtz.homerecord.housemanual"
    defaultConfig {
        applicationId = "com.kurtz.homerecord.housemanual"
    }
    buildTypes {
        // Keys per docs/billing.md; blank means the free tier with a Retry paywall.
        debug {
            buildConfigField("String", "REVENUECAT_API_KEY", "\"${revenueCatKey("revenuecat.testApiKey", "REVENUECAT_TEST_API_KEY").ifEmpty { revenueCatKey("revenuecat.apiKey", "REVENUECAT_API_KEY") }}\"")
        }
        for (name in listOf("release", "benchmark")) {
            getByName(name) {
                buildConfigField("String", "REVENUECAT_API_KEY", "\"${revenueCatKey("revenuecat.apiKey", "REVENUECAT_API_KEY")}\"")
            }
        }
    }
}

fun revenueCatKey(prop: String, env: String): String =
    localProp(prop, "").ifEmpty { System.getenv(env).orEmpty() }

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
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
