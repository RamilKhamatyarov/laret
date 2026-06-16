package com.rkhamatyarov.laret.update

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChecksumVerifierTest {

    @TempDir
    lateinit var tempDir: Path

    private val helloHash = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

    private fun fileWith(content: String): Path {
        val file = tempDir.resolve("laret-linux-x86_64")
        Files.writeString(file, content)
        return file
    }

    @Test
    fun test_sha256_computes_known_digest() {
        val file = fileWith("hello")

        assertEquals(helloHash, ChecksumVerifier.sha256(file))
    }

    @Test
    fun test_verify_passes_for_matching_checksum() {
        val file = fileWith("hello")
        val sums = "$helloHash  laret-linux-x86_64\nffff  other-file"

        assertTrue(ChecksumVerifier.verify(file, sums, "laret-linux-x86_64").isSuccess)
    }

    @Test
    fun test_verify_fails_on_mismatch() {
        val file = fileWith("tampered content")
        val sums = "$helloHash  laret-linux-x86_64"

        val result = ChecksumVerifier.verify(file, sums, "laret-linux-x86_64")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("mismatch"))
    }

    @Test
    fun test_verify_fails_when_asset_missing_from_sums() {
        val file = fileWith("hello")
        val sums = "$helloHash  some-other-asset"

        assertTrue(ChecksumVerifier.verify(file, sums, "laret-linux-x86_64").isFailure)
    }

    @Test
    fun test_expected_hash_rejects_malformed_lines() {
        assertNull(ChecksumVerifier.expectedHash("not-a-hash  laret-linux-x86_64", "laret-linux-x86_64"))
        assertNull(ChecksumVerifier.expectedHash("", "laret-linux-x86_64"))
    }
}
