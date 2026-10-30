import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Both apps: SDK levels, version, instrumentation runner, the API base URL per build type, release with R8
 * and signing, and the QA-only `benchmark` build type. Modules add their applicationId and own fields.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        val android = extensions.getByType<ApplicationExtension>()
        configureAndroid(android)
        android.defaultConfig.apply {
            targetSdk = 37
            versionCode = 1
            versionName = "0.1.0"
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProp("google.webClientId", "")}\"")
        }
        android.buildFeatures.buildConfig = true
        // Release key from local.properties (homerecord.storeFile, storePassword, keyAlias, keyPassword); the
        // testing-apks workflow writes its testing key there, a store build puts the upload key there. Without
        // them the release APK comes out unsigned.
        val storeFile = localProp("homerecord.storeFile", "")
        if (storeFile.isNotEmpty()) {
            android.signingConfigs.create("release") {
                this.storeFile = file(storeFile)
                storePassword = localProp("homerecord.storePassword", "")
                keyAlias = localProp("homerecord.keyAlias", "")
                keyPassword = localProp("homerecord.keyPassword", "")
            }
        }
        android.buildTypes.getByName("debug") {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl()}\"")
        }
        val release = android.buildTypes.getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(android.getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = android.signingConfigs.findByName("release")
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiBaseUrl()}\"")
        }
        // QA only (AC-U11 scroll jank, qa/scripts/device-suite.sh): the release code with R8 and the baseline
        // profile, signed with the debug key, plus the debug seed receiver and the dev backend so the suite can
        // fill a 200-row list. Never shipped; `release` stays free of debug code. Fields a module adds to
        // `release` after this runs must be added to `benchmark` too.
        android.buildTypes.create("benchmark") {
            initWith(release)
            signingConfig = android.signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl()}\"")
        }
        android.sourceSets.getByName("benchmark") {
            kotlin.srcDir("src/debug/kotlin")
            assets.srcDir("src/debug/assets")
            res.srcDir("src/debug/res")
            manifest.srcFile("src/debug/AndroidManifest.xml")
        }
        dependencies {
            add("androidTestImplementation", platform(libs.lib("compose-bom")))
            for (alias in listOf(
                "androidx-test-core", "androidx-test-runner", "androidx-test-ext-junit",
                "androidx-test-espresso-core", "compose-ui-test-junit4", "truth",
            )) {
                add("androidTestImplementation", libs.lib(alias))
            }
        }
    }
}
