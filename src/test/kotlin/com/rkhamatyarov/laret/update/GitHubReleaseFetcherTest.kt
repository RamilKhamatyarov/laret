package com.rkhamatyarov.laret.update

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubReleaseFetcherTest {

    private val releaseJson = """
        {
          "tag_name": "v0.3.0",
          "assets": [
            {"name": "laret-linux-x86_64", "browser_download_url": "https://dl.example/laret-linux-x86_64"},
            {"name": "laret-windows-x86_64.exe", "browser_download_url": "https://dl.example/laret-windows.exe"},
            {"name": "SHA256SUMS.txt", "browser_download_url": "https://dl.example/SHA256SUMS.txt"}
          ]
        }
    """.trimIndent()

    private fun responseReturning(status: Int, body: String): HttpResponse<String> {
        val response = mockk<HttpResponse<String>>()
        every { response.statusCode() } returns status
        every { response.body() } returns body
        return response
    }

    private fun clientReturning(status: Int, body: String): HttpClient {
        val response = responseReturning(status, body)
        val client = mockk<HttpClient>()
        every { client.send(any(), any<HttpResponse.BodyHandler<String>>()) } returns response
        return client
    }

    private fun clientReturning(vararg responses: HttpResponse<String>): HttpClient {
        val client = mockk<HttpClient>()
        every { client.send(any(), any<HttpResponse.BodyHandler<String>>()) } returnsMany responses.toList()
        return client
    }

    @Test
    fun test_fetch_latest_resolves_platform_asset_and_checksums() {
        val fetcher = GitHubReleaseFetcher(client = clientReturning(200, releaseJson))

        val release = fetcher.fetchLatest(osName = "Linux", osArch = "amd64").getOrThrow()

        assertEquals("0.3.0", release.version)
        assertEquals("https://dl.example/laret-linux-x86_64", release.assetUrl)
        assertEquals("https://dl.example/SHA256SUMS.txt", release.checksumUrl)
    }

    @Test
    fun test_fetch_latest_fails_gracefully_on_http_error() {
        val fetcher = GitHubReleaseFetcher(client = clientReturning(403, "rate limited"))

        val result = fetcher.fetchLatest(osName = "Linux", osArch = "amd64")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("403"))
    }

    @Test
    fun test_fetch_latest_falls_back_to_release_list_when_latest_endpoint_returns_404() {
        val client = clientReturning(
            responseReturning(404, """{"message":"Not Found"}"""),
            responseReturning(200, "[$releaseJson]"),
        )
        val fetcher = GitHubReleaseFetcher(client = client)

        val release = fetcher.fetchLatest(osName = "Linux", osArch = "amd64").getOrThrow()

        assertEquals("0.3.0", release.version)
        assertEquals("laret-linux-x86_64", release.assetName)
    }

    @Test
    fun test_fetch_latest_reports_missing_published_releases_when_fallback_is_empty() {
        val client = clientReturning(
            responseReturning(404, """{"message":"Not Found"}"""),
            responseReturning(200, "[]"),
        )
        val fetcher = GitHubReleaseFetcher(client = client)

        val result = fetcher.fetchLatest(osName = "Linux", osArch = "amd64")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("No published GitHub releases"))
    }

    @Test
    fun test_fetch_latest_fails_when_platform_asset_missing() {
        val jsonWithoutMac = releaseJson
        val fetcher = GitHubReleaseFetcher(client = clientReturning(200, jsonWithoutMac))

        val result = fetcher.fetchLatest(osName = "Mac OS X", osArch = "aarch64")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("laret-macos-aarch64"))
    }

    @Test
    fun test_fetch_latest_fails_for_unsupported_platform() {
        val fetcher = GitHubReleaseFetcher(client = clientReturning(200, releaseJson))

        val result = fetcher.fetchLatest(osName = "SunOS", osArch = "sparc")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Unsupported platform"))
    }

    @Test
    fun test_asset_name_resolution_covers_release_matrix() {
        assertEquals("laret-linux-x86_64", GitHubReleaseFetcher.assetNameFor("Linux", "amd64"))
        assertEquals("laret-macos-x86_64", GitHubReleaseFetcher.assetNameFor("Mac OS X", "x86_64"))
        assertEquals("laret-macos-aarch64", GitHubReleaseFetcher.assetNameFor("Mac OS X", "aarch64"))
        assertEquals("laret-windows-x86_64.exe", GitHubReleaseFetcher.assetNameFor("Windows 11", "amd64"))
        assertNull(GitHubReleaseFetcher.assetNameFor("Linux", "aarch64"))
    }

    @Test
    fun test_network_exception_becomes_failure_not_throw() {
        val client = mockk<HttpClient>()
        every {
            client.send(any(), any<HttpResponse.BodyHandler<String>>())
        } throws java.net.http.HttpTimeoutException("request timed out")
        val fetcher = GitHubReleaseFetcher(client = client)

        val result = fetcher.fetchLatest(osName = "Linux", osArch = "amd64")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("timed out"))
    }
}
