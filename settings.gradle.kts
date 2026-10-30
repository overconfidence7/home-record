pluginManagement {
    // Convention plugins (homerecord.android.*): SDK, Java, test, release, R8 and signing config set once.
    includeBuild("build-logic")
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
    }
}

rootProject.name = "home-record"

include(":core", ":core-ui", ":house-model", ":house-manual", ":field-log")
