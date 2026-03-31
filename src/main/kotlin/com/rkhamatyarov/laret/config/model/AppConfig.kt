package com.rkhamatyarov.laret.config.model

import com.fasterxml.jackson.annotation.JsonProperty

data class AppConfig(
    @field:JsonProperty("app")
    val app: AppMetadata = AppMetadata(),
    @field:JsonProperty("output")
    val output: OutputConfig = OutputConfig(),
    @field:JsonProperty("plugins")
    val plugins: PluginConfig = PluginConfig(),
    @field:JsonProperty("logging")
    val logging: LoggingConfig = LoggingConfig(),
    /**
     * Persistent flag overrides.
     *
     * Keys use dot-separated notation with three levels of specificity:
     *  - `"<group>.<command>.<flagLong>"` — applies to one command in one group
     *  - `"<command>.<flagLong>"` — applies to that command name in any group
     *  - `"global.<flagLong>"` — applies to any command that declares the flag
     *
     * Example `.laret.yml`:
     * ```yaml
     * flags:
     *   file:
     *     create:
     *       force: true
     *   global:
     *     format: json
     * ```
     */
    @field:JsonProperty("flags")
    val flags: Map<String, Any>? = null
)

data class AppMetadata(
    @field:JsonProperty("name")
    val name: String = "Laret",
    @field:JsonProperty("version")
    val version: String = "1.0.0",
    @field:JsonProperty("description")
    val description: String = "",
    @field:JsonProperty("author")
    val author: String = ""
)
