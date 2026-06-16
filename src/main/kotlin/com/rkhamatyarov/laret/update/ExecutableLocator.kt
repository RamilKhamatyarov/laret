package com.rkhamatyarov.laret.update

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolves the path of the currently running **native** laret binary.
 *
 * When the process is a JVM (`gradle run`, tests, fat JAR) the process
 * executable is `java`/`java.exe` — replacing that would be catastrophic, so
 * [locate] fails with a clear message instead.  A binary renamed by the user
 * is still accepted as long as its file name starts with `laret`.
 */
object ExecutableLocator {

    fun locate(): Result<Path> = runCatching {
        val command = ProcessHandle.current().info().command().orElse(null)
            ?: throw IllegalStateException("Cannot determine the current process executable")
        val path = Paths.get(command)
        val name = path.fileName.toString().lowercase().removeSuffix(".exe")
        if (name != "laret" && !name.startsWith("laret-")) {
            throw IllegalStateException(
                "Self-update requires the native laret binary; current process is '$name'. " +
                    "Run the released binary instead of the JVM.",
            )
        }
        if (!Files.isRegularFile(path)) {
            throw IllegalStateException("Executable not found on disk: $path")
        }
        path
    }
}
