package com.rkhamatyarov.laret.completion

/**
 * Supported LLM schema dialects, selectable from the CLI.
 *
 * @param id value accepted on the command line (e.g. `--format openai`).
 */
enum class SchemaFormat(val id: String) {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    ;

    companion object {
        /** Resolves a CLI [id] to a rmat, or `null` when unrecognised. */
        fun fromId(id: String): SchemaFormat? = entries.find { it.id == id.lowercase() }
    }
}
