package com.rkhamatyarov.laret.config

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rkhamatyarov.laret.config.model.AppConfig
import java.io.File

class ConfigLoader {
    private val jsonMapper: ObjectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val yamlMapper: ObjectMapper = YAMLMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val tomlMapper: ObjectMapper = TomlMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun load(configPath: String? = null, profile: String? = null): AppConfig {
        val file = resolveConfigFile(configPath, profile)

        return if (file?.exists() == true) {
            loadFromFile(file)
        } else {
            AppConfig()
        }
    }

    fun loadFromFile(file: File): AppConfig {
        require(file.exists()) { "Config file not found: ${file.absolutePath}" }

        val format =
            ConfigFormat.fromFile(file)
                ?: throw IllegalArgumentException("Unsupported config format for file: ${file.name}")

        return try {
            val content = file.readText()
            val mapper = getMapper(format)
            val config = mapper.readValue(content, AppConfig::class.java)
            applyEnvironmentOverrides(config)
        } catch (e: Exception) {
            throw RuntimeException("Config loading failed: ${e.message}", e)
        }
    }

    fun loadMapFromFile(file: File): Map<String, Any?> {
        require(file.exists()) { "Config file not found: ${file.absolutePath}" }

        val format =
            ConfigFormat.fromFile(file)
                ?: throw IllegalArgumentException("Unsupported config format for file: ${file.name}")

        return try {
            val content = file.readText()
            val mapper = getMapper(format)
            mapper.readValue(content, object : TypeReference<Map<String, Any?>>() {})
        } catch (e: Exception) {
            throw RuntimeException("Config loading failed: ${e.message}", e)
        }
    }

    fun save(config: AppConfig, outputPath: String, format: ConfigFormat = ConfigFormat.YAML) {
        val file = File(outputPath)
        file.parentFile?.mkdirs()

        try {
            val mapper = getMapper(format)
            val content = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config)
            file.writeText(content)
        } catch (e: Exception) {
            throw RuntimeException("Config save failed: ${e.message}", e)
        }
    }

    private fun resolveConfigFile(configPath: String?, profile: String? = null): File? {
        val profileCandidates =
            profile
                ?.takeIf { it.isNotBlank() }
                ?.let { activeProfile ->
                    listOf(
                        File(".laret.$activeProfile.yml"),
                        File(".laret.$activeProfile.yaml"),
                        File(".laret.$activeProfile.toml"),
                        File(".laret.$activeProfile.json"),
                        File(System.getProperty("user.home"), ".laret.$activeProfile.yml"),
                        File(System.getProperty("user.home"), ".laret.$activeProfile.toml"),
                        File(System.getProperty("user.home"), ".laret.$activeProfile.json"),
                    )
                }
                .orEmpty()

        val candidates =
            listOfNotNull(
                configPath?.let { File(it) },
            ) + profileCandidates +
                listOf(
                    File(".laret.yml"),
                    File(".laret.yaml"),
                    File(".laret.toml"),
                    File(".laret.json"),
                    File(System.getProperty("user.home"), ".laret.yml"),
                    File(System.getProperty("user.home"), ".laret.toml"),
                    File(System.getProperty("user.home"), ".laret.json"),
                )

        return candidates.firstOrNull { it.exists() }
    }

    private fun getMapper(format: ConfigFormat): ObjectMapper = when (format) {
        ConfigFormat.YAML -> yamlMapper
        ConfigFormat.TOML -> tomlMapper
        ConfigFormat.JSON -> jsonMapper
    }

    private fun applyEnvironmentOverrides(config: AppConfig): AppConfig = config.copy(
        output =
        config.output.copy(
            colorized = System.getenv("LARET_COLORIZED")?.toBoolean() ?: config.output.colorized,
            verbose = System.getenv("LARET_VERBOSE")?.toBoolean() ?: config.output.verbose,
            format = System.getenv("LARET_OUTPUT_FORMAT") ?: config.output.format,
        ),
        logging =
        config.logging.copy(
            level = System.getenv("LARET_LOG_LEVEL") ?: config.logging.level,
            file = System.getenv("LARET_LOG_FILE") ?: config.logging.file,
        ),
    )
}
