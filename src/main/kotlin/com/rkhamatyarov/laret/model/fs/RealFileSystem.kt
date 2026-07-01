package com.rkhamatyarov.laret.model.fs

import java.nio.file.Files
import java.nio.file.Path

/**
 * The production [LaretFileSystem]: every operation delegates straight to
 * [Files].  Mutations actually touch the disk and stay quiet —
 * narration of what happened is the command's concern, not the filesystem's.
 */
class RealFileSystem : LaretFileSystem {
    override fun writeText(path: Path, content: String) {
        Files.writeString(path, content)
    }

    override fun delete(path: Path): Boolean = Files.deleteIfExists(path)

    override fun createDirectories(path: Path) {
        Files.createDirectories(path)
    }

    override fun exists(path: Path): Boolean = Files.exists(path)

    override fun isDirectory(path: Path): Boolean = Files.isDirectory(path)

    override fun readText(path: Path): String = Files.readString(path)

    override fun listFiles(path: Path): List<Path> = if (Files.isDirectory(path)) {
        Files.list(path).use { stream -> stream.sorted().toList() }
    } else {
        emptyList()
    }
}
