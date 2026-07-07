package com.rkhamatyarov.laret.model.fs

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Abstraction over filesystem **side-effects** so that a dry run can intercept
 * writes and deletes without any command ever checking a dry-run flag.
 *
 * Reads ([exists], [readText], [listFiles]) are never side-effects and behave
 * identically in every mode.  Mutations ([writeText], [delete],
 * [createDirectories]) are routed through the injected implementation:
 * [RealFileSystem] in a normal run, [DryRunFileSystem] under `--dry-run`.
 * Because the interception lives behind this interface, command actions stay
 * free of `if (ctx.isDryRun)` branches — accidental side-effects become
 * structurally impossible rather than something a reviewer must catch.
 *
 * The `String` overloads exist purely for DSL ergonomics; they delegate to the
 * [Path] methods.  No reflection or dynamic proxies are used, so every
 * implementation is GraalVM native-image friendly.
 */
interface LaretFileSystem {
    fun writeText(path: Path, content: String)

    fun delete(path: Path): Boolean

    fun createDirectories(path: Path)

    fun exists(path: Path): Boolean

    fun isDirectory(path: Path): Boolean

    fun readText(path: Path): String

    fun listFiles(path: Path): List<Path>

    fun writeText(path: String, content: String): Unit = writeText(Paths.get(path), content)

    fun delete(path: String): Boolean = delete(Paths.get(path))

    fun createDirectories(path: String): Unit = createDirectories(Paths.get(path))

    fun exists(path: String): Boolean = exists(Paths.get(path))

    fun isDirectory(path: String): Boolean = isDirectory(Paths.get(path))

    fun readText(path: String): String = readText(Paths.get(path))

    fun listFiles(path: String): List<Path> = listFiles(Paths.get(path))
}
