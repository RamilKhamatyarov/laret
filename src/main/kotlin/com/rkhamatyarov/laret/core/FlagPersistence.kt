package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.config.model.AppConfig
import com.rkhamatyarov.laret.model.Option

/**
 * Resolves persistent flag values from [AppConfig.flags].
 *
 * Lookup precedence (first match wins):
 *  1. `"<groupName>.<commandName>.<flagLong>"`
 *  2. `"<commandName>.<flagLong>"`
 *  3. `"global.<flagLong>"`
 *
 * This utility is called during command execution for every option declared
 * with `persistent = true` that was **not** explicitly supplied on the
 * command line.  The result, when non-null, is used as the effective value
 * instead of the option's compile-time default.
 */
object FlagPersistence {
    /**
     * Look up [option] in [config] using the provided [groupName] and
     * [commandName] as key components.
     *
     * @param option      The option being resolved.
     * @param groupName   Name of the group the command belongs to, or null.
     * @param commandName Name of the command being executed.
     * @param config      Loaded application config, or null when no config file was found.
     * @return The resolved string value, or null if not found in config.
     */
    fun resolveFlag(option: Option, groupName: String?, commandName: String, config: AppConfig?): String? {
        val flagsMap = config?.flags ?: return null
        val keys = buildKeys(groupName, commandName, option.long)
        for (key in keys) {
            val value = getValueFromMap(flagsMap, key)
            if (value != null) {
                return convertToString(value, option.takesValue)
            }
        }
        return null
    }

    /**
     * Build the list of lookup keys in priority order.
     *
     * For `groupName = "file"`, `commandName = "create"`, `flagLong = "force"`:
     *  - `"file.create.force"`
     *  - `"create.force"`
     *  - `"global.force"`
     */
    internal fun buildKeys(groupName: String?, commandName: String, flagLong: String): List<String> = buildList {
        if (!groupName.isNullOrBlank()) add("$groupName.$commandName.$flagLong")
        add("$commandName.$flagLong")
        add("global.$flagLong")
    }

    /**
     * Traverse [map] using a dot-separated [key].
     *
     * For `key = "file.create.force"` this navigates:
     * `map["file"] → map["create"] → map["force"]`
     *
     * At any level the value may be a nested [Map] (for intermediate segments)
     * or a leaf value (for the final segment).
     */
    @Suppress("UNCHECKED_CAST")
    internal fun getValueFromMap(map: Map<String, Any>, key: String): Any? {
        val parts = key.split(".")
        var current: Any = map
        for (part in parts) {
            current = (current as? Map<String, Any>)?.get(part) ?: return null
        }
        return current
    }

    /**
     * Convert the raw config value to a string compatible with
     * [com.rkhamatyarov.laret.core.CommandContext.options].
     *
     * Boolean flags that do not take a value (`takesValue = false`) are
     * represented as `"true"` or `"false"` strings so that
     * [com.rkhamatyarov.laret.core.CommandContext.optionBool] works correctly.
     */
    internal fun convertToString(value: Any, takesValue: Boolean): String = when {
        !takesValue && value is Boolean -> value.toString()
        !takesValue && value.toString().lowercase() in setOf("true", "false") ->
            value.toString().lowercase()
        else -> value.toString()
    }
}
