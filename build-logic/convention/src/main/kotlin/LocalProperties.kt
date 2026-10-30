import org.gradle.api.Project
import java.util.Properties

/** A value from the root `local.properties` (gitignored; never in source), or [default]. */
fun Project.localProp(key: String, default: String): String {
    val f = rootProject.file("local.properties")
    val p = Properties()
    if (f.exists()) f.inputStream().use { p.load(it) }
    return p.getProperty(key, default)
}

/** Debug builds: `api.baseUrl`, default the `wrangler dev` server as the emulator sees it. */
fun Project.apiBaseUrl(): String = localProp("api.baseUrl", "http://10.0.2.2:8787")

// Release builds talk to the production Worker over HTTPS only; `api.baseUrl` (the emulator
// host) never reaches them. Override with `api.releaseBaseUrl` or API_RELEASE_BASE_URL.
fun Project.releaseApiBaseUrl(): String {
    val url = localProp("api.releaseBaseUrl", "").ifEmpty { System.getenv("API_RELEASE_BASE_URL").orEmpty() }
        .ifEmpty { "https://api.homerecord.app" }
    require(url.startsWith("https://")) { "release API base URL must be https: $url" }
    return url
}
