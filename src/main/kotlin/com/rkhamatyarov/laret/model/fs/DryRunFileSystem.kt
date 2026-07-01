package com.rkhamatyarov.laret.model.fs

import java.nio.file.Path

/**
 * The `--dry-run` [LaretFileSystem]: reads delegate to [real] (reading is never
 * a side-effect), while every mutation touches **no disk** and instead narrates
 * what *would* have happened to `System.err`.
 *
 * This is the single source of truth for side-effect narration — command
 * actions deliberately make no "created"/"deleted" claims of their own, so
 * nothing can contradict the `[DRY-RUN]` lines emitted here.
 */
class DryRunFileSystem(private val real: LaretFileSystem = RealFileSystem()) : LaretFileSystem {
    override fun writeText(path: Path, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8).size
        System.err.println("[DRY-RUN] Would write $bytes bytes to $path")
    }

    override fun delete(path: Path): Boolean {
        System.err.println("[DRY-RUN] Would delete $path")
        return true
    }

    override fun createDirectories(path: Path) {
        System.err.println("[DRY-RUN] Would create directories $path")
    }

    override fun exists(path: Path): Boolean = real.exists(path)

    override fun isDirectory(path: Path): Boolean = real.isDirectory(path)

    override fun readText(path: Path): String = real.readText(path)

    override fun listFiles(path: Path): List<Path> = real.listFiles(path)
}
