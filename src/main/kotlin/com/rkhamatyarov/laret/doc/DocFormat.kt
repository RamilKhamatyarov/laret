package com.rkhamatyarov.laret.doc

/**
 * Supported documentation output formats for `laret doc generate`.
 *
 * @param id CLI token accepted by the `--format` flag.
 */
enum class DocFormat(val id: String) {
    MARKDOWN("md"),
    MAN("man"),
    ;

    companion object {
        /** Resolve a [DocFormat] from its CLI [id], or `null` when unknown. */
        fun fromId(id: String): DocFormat? = entries.find { it.id == id.trim().lowercase() }
    }
}
