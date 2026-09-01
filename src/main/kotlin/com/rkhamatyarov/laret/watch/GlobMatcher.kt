package com.rkhamatyarov.laret.watch

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher

/**
 * Matches changed files against glob include/exclude patterns for live watch.
 *
 * Patterns use Java NIO glob syntax (`*`, `**`, `?`, `{a,b}`, `[a-z]`). A
 * pattern prefixed with `!` is an exclude. A path matches when it satisfies at
 * least one include (or there are no includes) and no exclude. See the
 * `live-watch-mode` ADR.
 */
class GlobMatcher(patterns: List<String>) {
    private val includes = patterns.filterNot { it.startsWith("!") }.map { compile(it) }
    private val excludes = patterns.filter { it.startsWith("!") }.map { compile(it.removePrefix("!")) }

    /**
     * Whether a file at [relativePath] (relative to the watch root, `/`- or
     * platform-separated) should trigger a re-run.
     */
    fun matches(relativePath: String): Boolean {
        val path = Path.of(relativePath)
        if (excludes.any { it.matches(path) }) return false
        if (includes.isEmpty()) return true
        return includes.any { it.matches(path) }
    }

    /** Convenience overload matching a [Path] relative to the watch root. */
    fun matches(relativePath: Path): Boolean = matches(relativePath.toString())

    private fun compile(glob: String): Matcher {
        val fs = FileSystems.getDefault()
        val globs = buildList {
            add(glob)
            if (glob.startsWith("**/")) add(glob.removePrefix("**/"))
        }
        val full = globs.map { fs.getPathMatcher("glob:$it") }
        val byName = fs.getPathMatcher("glob:$glob")
        return Matcher(full, byName)
    }

    private class Matcher(private val full: List<PathMatcher>, private val byName: PathMatcher) {
        fun matches(path: Path): Boolean {
            if (full.any { it.matches(path) }) return true
            val name = path.fileName ?: return false
            return byName.matches(name)
        }
    }
}
