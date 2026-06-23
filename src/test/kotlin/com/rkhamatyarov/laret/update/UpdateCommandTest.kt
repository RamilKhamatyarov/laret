package com.rkhamatyarov.laret.update

import com.rkhamatyarov.laret.core.CliApp
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCommandTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var live: Path
    private val app = CliApp(name = "laret", version = "0.2.0")

    private val release = ReleaseInfo(
        tagName = "v0.3.0",
        version = "0.3.0",
        assetName = "laret-linux-x86_64",
        assetUrl = "https://dl.example/laret-linux-x86_64",
        checksumUrl = "https://dl.example/SHA256SUMS.txt",
    )

    private val newBinaryHash = java.security.MessageDigest.getInstance("SHA-256")
        .digest("new binary".toByteArray())
        .joinToString("") { "%02x".format(it) }

    @BeforeEach
    fun setUp() {
        live = tempDir.resolve("laret")
        Files.writeString(live, "current binary")
    }

    private fun fetcherFor(latest: ReleaseInfo = release, sums: String? = null): GitHubReleaseFetcher {
        val fetcher = mockk<GitHubReleaseFetcher>()
        every { fetcher.fetchLatest(any(), any()) } returns Result.success(latest)
        every { fetcher.downloadTo(any(), any()) } answers {
            val target = secondArg<Path>()
            Files.writeString(target, "new binary")
            Result.success(target)
        }
        every { fetcher.downloadText(any()) } returns Result.success(
            sums ?: "$newBinaryHash  laret-linux-x86_64",
        )
        return fetcher
    }

    private fun command(fetcher: GitHubReleaseFetcher, locator: Result<Path> = Result.success(live)): UpdateCommand =
        UpdateCommand(
            app = app,
            fetcher = fetcher,
            replacer = BinaryReplacer(),
            locateExecutable = { locator },
        )

    @Test
    fun test_check_reports_available_update() {
        val check = command(fetcherFor()).check().getOrThrow()

        assertEquals("0.2.0", check.currentVersion)
        assertEquals("0.3.0", check.latestVersion)
        assertTrue(check.updateAvailable)
    }

    @Test
    fun test_check_reports_up_to_date_when_remote_not_newer() {
        val same = release.copy(tagName = "v0.2.0", version = "0.2.0")

        val check = command(fetcherFor(latest = same)).check().getOrThrow()

        assertFalse(check.updateAvailable)
    }

    @Test
    fun test_execute_swaps_binary_when_update_available() {
        val result = command(fetcherFor()).execute()

        assertTrue(result.isSuccess)
        assertEquals("new binary", Files.readString(live))
        assertTrue(Files.exists(tempDir.resolve("laret.old")))
    }

    @Test
    fun test_execute_refuses_when_already_up_to_date() {
        val same = release.copy(tagName = "v0.2.0", version = "0.2.0")

        val result = command(fetcherFor(latest = same)).execute()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("up to date"))
        assertEquals("current binary", Files.readString(live))
    }

    @Test
    fun test_execute_with_force_installs_same_version() {
        val same = release.copy(tagName = "v0.2.0", version = "0.2.0")

        val result = command(fetcherFor(latest = same)).execute(force = true)

        assertTrue(result.isSuccess)
        assertEquals("new binary", Files.readString(live))
    }

    @Test
    fun test_execute_aborts_and_cleans_staging_on_checksum_mismatch() {
        val badSums = "deadbeef".repeat(8) + "  laret-linux-x86_64"

        val result = command(fetcherFor(sums = badSums)).execute()

        assertTrue(result.isFailure)
        assertEquals("current binary", Files.readString(live))
        assertFalse(Files.exists(tempDir.resolve("laret.new")), "staged download must be cleaned up")
    }

    @Test
    fun test_execute_refuses_gracefully_outside_native_binary() {
        val jvmGuard = Result.failure<Path>(IllegalStateException("Self-update requires the native laret binary"))

        val result = command(fetcherFor(), locator = jvmGuard).execute()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("native laret binary"))
    }

    @Test
    fun test_execute_refuses_in_protected_install_location() {
        val protected = Result.success(Paths.get("/usr/local/bin/laret"))

        val result = command(fetcherFor(), locator = protected).execute()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("protected location"))
        assertEquals("current binary", Files.readString(live))
    }

    @Test
    fun test_execute_aborts_on_major_bump_when_not_confirmed() {
        val majorApp = CliApp(name = "laret", version = "1.0.0")
        val majorRelease = release.copy(tagName = "v2.0.0", version = "2.0.0")
        val command = UpdateCommand(
            app = majorApp,
            fetcher = fetcherFor(latest = majorRelease),
            replacer = BinaryReplacer(),
            locateExecutable = { Result.success(live) },
            confirmMajor = { false },
        )

        val result = command.execute()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("major version", ignoreCase = true))
        assertEquals("current binary", Files.readString(live))
    }

    @Test
    fun test_execute_proceeds_on_major_bump_when_confirmed() {
        val majorApp = CliApp(name = "laret", version = "1.0.0")
        val majorRelease = release.copy(tagName = "v2.0.0", version = "2.0.0")
        val command = UpdateCommand(
            app = majorApp,
            fetcher = fetcherFor(latest = majorRelease),
            replacer = BinaryReplacer(),
            locateExecutable = { Result.success(live) },
            confirmMajor = { true },
        )

        val result = command.execute()

        assertTrue(result.isSuccess)
        assertEquals("new binary", Files.readString(live))
    }
}
