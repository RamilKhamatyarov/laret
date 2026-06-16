package com.rkhamatyarov.laret.update

import com.rkhamatyarov.laret.core.CliApp
import java.nio.file.Files
import java.nio.file.Path

/**
 * Orchestrates `laret update`: fetch → compare → download → verify → swap.
 *
 * Collaborators are injected for testability; production wiring uses the
 * defaults.  All failures surface as [Result] so the command layer can print
 * a friendly message and let the `onError` hook set the exit code.
 */
class UpdateCommand(
    private val app: CliApp,
    private val fetcher: GitHubReleaseFetcher = GitHubReleaseFetcher(),
    private val replacer: BinaryReplacer = BinaryReplacer(),
    private val locateExecutable: () -> Result<Path> = ExecutableLocator::locate,
) {

    /** Dry-run: report current vs latest without touching anything. */
    fun check(): Result<UpdateCheck> = fetcher.fetchLatest().mapCatching { release ->
        if (!VersionComparator.isValid(app.version)) {
            throw IllegalStateException("Cannot parse current version '${app.version}'")
        }
        UpdateCheck(
            currentVersion = app.version,
            latestVersion = release.version,
            updateAvailable = VersionComparator.isNewer(release.version, app.version),
        )
    }

    /**
     * Perform the self-update.  With [force] the version comparison is
     * skipped and the latest release is installed unconditionally.
     */
    fun execute(force: Boolean = false): Result<Path> = runCatching {
        val live = locateExecutable().getOrThrow()
        val release = fetcher.fetchLatest().getOrThrow()

        if (!force) {
            if (!VersionComparator.isValid(app.version)) {
                throw IllegalStateException(
                    "Cannot parse current version '${app.version}'; use --force to update anyway",
                )
            }
            if (!VersionComparator.isNewer(release.version, app.version)) {
                throw IllegalStateException(
                    "Already up to date (current ${app.version}, latest ${release.version})",
                )
            }
        }

        val staged = live.resolveSibling("${live.fileName}.new")
        // A stale staged file from a crashed update must not poison this run.
        runCatching { Files.deleteIfExists(staged) }

        try {
            fetcher.downloadTo(release.assetUrl, staged).getOrThrow()
            verifyChecksum(staged, release).getOrThrow()
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(staged) }
            throw e
        }

        replacer.replace(staged, live).getOrThrow()
    }

    private fun verifyChecksum(staged: Path, release: ReleaseInfo): Result<Unit> {
        val checksumUrl = release.checksumUrl
            ?: return Result.failure(
                IllegalStateException("Release ${release.tagName} has no ${GitHubReleaseFetcher.CHECKSUM_FILE}"),
            )
        return fetcher.downloadText(checksumUrl).mapCatching { sums ->
            ChecksumVerifier.verify(staged, sums, release.assetName).getOrThrow()
        }
    }
}
