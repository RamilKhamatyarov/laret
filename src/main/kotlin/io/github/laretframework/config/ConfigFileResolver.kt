package io.github.laretframework.config

import java.io.File

/** A `--config` path or `--profile` name that was requested but selected no file. */
data class UnresolvedConfigSelection(val selector: Selector, val value: String) {
    enum class Selector { CONFIG, PROFILE }
}

internal class ConfigFileResolver(
    private val workDir: File = File("."),
    private val homeDir: File = File(System.getProperty("user.home")),
    private val exists: (File) -> Boolean = { it.exists() },
) {
    private enum class Kind { EXPLICIT, PROFILE, BASE }

    fun resolve(configPath: String?, profile: String?): File? = select(configPath, profile)?.first

    /** An explicit path wins over a profile, so with both given only the path has to resolve. */
    fun unresolved(configPath: String?, profile: String?): UnresolvedConfigSelection? {
        val kind = select(configPath, profile)?.second
        val name = profile?.takeIf(String::isNotBlank)
        return when {
            configPath != null && kind != Kind.EXPLICIT ->
                UnresolvedConfigSelection(UnresolvedConfigSelection.Selector.CONFIG, configPath)

            configPath == null && name != null && kind != Kind.PROFILE ->
                UnresolvedConfigSelection(UnresolvedConfigSelection.Selector.PROFILE, name)

            else -> null
        }
    }

    private fun select(configPath: String?, profile: String?): Pair<File, Kind>? {
        val candidates = buildList {
            configPath?.let { add(File(it) to Kind.EXPLICIT) }
            profile?.takeIf(String::isNotBlank)?.let { name ->
                addAll(candidates(".laret.$name").map { it to Kind.PROFILE })
            }
            addAll(candidates(".laret").map { it to Kind.BASE })
        }
        return candidates.firstOrNull { exists(it.first) }
    }

    private fun candidates(stem: String): List<File> = listOf(workDir, homeDir).flatMap { directory ->
        EXTENSIONS.map { extension -> File(directory, "$stem.$extension") }
    }

    private companion object {
        val EXTENSIONS = listOf("yml", "yaml", "toml", "json")
    }
}
