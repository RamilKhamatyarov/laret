package com.rkhamatyarov.laret.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rkhamatyarov.laret.config.model.AppConfig
import com.rkhamatyarov.laret.ui.redBold
import java.io.File

class ConfigLoader {
    private val jsonMapper: ObjectMapper = jacksonObjectMapper()
    private val yamlMapper: ObjectMapper = YAMLMapper()
    private val tomlMapper: ObjectMapper = TomlMapper()

    fun load(configPath: String? = null): AppConfig {
        val file = resolveConfigFile(configPath)

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

            println("Loaded config from ${file.absolutePath} (format: $format)")
            applyEnvironmentOverrides(config)
        } catch (e: Exception) {
            println(redBold("Failed to load config from ${file.absolutePath} ${e.printStackTrace()}"))
            throw RuntimeException("Config loading failed: ${e.message}", e)
        }
    }

    fun save(
        config: AppConfig,
        outputPath: String,
        format: ConfigFormat = ConfigFormat.YAML,
    ) {
        val file = File(outputPath)
        file.parentFile?.mkdirs()

        try {
            val mapper = getMapper(format)
            val content =
                mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(config)
            file.writeText(content)
            println("Saved config to ${file.absolutePath}")
        } catch (e: Exception) {
            println(redBold("Failed to save config to ${file.absolutePath} ${e.printStackTrace()}"))
            throw RuntimeException("Config save failed: ${e.message}", e)
        }
    }

    private fun resolveConfigFile(configPath: String?): File? {
        // Priority order
        val candidates =
            listOf(
                configPath?.let { File(it) },
                File(".laret.yml"),
                File(".laret.yaml"),
                File(".laret.toml"),
                File(".laret.json"),
                File(System.getProperty("user.home"), ".laret.yml"),
                File(System.getProperty("user.home"), ".laret.toml"),
                File(System.getProperty("user.home"), ".laret.json"),
            ).filterNotNull()

        return candidates.firstOrNull { it.exists() }
    }

    private fun getMapper(format: ConfigFormat): ObjectMapper =
        when (format) {
            ConfigFormat.YAML -> yamlMapper
            ConfigFormat.TOML -> tomlMapper
            ConfigFormat.JSON -> jsonMapper
        }

    private fun applyEnvironmentOverrides(config: AppConfig): AppConfig {
        // Override with environment variables if present
        return config.copy(
            output =
                config.output.copy(
                    colorized =
                        System.getenv("LARET_COLORIZED")?.toBoolean()
                            ?: config.output.colorized,
                    verbose =
                        System.getenv("LARET_VERBOSE")?.toBoolean()
                            ?: config.output.verbose,
                    format =
                        System.getenv("LARET_OUTPUT_FORMAT")
                            ?: config.output.format,
                ),
            logging =
                config.logging.copy(
                    level =
                        System.getenv("LARET_LOG_LEVEL")
                            ?: config.logging.level,
                    file =
                        System.getenv("LARET_LOG_FILE")
                            ?: config.logging.file,
                ),
        )
    }
}
