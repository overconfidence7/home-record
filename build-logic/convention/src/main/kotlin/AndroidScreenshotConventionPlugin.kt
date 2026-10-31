import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies

/** Compose Preview Screenshot Testing (`updateDebugScreenshotTest`, `validateDebugScreenshotTest`). */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.compose.screenshot")
        withAndroid { it.experimentalProperties["android.experimental.enableScreenshotTest"] = true }
        // The renderer runs every preview of a module in one test JVM; at Gradle's default 512 MB heap Field Log's
        // ~340 shots thrash the GC and the worker never exits. 2 GB keeps both apps' runs bounded.
        tasks.withType(Test::class.java).configureEach {
            if (name.contains("ScreenshotTest")) maxHeapSize = "2g"
        }
        dependencies {
            add("screenshotTestImplementation", libs.lib("compose-ui-tooling"))
            add("screenshotTestImplementation", libs.lib("screenshot-validation-api"))
        }
    }
}
