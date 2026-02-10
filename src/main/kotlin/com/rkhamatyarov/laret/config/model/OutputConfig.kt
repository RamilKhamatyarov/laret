package com.rkhamatyarov.laret.config.model

import com.fasterxml.jackson.annotation.JsonProperty

data class OutputConfig(
    @field:JsonProperty("format")
    val format: String = "plain",
    @field:JsonProperty("colorized")
    val colorized: Boolean = true,
    @field:JsonProperty("verbose")
    val verbose: Boolean = false,
    @field:JsonProperty("prettify")
    val prettify: Boolean = true,
)
