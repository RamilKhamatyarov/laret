package com.rkhamatyarov.laret.config.model

import com.fasterxml.jackson.annotation.JsonProperty

data class PluginConfig(
    @field:JsonProperty("enabled")
    val enabled: List<String> = emptyList(),
    @field:JsonProperty("disabled")
    val disabled: List<String> = emptyList(),
    @field:JsonProperty("paths")
    val paths: List<String> = emptyList(),
    @field:JsonProperty("auto-load")
    val autoLoad: Boolean = true,
)
