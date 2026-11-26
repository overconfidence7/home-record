import org.gradle.api.GradleException
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
        // With a benchmark build in the same invocation the plugin also hands the renderer every library's own
        // R jar (fragment-1.1.0-R.jar and ~200 more, beside the app's merged R.jar), so every preview fails with
        // "androidx.fragment.R and androidx.fragment.R$attr disagree on InnerClasses attribute". Fail at once with
        // the fix instead of 700 render errors [build:screenshot-with-benchmark].
        gradle.taskGraph.whenReady {
            val tasksHere = allTasks.filter { it.project == target }
            val screenshot = tasksHere.firstOrNull { it.name.matches(Regex("(validate|update)\\w*ScreenshotTest")) }
            val benchmark = tasksHere.firstOrNull { it.name.contains("Benchmark") && !it.name.contains("ScreenshotTest") }
            if (screenshot != null && benchmark != null) {
                throw GradleException(
                    "${screenshot.path} and ${benchmark.path} cannot run in one Gradle invocation: the benchmark " +
                        "build adds library R jars to the screenshot renderer and every preview fails. Run them separately.",
                )
            }
        }
        dependencies {
            add("screenshotTestImplementation", libs.lib("compose-ui-tooling"))
            add("screenshotTestImplementation", libs.lib("screenshot-validation-api"))
        }
    }
}
