package com.rkhamatyarov.laret.doc.prose

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command
import java.nio.file.Files
import java.nio.file.Paths
import java.util.logging.Logger
import kotlin.streams.asSequence

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

    override fun exists(groupName: String, command: String, lang: String): Boolean =
        classLoader.getResource("docs/$lang/$groupName/$command.md") != null

    /**
     * Finds authored `.md` files under `docs/{lang}` that no longer correspond
     * to a command in [app] (orphans), used by strict mode.
     *
     * Best-effort and Native-Image-safe: classpath directory enumeration only
     * works when resources live on the file system (dev/CI from source); when
     * the resource root is a JAR or not a `file:` directory, this returns empty.
     * `index.md` files (generated navigation, not authored prose) are ignored.
     */
    fun findOrphans(app: CliApp, languages: List<String>): List<String> {
        val known = buildSet {
            app.groups.forEach { group -> group.commands.forEach { add("${group.name}/${it.name}") } }
        }
        return languages.flatMap { lang -> orphansForLang(lang, known) }
    }

    private fun orphansForLang(lang: String, known: Set<String>): List<String> {
        val url = classLoader.getResource("docs/$lang") ?: return emptyList()
        if (url.protocol != "file") return emptyList()
        val root = Paths.get(url.toURI())
        if (!Files.isDirectory(root)) return emptyList()
        return Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .filter { !it.substringAfterLast('/').equals("index.md", ignoreCase = true) }
                .map { it.removeSuffix(".md") }
                .filter { it !in known }
                .map { "docs/$lang/$it.md" }
                .toList()
        }
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
        return parseFrontmatter(block) to body
    }

    /**
     * Reads the frontmatter via Jackson's tree model rather than data-class
     * binding.  `jackson-module-kotlin` would invoke Kotlin reflection on this
     * nested private class, which fails at runtime inside the shaded fat JAR
     * (`KotlinReflectionInternalError: Unresolved class … $Frontmatter`).  The
     * tree model is pure `jackson-databind` and needs no reflection.
     */
    private fun parseFrontmatter(block: String): Frontmatter {
        val node: JsonNode = yaml.readTree(block) ?: return Frontmatter()
        if (!node.isObject) return Frontmatter()
        return Frontmatter(
            title = node.path("title").takeIf { it.isTextual }?.asText(),
            summary = node.path("summary").takeIf { it.isTextual }?.asText(),
            synopsis = node.path("synopsis").takeIf { it.isTextual }?.asText(),
            examples = stringList(node.path("examples")),
            seeAlso = stringList(node.path("see_also")),
        )
    }

    private fun stringList(node: JsonNode): List<String> =
        if (node.isArray) node.mapNotNull { if (it.isNull) null else it.asText() } else emptyList()

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
        val seeAlso: List<String> = emptyList(),
    )

    companion object {
        const val FALLBACK_LANG = "en"
        private const val FENCE = "---"
    }
}
