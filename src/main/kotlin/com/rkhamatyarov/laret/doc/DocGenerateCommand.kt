package com.rkhamatyarov.laret.doc

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.doc.generators.GroffDocGenerator
import com.rkhamatyarov.laret.doc.generators.MarkdownDocGenerator
import com.rkhamatyarov.laret.doc.prose.ProseProvider
import com.rkhamatyarov.laret.doc.prose.ResourceProseProvider
import java.nio.file.Files
import java.nio.file.Path

/**
 * Raised by [DocGenerateCommand] in `--strict` mode when documentation fails
 * validation.  Carries every problem so CI logs show all of them at once.
 */
class DocValidationException(val problems: List<String>) :
    RuntimeException("Documentation validation failed:\n" + problems.joinToString("\n") { "  - $it" })

/**
 * Orchestrates documentation generation for `laret doc generate`.
 *
 * This is the **only** layer that performs file I/O: it selects a pure
 * [DocGenerator] for the requested [DocFormat], fans out over the requested
 * language(s), then writes the resulting [DocFile]s under the output directory.
 *
 * Usage: `laret doc generate --format <md|man> --lang <en|es|all> --output-dir <path> [--strict] [--include-hidden]`.
 */
class DocGenerateCommand(private val app: CliApp) {

    /**
     * Generate and write documentation.
     *
     * @param format        Output format (Markdown or man pages).
     * @param lang          Language tag, or [ALL] to emit every supported language.
     * @param outputDir     Directory under which [DocFile.relativePath]s are written.
     * @param provider      Prose source (overridable for tests).
     * @param strict        Fail-fast on missing prose files, broken `see_also`
     *                      links, or orphaned `.md` files (for CI/CD).
     * @param includeHidden Document hidden commands too (with an `[INTERNAL]` badge).
     * @return The list of files actually written, in generation order.
     * @throws DocValidationException in [strict] mode when validation fails.
     */
    fun run(
        format: DocFormat,
        lang: String,
        outputDir: Path,
        provider: ProseProvider = ResourceProseProvider(),
        strict: Boolean = false,
        includeHidden: Boolean = false,
    ): List<Path> {
        val languages = resolveLanguages(format, lang)

        if (strict) {
            val problems = collectProblems(languages, provider, includeHidden)
            if (problems.isNotEmpty()) throw DocValidationException(problems)
        }

        val generator: DocGenerator = when (format) {
            DocFormat.MARKDOWN -> MarkdownDocGenerator(provider)
            DocFormat.MAN -> GroffDocGenerator(provider)
        }

        val files = languages
            .flatMap { generator.generate(app, it, includeHidden) }
            .distinctBy { it.relativePath }
            .toMutableList()

        if (generator is MarkdownDocGenerator) {
            files += generator.mkdocsYaml(app, languages)
        }

        return files.map { write(outputDir, it) }
    }

    /**
     * Man pages are language-neutral (flat `man1/`), so they are emitted once.
     * Markdown fans out: [ALL] expands to every supported language.
     */
    private fun resolveLanguages(format: DocFormat, lang: String): List<String> = when {
        format == DocFormat.MAN -> listOf(
            if (lang == ALL || lang.isBlank()) ResourceProseProvider.FALLBACK_LANG else lang,
        )
        lang == ALL -> SUPPORTED_LANGUAGES
        else -> listOf(lang)
    }

    /** Collects every strict-mode problem so they can be reported together. */
    private fun collectProblems(
        languages: List<String>,
        provider: ProseProvider,
        includeHidden: Boolean,
    ): List<String> {
        val problems = mutableListOf<String>()
        val validTargets = app.groups.flatMap { group ->
            group.commands.map { "${app.name}-${group.name}-${it.name}" }
        }.toSet()

        languages.forEach { lang ->
            app.groups.forEach { group ->
                group.commands.filter { includeHidden || !it.hidden }.forEach { command ->
                    if (!provider.exists(group.name, command.name, lang)) {
                        problems += "missing prose: docs/$lang/${group.name}/${command.name}.md"
                    }
                    provider.resolve(group.name, command, lang).seeAlso
                        .filter { it.isNotBlank() && it !in validTargets }
                        .forEach { problems += "broken see_also '$it' in ${group.name}/${command.name} ($lang)" }
                }
            }
        }

        if (provider is ResourceProseProvider) {
            provider.findOrphans(app, languages).forEach { problems += "orphaned doc: $it" }
        }
        return problems
    }

    private fun write(outputDir: Path, docFile: DocFile): Path {
        val target = outputDir.resolve(docFile.relativePath)
        target.parent?.let { Files.createDirectories(it) }
        Files.writeString(target, docFile.content, Charsets.UTF_8)
        return target
    }

    companion object {
        const val ALL = "all"

        /** Languages emitted by `--lang all`, aligned with the repo's i18n bundles. */
        val SUPPORTED_LANGUAGES = listOf("en", "es")
    }
}
