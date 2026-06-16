package com.rkhamatyarov.laret.update

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration

/**
 * Fetches release metadata and binary assets from the GitHub Releases API
 * using the JDK [HttpClient] and the existing Jackson dependency — no new
 * networking libraries.
 *
 * JSON is parsed with `readTree` (tree model) rather than data-class binding
 * so the GraalVM native image needs **no extra reflection configuration**.
 *
 * All operations return [Result]; network failures (timeouts, 4xx/5xx,
 * unsupported platforms) degrade gracefully instead of throwing through the
 * command layer.
 */
class GitHubReleaseFetcher(private val repo: String = DEFAULT_REPO, private val client: HttpClient = defaultClient()) {

    private val mapper = jacksonObjectMapper()

    /** Resolve the latest release and the asset matching the current platform. */
    fun fetchLatest(
        osName: String = System.getProperty("os.name"),
        osArch: String = System.getProperty("os.arch"),
    ): Result<ReleaseInfo> = runCatching {
        val assetName = assetNameFor(osName, osArch)
            ?: throw IllegalStateException("Unsupported platform: $osName/$osArch")

        val request = baseRequest("https://api.github.com/repos/$repo/releases/latest").GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IllegalStateException("GitHub API returned HTTP ${response.statusCode()} for $repo")
        }

        val root: JsonNode = mapper.readTree(response.body())
        val tagName = root.path("tag_name").asText("")
        if (tagName.isBlank()) throw IllegalStateException("Release response has no tag_name")

        val assets = root.path("assets")
        val assetUrl = downloadUrlOf(assets, assetName)
            ?: throw IllegalStateException("Release $tagName has no asset named '$assetName'")

        ReleaseInfo(
            tagName = tagName,
            version = tagName.removePrefix("v"),
            assetName = assetName,
            assetUrl = assetUrl,
            checksumUrl = downloadUrlOf(assets, CHECKSUM_FILE),
        )
    }

    /** Download [url] to [target], following GitHub's redirect to the CDN. */
    fun downloadTo(url: String, target: Path): Result<Path> = runCatching {
        val request = baseRequest(url).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofFile(target))
        if (response.statusCode() != 200) {
            throw IllegalStateException("Download failed with HTTP ${response.statusCode()}: $url")
        }
        response.body()
    }

    /** Download [url] as text (used for `SHA256SUMS.txt`). */
    fun downloadText(url: String): Result<String> = runCatching {
        val request = baseRequest(url).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IllegalStateException("Download failed with HTTP ${response.statusCode()}: $url")
        }
        response.body()
    }

    private fun baseRequest(url: String): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/vnd.github+json")
        System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        return builder
    }

    private fun downloadUrlOf(assets: JsonNode, name: String): String? = assets
        .firstOrNull { it.path("name").asText() == name }
        ?.path("browser_download_url")?.asText()
        ?.takeIf { it.isNotBlank() }

    companion object {
        const val DEFAULT_REPO = "RamilKhamatyarov/laret"
        const val CHECKSUM_FILE = "SHA256SUMS.txt"
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)

        private fun defaultClient(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        /**
         * Map JVM os.name/os.arch onto the asset names published by release.yml,
         * or `null` for platforms without a published binary.
         */
        fun assetNameFor(osName: String, osArch: String): String? {
            val os = osName.lowercase()
            val arm = osArch.lowercase() in setOf("aarch64", "arm64")
            return when {
                os.contains("win") -> "laret-windows-x86_64.exe"
                os.contains("mac") || os.contains("darwin") ->
                    if (arm) "laret-macos-aarch64" else "laret-macos-x86_64"
                os.contains("linux") && !arm -> "laret-linux-x86_64"
                else -> null
            }
        }
    }
}
