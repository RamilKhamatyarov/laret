package com.rkhamatyarov.laret.update

import java.nio.file.Path

/**
 * Decides whether the running binary lives in a location that user-space
 * self-update must not touch (system bin dirs on Unix, `Program Files` on
 * Windows).  Updating there needs elevated privileges or a package manager, so
 * [UpdateCommand] fails gracefully with guidance instead of attempting a swap
 * that would fail mid-way.
 *
 * Detection is path-prefix based (pure and testable); the command layer may
 * additionally consult filesystem writability at runtime.
 */
object ProtectedLocation {

    private val PROTECTED_PREFIXES = listOf(
        "/usr/bin", "/usr/sbin", "/usr/local/bin", "/usr/local/sbin",
        "/bin", "/sbin", "/opt",
        "c:\\program files", "c:\\program files (x86)", "c:\\windows",
    )

    /** True when [path]'s directory matches a known privileged install location. */
    fun isProtected(path: Path): Boolean {
        val dir = (path.parent ?: path).toString().replace('\\', '/').lowercase().trimEnd('/')
        return PROTECTED_PREFIXES.any { prefix ->
            val normalized = prefix.replace('\\', '/')
            dir == normalized || dir.startsWith("$normalized/")
        }
    }

    /** Human-readable guidance shown when a self-update is refused for [path]. */
    fun guidance(path: Path): String = "laret is installed in a protected location ($path). " +
        "Update it with elevated privileges, e.g. `sudo mv <new-binary> $path`, " +
        "or via the package manager you installed it with."
}
