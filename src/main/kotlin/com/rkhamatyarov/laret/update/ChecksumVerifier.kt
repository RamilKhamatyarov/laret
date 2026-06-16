package com.rkhamatyarov.laret.update

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Verifies a downloaded asset against the `SHA256SUMS.txt` published by the
 * release pipeline (lines of the form `<64-hex>  <asset-name>`).
 */
object ChecksumVerifier {

    /** Hex-encoded SHA-256 of [file]. */
    fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Extract the expected hash for [assetName] from [sumsText], or `null`. */
    fun expectedHash(sumsText: String, assetName: String): String? = sumsText
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.endsWith(" $assetName") || it.endsWith("\t$assetName") || it.endsWith("*$assetName") }
        ?.split(Regex("""\s+"""))
        ?.firstOrNull()
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }

    /**
     * Verify [file] against [sumsText].  Fails when the asset is missing from
     * the checksum list or the hashes differ.
     */
    fun verify(file: Path, sumsText: String, assetName: String): Result<Unit> = runCatching {
        val expected = expectedHash(sumsText, assetName)
            ?: throw IllegalStateException("No SHA256 entry for '$assetName' in $sumsText")
        val actual = sha256(file)
        if (actual != expected) {
            throw IllegalStateException(
                "Checksum mismatch for $assetName: expected $expected, got $actual",
            )
        }
    }
}
