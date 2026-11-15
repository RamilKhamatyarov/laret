package io.github.laretframework.config

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.laretframework.config.model.AppConfig
import java.io.File

class ConfigLoader internal constructor(
    private val envProvider: (String) -> String?,
    private val fileResolver: ConfigFileResolver,
) {
    constructor() : this(System::getenv, ConfigFileResolver())
    private val jsonMapper: ObjectMapper by lazy {
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val yamlMapper: ObjectMapper by lazy {
        YAMLMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    private val tomlMapper: ObjectMapper by lazy {
        TomlMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    fun load(configPath: String? = null, profile: String? = null): AppConfig {
        val file = resolveConfigFile(configPath, profile)

        return if (file?.exists() == true) {
            loadFromFile(file)
        } else {
            applyEnvironmentOverrides(AppConfig())
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

    private fun resolveConfigFile(configPath: String?, profile: String? = null): File? =
        fileResolver.resolve(configPath, profile)

    internal fun selectedFile(configPath: String?, profile: String?): File? = resolveConfigFile(configPath, profile)

    internal fun unresolvedSelection(configPath: String?, profile: String?): UnresolvedConfigSelection? =
        fileResolver.unresolved(configPath, profile)

    private fun getMapper(format: ConfigFormat): ObjectMapper = when (format) {
        ConfigFormat.YAML -> yamlMapper
        ConfigFormat.TOML -> tomlMapper
        ConfigFormat.JSON -> jsonMapper
    }

    private fun applyEnvironmentOverrides(config: AppConfig): AppConfig = config.copy(
        output =
        config.output.copy(
            colorized = envProvider("LARET_COLORIZED")?.toBoolean() ?: config.output.colorized,
            verbose = envProvider("LARET_VERBOSE")?.toBoolean() ?: config.output.verbose,
            format = envProvider("LARET_OUTPUT_FORMAT") ?: config.output.format,
        ),
        logging =
        config.logging.copy(
            level = envProvider("LARET_LOGGING_LEVEL") ?: envProvider("LARET_LOG_LEVEL") ?: config.logging.level,
            file = envProvider("LARET_LOGGING_FILE") ?: envProvider("LARET_LOG_FILE") ?: config.logging.file,
        ),
    )
}
