package com.rkhamatyarov.laret.update

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

/**
 * Cross-platform rename-and-replace of the running executable.
 *
 * Windows locks a running `.exe` against deletion and overwriting, but
 * **renaming it is allowed** — that loophole makes self-update possible:
 *
 * 1. The verified download is staged as `<live>.new` *in the same directory*
 *    (same volume, so the final move can be atomic).
 * 2. The live binary is renamed to `<live>.old` (legal even while running).
 * 3. `<live>.new` is moved onto the live name (`ATOMIC_MOVE` where supported).
 * 4. If step 3 fails, `<live>.old` is rolled back to the live name, so a
 *    crash mid-update never leaves the install without a working binary.
 *
 * The lingering `.old` file is removed by [OldBinaryCleaner] on next startup.
 */
class BinaryReplacer(private val installMove: (staged: Path, live: Path) -> Unit = ::atomicMove) {

    /**
     * Swap [staged] (the verified new binary, expected next to [live]) onto
     * [live].  On success returns the live path; on failure the previous
     * binary has been restored.
     */
    fun replace(staged: Path, live: Path): Result<Path> = runCatching {
        require(Files.isRegularFile(staged)) { "Staged binary not found: $staged" }
        val old = live.resolveSibling("${live.fileName}.old")

        // A stale .old from a previous update would block the rename on Windows.
        runCatching { Files.deleteIfExists(old) }

        Files.move(live, old)
        try {
            installMove(staged, live)
        } catch (e: Exception) {
            rollback(old, live)
            throw IllegalStateException("Failed to install new binary, previous version restored: ${e.message}", e)
        }
        markExecutable(live)
        live
    }

    private fun rollback(old: Path, live: Path) {
        runCatching { Files.move(old, live, StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun markExecutable(target: Path) {
        runCatching {
            val perms = Files.getPosixFilePermissions(target).toMutableSet()
            perms.add(PosixFilePermission.OWNER_EXECUTE)
            perms.add(PosixFilePermission.GROUP_EXECUTE)
            perms.add(PosixFilePermission.OTHERS_EXECUTE)
            Files.setPosixFilePermissions(target, perms)
        }
        // POSIX permissions not supported on Windows — silently ignore.
    }

    companion object {
        private fun atomicMove(staged: Path, live: Path) {
            try {
                Files.move(staged, live, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staged, live, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
