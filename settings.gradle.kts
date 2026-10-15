// Maven Central is reached through a shared proxy in the cloud builder and is
// rate-limited there (HTTP 429, sporadic 404). Google's full mirror of Maven
// Central on googleapis.com is tried first; Maven Central stays as the fallback
// for anything the mirror has not synced yet. (pluginManagement must be the
// first statement, so the URL is repeated rather than held in a val.)
pluginManagement {
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        mavenCentral()
    }
}

rootProject.name = "HomeRecord"
include(":core", ":core-ui", ":house-manual", ":field-log")
