package com.rkhamatyarov.laret.plugin.runtime

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.rkhamatyarov.laret.plugin.install.PluginInstaller
import com.rkhamatyarov.laret.plugin.model.InstalledPlugin
import com.rkhamatyarov.laret.plugin.model.PluginConfig
import com.rkhamatyarov.laret.plugin.model.PluginMetadata
import com.rkhamatyarov.laret.plugin.model.PluginStatus
import java.nio.file.Files
import java.nio.file.Path

class PluginCatalog(
    directories: List<Path>,
    private val config: PluginConfig = PluginConfig(),
    private val reservedNames: Set<String> = emptySet(),
    private val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
) {
    private val directories = directories.map { it.toAbsolutePath().normalize() }.distinct()
    private var snapshot: List<InstalledPlugin> = emptyList()

    fun refresh(): List<InstalledPlugin> {
        val discovered = mutableListOf<InstalledPlugin>()
        val seen = mutableSetOf<String>()
        directories.forEach { directory ->
            if (!Files.isDirectory(directory)) return@forEach
            val metadataNames = mutableSetOf<String>()
            Files.list(directory).use { files ->
                files.filter { it.fileName.toString().endsWith(".toml") }
                    .sorted()
                    .forEach { metadataFile ->
                        val name = metadataFile.fileName.toString().removeSuffix(".toml")
                        metadataNames += name
                        val entry = inspect(name, metadataFile)
                        discovered += if (name in seen) entry.copy(status = PluginStatus.SHADOWED) else entry
                        seen += name
                    }
            }
            Files.list(directory).use { files ->
                files.filter { isExecutableCandidate(it) }
                    .forEach { executable ->
                        val name = executable.fileName.toString().removeSuffix(".exe")
                        if (name !in metadataNames) {
                            discovered += InstalledPlugin(
                                name,
                                executable,
                                null,
                                PluginStatus.INVALID,
                                "metadata missing",
                            )
                        }
                    }
            }
        }
        snapshot = discovered
        return snapshot
    }

    fun list(): List<InstalledPlugin> = snapshot.ifEmpty { refresh() }

    fun activeNames(): List<String> = list()
        .filter { it.status == PluginStatus.INSTALLED && isEnabled(it.name) }
        .map { it.name }

    fun findActive(name: String): InstalledPlugin? = list().firstOrNull {
        it.name == name && it.status == PluginStatus.INSTALLED && isEnabled(name)
    }

    fun remove(name: String, force: Boolean = false): Result<Unit> = runCatching {
        val entry = list().firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("Plugin '$name' not found")
        if (entry.status != PluginStatus.INSTALLED && !force) {
            throw IllegalStateException("Plugin '$name' is ${entry.status.name.lowercase()}; use --force to remove it")
        }
        val executable = entry.executable
        val metadata = entry.metadataFile
        if (executable != null) Files.deleteIfExists(executable)
        if (metadata != null) Files.deleteIfExists(metadata)
        refresh()
    }

    private fun inspect(name: String, metadataFile: Path): InstalledPlugin {
        val executable = metadataFile.parent.resolve(PluginInstaller.platformFileName(name))
        if (name in reservedNames) return invalid(name, executable, metadataFile, "name is reserved")
        if (Files.isSymbolicLink(metadataFile) || Files.isSymbolicLink(executable)) {
            return invalid(name, executable, metadataFile, "symlinks are not allowed")
        }
        return try {
            val metadata = TomlMapper().findAndRegisterModules().readValue(
                metadataFile.toFile(),
                PluginMetadata::class.java,
            )
            PluginInstaller.validateName(name)
            require(metadata.name == name) { "metadata name does not match filename" }
            require(Files.isRegularFile(executable)) { "executable missing" }
            require(isExecutableCandidate(executable)) { "file is not executable" }
            val actual = PluginInstaller.sha256(executable)
            require(actual == metadata.sha256) { "checksum mismatch" }
            InstalledPlugin(name, executable, metadataFile, PluginStatus.INSTALLED, metadata = metadata)
        } catch (error: Exception) {
            invalid(name, executable, metadataFile, error.message ?: "invalid metadata")
        }
    }

    private fun invalid(name: String, executable: Path, metadata: Path, reason: String) =
        InstalledPlugin(name, executable, metadata, PluginStatus.INVALID, reason)

    private fun isEnabled(name: String): Boolean =
        config.autoLoad && name !in config.disabled && (config.enabled.isEmpty() || name in config.enabled)

    private fun isExecutableCandidate(path: Path): Boolean {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) return false
        if (path.fileName.toString().endsWith(".toml")) return false
        if (isWindows) {
            return path.fileName.toString().endsWith(".exe")
        }
        return true
    }

    companion object {
        fun directories(
            configured: List<String>,
            override: Path? = null,
            env: String? = System.getenv("LARET_PLUGIN_DIR"),
            home: Path = Path.of(System.getProperty("user.home")),
        ): List<Path> {
            val values = buildList {
                override?.let(::add)
                env?.split(
                    System.getProperty("path.separator"),
                )?.filter { it.isNotBlank() }?.forEach { add(Path.of(it)) }
                configured.forEach { add(Path.of(it.replaceFirst("~", home.toString()))) }
                add(home.resolve(".laret").resolve("plugins"))
            }
            return values.map { it.toAbsolutePath().normalize() }.distinct()
        }
    }
}
