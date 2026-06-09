package com.rkhamatyarov.laret.doc

import com.rkhamatyarov.laret.core.CliApp

/**
 * Contract for documentation generators.
 *
 * Implementations are **pure**: [generate] returns the rendered [DocFile]s and
 * never writes to disk.  This keeps the formatting logic trivially testable and
 * makes adding new formats (HTML, AsciiDoc, …) an open/closed extension — just
 * add another [DocGenerator] without touching existing code or the orchestrator.
 *
 * Long-form prose and i18n metadata are supplied through a
 * [com.rkhamatyarov.laret.doc.prose.ProseProvider] injected into the concrete
 * generator, so the generator itself stays free of file/resource access.
 */
interface DocGenerator {
    /** The format this generator emits. */
    val format: DocFormat

    /**
     * Render documentation for every command in [app] for the given [lang].
     *
     * @param app  The CLI application whose groups/commands are documented.
     * @param lang Language tag (e.g. `"en"`, `"es"`) used to resolve prose.
     * @return One or more [DocFile]s; the caller performs the actual I/O.
     */
    fun generate(app: CliApp, lang: String): List<DocFile>
}
