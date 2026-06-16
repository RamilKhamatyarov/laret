package com.rkhamatyarov.laret.update

/**
 * Metadata about the latest published GitHub release, resolved for the
 * current platform.
 *
 * @param tagName     Raw tag (e.g. `"v0.2.0"`).
 * @param version     Tag with the leading `v` stripped (e.g. `"0.2.0"`).
 * @param assetName   Platform-specific binary asset name
 *                    (e.g. `"laret-linux-x86_64"`, `"laret-windows-x86_64.exe"`).
 * @param assetUrl    Direct download URL of the platform binary.
 * @param checksumUrl Download URL of `SHA256SUMS.txt`, or `null` when the
 *                    release does not ship checksums.
 */
data class ReleaseInfo(
    val tagName: String,
    val version: String,
    val assetName: String,
    val assetUrl: String,
    val checksumUrl: String?,
)

/**
 * Result of `laret update check`.
 */
data class UpdateCheck(val currentVersion: String, val latestVersion: String, val updateAvailable: Boolean)
