import org.gradle.api.Plugin
import org.gradle.api.Project

/** Compose compiler plugin and `buildFeatures.compose`; apply after the application or library convention. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        withAndroid { it.buildFeatures.compose = true }
    }
}
