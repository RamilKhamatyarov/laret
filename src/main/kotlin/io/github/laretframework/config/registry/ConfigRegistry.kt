package io.github.laretframework.config.registry

import io.github.laretframework.config.ConfigFileResolver
import io.github.laretframework.config.ConfigLoader
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.util.logging.Level
import java.util.logging.Logger

interface ConfigLayer {
    val priority: Int get() = 0
    fun get(key: String): Any?
    fun describe(key: String): ConfigCandidate = ConfigCandidate("layer", priority, get(key))
}

data class ConfigCandidate(val source: String, val priority: Int, val value: Any?)

data class ConfigExplanation(val key: String, val winner: ConfigCandidate?, val candidates: List<ConfigCandidate>)

fun interface ConfigFileReader {
    fun load(file: File): Map<String, Any?>
}

class ConfigRegistry(
    private val layers: MutableList<ConfigLayer> = mutableListOf(),
    private val logger: Logger = Logger.getLogger(ConfigRegistry::class.java.name),
) {
    fun defaults(values: Map<String, Any?>): ConfigRegistry = apply {
        layers.add(DefaultLayer(flatten(values)))
    }

    fun files(
        configPath: String? = null,
        profile: String? = null,
        reader: ConfigFileReader = ConfigFileReader { ConfigLoader().loadMapFromFile(it) },
    ): ConfigRegistry = apply {
        layers.add(FileLayer(configPath = configPath, profile = profile, reader = reader))
    }

    fun env(
        prefix: String = "LARET",
        bindings: Map<String, String> = emptyMap(),
        envProvider: () -> Map<String, String> = { System.getenv() },
    ): ConfigRegistry = apply {
        layers.add(EnvLayer(prefix = prefix, bindings = bindings, envProvider = envProvider))
    }

    fun flags(values: Map<String, String>, bindings: Map<String, String> = emptyMap()): ConfigRegistry = apply {
        layers.add(FlagLayer(values = values, bindings = bindings))
    }

    fun get(key: String): Any? {
        val normalized = normalizeKey(key)
        return layers.sortedByDescending { it.priority }.firstNotNullOfOrNull { it.get(normalized) }
    }

    fun explain(key: String): ConfigExplanation {
        val normalized = normalizeKey(key)
        val candidates = layers.sortedByDescending { it.priority }.map { it.describe(normalized) }
        return ConfigExplanation(normalized, candidates.firstOrNull { it.value != null }, candidates)
    }

    fun getString(key: String, default: String? = null): String? = get(key)?.toString() ?: default

    fun getInt(key: String, default: Int? = null): Int? {
        val value = get(key) ?: return default
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: logConversionFailure(key, value, "Int", default)
            else -> logConversionFailure(key, value, "Int", default)
        }
    }

    fun getLong(key: String, default: Long? = null): Long? {
        val value = get(key) ?: return default
        return when (value) {
            is Long -> value
            is BigInteger -> value.toLong()
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: logConversionFailure(key, value, "Long", default)
            else -> logConversionFailure(key, value, "Long", default)
        }
    }

    fun getDouble(key: String, default: Double? = null): Double? {
        val value = get(key) ?: return default
        return when (value) {
            is Double -> value
            is BigDecimal -> value.toDouble()
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: logConversionFailure(key, value, "Double", default)
            else -> logConversionFailure(key, value, "Double", default)
        }
    }

    fun getBool(key: String, default: Boolean? = null): Boolean? {
        val value = get(key) ?: return default
        return when (value) {
            is Boolean -> value

            is String -> when (value.lowercase()) {
                "true", "1", "yes", "y", "on" -> true
                "false", "0", "no", "n", "off" -> false
                else -> logConversionFailure(key, value, "Boolean", default)
            }

            else -> logConversionFailure(key, value, "Boolean", default)
        }
    }

    fun getSlice(key: String, default: List<String>? = null): List<String>? {
        val value = get(key) ?: return default
        return when (value) {
            is Iterable<*> -> value.mapNotNull { it?.toString() }
            is Array<*> -> value.mapNotNull { it?.toString() }
            is String -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> logConversionFailure(key, value, "List<String>", default)
        }
    }

    private fun <T> logConversionFailure(key: String, value: Any, targetType: String, fallback: T?): T? {
        logger.log(
            Level.FINE,
            "Cannot convert config key '$key' with value '$value' to $targetType; using fallback",
        )
        return fallback
    }

    companion object {
        fun empty(): ConfigRegistry = ConfigRegistry()

        fun defaultBindingKey(groupName: String, optionLong: String): String =
            normalizeKey("$groupName.${optionLong.replace('-', '.')}")

        internal fun normalizeKey(key: String): String = key.trim()
            .replace('-', '.')
            .replace('_', '.')
            .lowercase()
            .split(".")
            .filter { it.isNotBlank() }
            .joinToString(".")

        internal fun envName(prefix: String, key: String): String {
            val normalizedPrefix = prefix.trim().uppercase()
            val normalizedKey = normalizeKey(key).replace('.', '_').uppercase()
            return if (normalizedPrefix.isBlank()) normalizedKey else "${normalizedPrefix}_$normalizedKey"
        }

        internal fun flatten(values: Map<String, Any?>): Map<String, Any?> {
            val result = linkedMapOf<String, Any?>()

            fun visit(prefix: String, value: Any?) {
                when (value) {
                    is Map<*, *> -> {
                        value.forEach { (childKey, childValue) ->
                            if (childKey != null) {
                                val nextKey = listOf(prefix, childKey.toString())
                                    .filter { it.isNotBlank() }
                                    .joinToString(".")
                                visit(nextKey, childValue)
                            }
                        }
                    }

                    else -> result[normalizeKey(prefix)] = value
                }
            }

            values.forEach { (key, value) -> visit(key, value) }
            return result
        }
    }
}

