package com.rkhamatyarov.laret.doc.validation

/**
 * Resolves internal `.md` links against the set of generated output paths.
 *
 * The registry is simply the set of [com.rkhamatyarov.laret.doc.DocFile]
 * relative paths the generator produced, so a link is valid iff it points at a
 * page that will actually be written. All paths use `/` separators (the
 * generator's convention), so resolution is pure string math and stays
 * OS-independent and reflection-free.
 */
class LinkResolver(generatedPaths: Set<String>) {

    private val known: Set<String> = generatedPaths.mapNotNull { normalize(it) }.toSet()

    /** True when [path] (after normalization) is one of the generated pages. */
    fun isKnown(path: String): Boolean = normalize(path)?.let { it in known } ?: false

    /**
     * Resolve a link [target] (its file part, no anchor) that appears in the page
     * at [fromPath], returning the normalized output path — or `null` when the
     * link escapes above the output root (always broken).
     */
    fun resolve(fromPath: String, target: String): String? {
        val combined = when {
            target.startsWith("/") -> target.trimStart('/')
            else -> {
                val baseDir = fromPath.substringBeforeLast('/', "")
                if (baseDir.isEmpty()) target else "$baseDir/$target"
            }
        }
        return normalize(combined)
    }

    /** Collapse `.`/`..` segments; returns `null` if `..` escapes the root. */
    private fun normalize(path: String): String? {
        val out = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> if (out.isEmpty()) return null else out.removeLast()
                else -> out.addLast(segment)
            }
        }
        return out.joinToString("/")
    }
}
