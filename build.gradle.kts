plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Robolectric downloads its android-all jars itself (not through Gradle), from
// Maven Central by default. Point it at Google's mirror for the same reason as
// settings.gradle.kts. The jars land in ~/.m2 and are reused on later runs.
subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("robolectric.dependency.repo.id", "mavencentralmirror")
        systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
    }
}
