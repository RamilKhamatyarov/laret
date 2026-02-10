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
)

data class AppMetadata(
    @field:JsonProperty("name")
    val name: String = "Laret",
    @field:JsonProperty("version")
    val version: String = "1.0.0",
    @field:JsonProperty("description")
    val description: String = "",
    @field:JsonProperty("author")
    val author: String = "",
)
