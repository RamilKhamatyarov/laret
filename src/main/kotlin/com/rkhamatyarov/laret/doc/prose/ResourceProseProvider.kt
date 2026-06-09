package com.rkhamatyarov.laret.doc.prose

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rkhamatyarov.laret.model.Command
import java.util.logging.Logger

/**
 * [ProseProvider] backed by classpath resources at
 * `docs/{lang}/{group}/{command}.md`.
 *
 * Each file may begin with a YAML frontmatter block delimited by `---` fences,
 * followed by a Markdown body used as the long description:
 *
 * ```
 * ---
 * title: Create a file
 * summary: Create a new file, optionally overwriting an existing one.
 * synopsis: laret file create <path> [--content <text>] [--force]
 * examples:
 *   - laret file create notes.txt
 *   - laret file create notes.txt --force
 * see_also:
 *   - laret-file-delete
 * ---
 * Long description prose goes here…
 * ```
 *
 * **Fallback (whole-file):** the requested [lang] file is used as-is when it
 * exists and parses; otherwise English is tried; otherwise the DSL
 * [Command.description] is used.  Missing frontmatter fields are simply empty —
 * they are never merged across languages.  Malformed frontmatter is logged and
 * treated as if the file were absent, so the fallback chain continues.
 *
 * Reuses the existing `jackson-dataformat-yaml` dependency; no new deps.
 */
class ResourceProseProvider(private val classLoader: ClassLoader = ResourceProseProvider::class.java.classLoader) :
    ProseProvider {

    private val log = Logger.getLogger(ResourceProseProvider::class.java.name)

    private val yaml = YAMLMapper().registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun resolve(groupName: String, command: Command, lang: String): Prose {
        tryLoad(lang, groupName, command)?.let { return it }
        if (lang != FALLBACK_LANG) {
            tryLoad(FALLBACK_LANG, groupName, command)?.let { return it }
        }
        return dslProse(command)
    }

    private fun tryLoad(lang: String, groupName: String, command: Command): Prose? {
        val path = "docs/$lang/$groupName/${command.name}.md"
        val text = readResource(path) ?: return null
        return try {
            val (frontmatter, body) = parse(text)
            toProse(frontmatter, body, command)
        } catch (e: Exception) {
            log.warning("Malformed frontmatter in $path: ${e.message}")
            null
        }
    }

    private fun readResource(path: String): String? =
        classLoader.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }

    private fun parse(content: String): Pair<Frontmatter, String> {
        val normalized = content.replace("\r\n", "\n")
        if (!normalized.startsWith("$FENCE\n")) {
            return Frontmatter() to normalized.trim()
        }
        val closing = normalized.indexOf("\n$FENCE", startIndex = FENCE.length)
        if (closing < 0) {
            return Frontmatter() to normalized.trim()
        }
        val block = normalized.substring(FENCE.length + 1, closing)
        val afterFence = normalized.indexOf('\n', closing + 1)
        val body = if (afterFence < 0) "" else normalized.substring(afterFence + 1).trim()
        val frontmatter = yaml.readValue(block, Frontmatter::class.java)
        return frontmatter to body
    }

    private fun toProse(frontmatter: Frontmatter, body: String, command: Command): Prose = Prose(
        title = frontmatter.title ?: command.name,
        summary = frontmatter.summary ?: command.description,
        synopsis = frontmatter.synopsis,
        examples = frontmatter.examples,
        seeAlso = frontmatter.seeAlso,
        body = body,
    )

    private fun dslProse(command: Command): Prose = Prose(
        title = command.name,
        summary = command.description,
        synopsis = null,
        examples = emptyList(),
        seeAlso = emptyList(),
        body = "",
    )

    /** Mirrors the supported frontmatter schema; unknown keys are ignored. */
    private data class Frontmatter(
        val title: String? = null,
        val summary: String? = null,
        val synopsis: String? = null,
        val examples: List<String> = emptyList(),
        @param:JsonProperty("see_also") val seeAlso: List<String> = emptyList(),
    )

    companion object {
        const val FALLBACK_LANG = "en"
        private const val FENCE = "---"
    }
}