class DefaultLayer(values: Map<String, Any?>) : ConfigLayer {
    override val priority = 0
    private val values = values.mapKeys { ConfigRegistry.normalizeKey(it.key) }

    override fun get(key: String): Any? = values[ConfigRegistry.normalizeKey(key)]
    override fun describe(key: String): ConfigCandidate = ConfigCandidate("default", priority, get(key))
}

class EnvLayer(
    private val prefix: String = "LARET",
    bindings: Map<String, String> = emptyMap(),
    private val envProvider: () -> Map<String, String> = { System.getenv() },
) : ConfigLayer {
    override val priority = 20
    private val normalizedBindings = bindings.mapValues { ConfigRegistry.normalizeKey(it.value) }

    override fun get(key: String): Any? = describe(key).value

    override fun describe(key: String): ConfigCandidate {
        val normalizedKey = ConfigRegistry.normalizeKey(key)
        val env = envProvider()
        val directName = ConfigRegistry.envName(prefix, normalizedKey)
        if (env[directName] != null) return ConfigCandidate(directName, priority, env[directName])

        val boundName = normalizedBindings
            .filterValues { it == normalizedKey }
            .keys
            .firstNotNullOfOrNull { optionName ->
                val name = ConfigRegistry.envName(prefix, optionName)
                if (env[name] != null) name else null
            }
        return ConfigCandidate(boundName ?: directName, priority, boundName?.let(env::get))
    }
}

class FlagLayer(values: Map<String, String>, bindings: Map<String, String> = emptyMap()) : ConfigLayer {
    override val priority = 30

    private val normalizedValues = values.entries.associate { (key, value) ->
        ConfigRegistry.normalizeKey(key) to (key to value)
    }
    private val normalizedBindings = bindings.mapValues { ConfigRegistry.normalizeKey(it.value) }

    override fun get(key: String): Any? = describe(key).value

    override fun describe(key: String): ConfigCandidate {
        val normalizedKey = ConfigRegistry.normalizeKey(key)
        val direct = normalizedValues[normalizedKey]
        if (direct != null) return ConfigCandidate(direct.first, priority, direct.second)

        val boundName = normalizedBindings
            .filterValues { it == normalizedKey }
            .keys
            .firstNotNullOfOrNull { optionName ->
                if (normalizedValues[ConfigRegistry.normalizeKey(optionName)] != null) optionName else null
            }
        val bound = boundName?.let { normalizedValues[ConfigRegistry.normalizeKey(it)] }
        return ConfigCandidate(bound?.first ?: normalizedKey, priority, bound?.second)
    }
}

class FileLayer(
    configPath: String? = null,
    profile: String? = null,
    private val reader: ConfigFileReader = ConfigFileReader { ConfigLoader().loadMapFromFile(it) },
    private val exists: (File) -> Boolean = { it.exists() },
    private val workDir: File = File("."),
    private val homeDir: File = File(System.getProperty("user.home")),
) : ConfigLayer {
    override val priority = 10

    private val file by lazy { ConfigFileResolver(workDir, homeDir, exists).resolve(configPath, profile) }
    private val values by lazy {
        file?.let { ConfigRegistry.flatten(reader.load(it)) }.orEmpty()
    }

    override fun get(key: String): Any? = values[ConfigRegistry.normalizeKey(key)]

    override fun describe(key: String): ConfigCandidate =
        ConfigCandidate(file?.absolutePath ?: "file", priority, get(key))

    internal fun selectedFile(): File? = file
}
