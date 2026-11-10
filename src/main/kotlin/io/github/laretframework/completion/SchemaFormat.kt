package io.github.laretframework.completion

/**
 * Supported schema formats, selectable from the CLI.
 *
 * @param id value accepted on the command line (e.g. `--format openai`).
 */
enum class SchemaFormat(val id: String) {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    LARET("laret"),
    ;

    companion object {
        /** Resolves a CLI [id] to a format, or `null` when unrecognised. */
        fun fromId(id: String): SchemaFormat? = entries.find { it.id == id.lowercase() }
    }
}
