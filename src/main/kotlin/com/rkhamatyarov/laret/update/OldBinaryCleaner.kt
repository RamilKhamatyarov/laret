package com.rkhamatyarov.laret.update

import java.nio.file.Files
import java.nio.file.Path

/**
 * Startup cleanup for `.old` binaries left behind by [BinaryReplacer].
 *
 * A previous update renames the running executable to `<name>.old`; that file
 * cannot be deleted while the old process is still alive, so deletion is
 * deferred to the **next** startup.  Files still locked by another running
 * instance are silently skipped and retried on a later start — this hook must
 * never break application startup.
 */
object OldBinaryCleaner {

    /**
     * Delete `laret*.old` files in [dir].  Locked or undeletable files are
     * skipped.  Returns the paths actually deleted.
     */
    fun cleanup(dir: Path): List<Path> {
        if (!Files.isDirectory(dir)) return emptyList()
        return runCatching {
            Files.list(dir).use { entries ->
                entries
                    .filter { it.fileName.toString().let { n -> n.startsWith("laret") && n.endsWith(".old") } }
                    .toList()
                    .filter { candidate -> runCatching { Files.deleteIfExists(candidate) }.getOrDefault(false) }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Best-effort cleanup around the current executable; absolutely never
     * throws.  No-op when not running as a native laret binary.
     */
    fun cleanupSilently() {
        runCatching {
            ExecutableLocator.locate().getOrNull()?.parent?.let { cleanup(it) }
        }
    }
}
