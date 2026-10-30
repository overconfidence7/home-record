plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    compileOnly(libs.android.gradle.api)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "homerecord.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "homerecord.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "homerecord.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidScreenshot") {
            id = "homerecord.android.screenshot"
            implementationClass = "AndroidScreenshotConventionPlugin"
        }
    }
}
