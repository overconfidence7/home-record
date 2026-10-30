import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** SDK levels, Java 17 and the JVM test stack every module shares (CLAUDE.md, Stack and architecture). */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.compileSdk = 37
    android.defaultConfig.minSdk = 26
    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    android.testOptions.unitTests.isIncludeAndroidResources = true
    dependencies {
        for (alias in listOf("junit", "truth", "robolectric", "androidx-test-core", "kotlinx-coroutines-test")) {
            add("testImplementation", libs.lib(alias))
        }
    }
}

/** Runs [block] on the module's Android extension once the application or library plugin is applied. */
internal fun Project.withAndroid(block: (CommonExtension) -> Unit) {
    for (id in listOf("com.android.application", "com.android.library")) {
        pluginManager.withPlugin(id) { block(extensions.getByType<CommonExtension>()) }
    }
}
