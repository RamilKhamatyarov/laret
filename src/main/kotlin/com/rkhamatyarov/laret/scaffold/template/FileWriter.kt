package com.rkhamatyarov.laret.scaffold.template

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class FileWriter {
    fun write(target: Path, content: String, executable: Boolean = false): Result<Path> = runCatching {
        target.parent?.let { Files.createDirectories(it) }
        Files.writeString(
            target,
            content,
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        if (executable) markExecutable(target)
        target
    }.onFailure { e ->
        return Result.failure(IllegalStateException("Failed to write $target: ${e.message}", e))
    }

    private fun markExecutable(target: Path) {
        runCatching {
            val perms = Files.getPosixFilePermissions(target).toMutableSet()
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
            perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE)
            perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE)
            Files.setPosixFilePermissions(target, perms)
        }
    }
}
