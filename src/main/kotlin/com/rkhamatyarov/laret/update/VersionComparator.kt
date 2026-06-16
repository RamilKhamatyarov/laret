package com.rkhamatyarov.laret.update

/**
 * Compares laret version strings of the form `MAJOR.MINOR.PATCH[-SNAPSHOT]`
 * (an optional leading `v` is tolerated).
 *
 * Ordering: numeric components first; when they are equal, a `-SNAPSHOT`
 * build sorts **below** its release (`0.2.0-SNAPSHOT < 0.2.0 < 0.2.1`).
 */
object VersionComparator {

    private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(-SNAPSHOT)?$""")

    private data class Parsed(val major: Int, val minor: Int, val patch: Int, val snapshot: Boolean)

    private fun parse(version: String): Parsed? {
        val match = PATTERN.matchEntire(version.trim()) ?: return null
        val (major, minor, patch, snapshot) = match.destructured
        return Parsed(major.toInt(), minor.toInt(), patch.toInt(), snapshot.isNotEmpty())
    }

    /** True when [version] is a well-formed laret version string. */
    fun isValid(version: String): Boolean = parse(version) != null

    /**
     * Returns `true` when [remote] is strictly newer than [local].
     *
     * Unparseable input never triggers an update: callers should treat it as
     * "cannot compare" and surface that, not silently download.
     */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote) ?: return false
        val l = parse(local) ?: return false
        val byNumbers = compareValuesBy(r, l, Parsed::major, Parsed::minor, Parsed::patch)
        if (byNumbers != 0) return byNumbers > 0
        // Same numbers: release is newer than its own snapshot.
        return l.snapshot && !r.snapshot
    }
}
