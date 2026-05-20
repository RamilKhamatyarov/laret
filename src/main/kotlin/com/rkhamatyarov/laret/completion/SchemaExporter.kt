package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.core.CliApp

/**
 * Converts a [CliApp] command tree into an LLM function-calling schema.
 *
 * Implementations are pure: they perform no I/O. Persisting the result is the
 * caller's responsibility (see [SchemaExportCommand]).
 */
interface SchemaExporter {
    /** Returns the schema serialised as a JSON string. */
    fun export(app: CliApp): String

    /** Returns the schema as a structured map, prior to serialisation. */
    fun exportAsModel(app: CliApp): Map<String, Any?>
}
