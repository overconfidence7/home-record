import org.gradle.api.Project

/**
 * The one version both apps ship with (docs/decisions.md 2026-11-07, versioning scheme).
 *
 * `version.properties` at the repo root holds `versionName` (semantic, MAJOR.MINOR.PATCH). The versionCode is
 * derived from it so it always rises with the name: MAJOR * 1,000,000 + MINOR * 10,000 + PATCH * 100 + BUILD,
 * where BUILD (0..99) is a rebuild of the same version sent to Play again (`HOMERECORD_BUILD` or the
 * `homerecord.build` local property, default 0). 0.1.0 is 10000; 1.2.3 build 4 is 1020304.
 */
data class AppVersion(val name: String, val code: Int)

fun appVersion(versionName: String, build: Int): AppVersion {
    val parts = versionName.split(".")
    require(parts.size == 3 && parts.all { p -> p.isNotEmpty() && p.all { it.isDigit() } }) {
        "versionName must be MAJOR.MINOR.PATCH: $versionName"
    }
    val (major, minor, patch) = parts.map { it.toInt() }
    require(minor < 100 && patch < 100) { "MINOR and PATCH must be under 100: $versionName" }
    require(build in 0..99) { "build must be 0..99: $build" }
    return AppVersion(versionName, major * 1_000_000 + minor * 10_000 + patch * 100 + build)
}

fun Project.appVersion(): AppVersion {
    val props = java.util.Properties()
    rootProject.file("version.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    val name = props.getProperty("versionName", "0.1.0")
    val build = System.getenv("HOMERECORD_BUILD").orEmpty().ifEmpty { localProp("homerecord.build", "0") }.toInt()
    return appVersion(name, build)
}

/** A signing value from `local.properties` (`homerecord.<key>`) or, for CI, the `HOMERECORD_<KEY>` environment variable. */
fun Project.signingProp(key: String): String =
    localProp("homerecord.$key", "").ifEmpty { System.getenv("HOMERECORD_" + key.uppercase()).orEmpty() }
