import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Compose Preview Screenshot Testing (`updateDebugScreenshotTest`, `validateDebugScreenshotTest`). */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.compose.screenshot")
        withAndroid { it.experimentalProperties["android.experimental.enableScreenshotTest"] = true }
        dependencies {
            add("screenshotTestImplementation", libs.lib("compose-ui-tooling"))
            add("screenshotTestImplementation", libs.lib("screenshot-validation-api"))
        }
    }
}
