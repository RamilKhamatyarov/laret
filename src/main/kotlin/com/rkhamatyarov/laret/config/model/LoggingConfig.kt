package com.rkhamatyarov.laret.config.model

import com.fasterxml.jackson.annotation.JsonProperty

data class LoggingConfig(
    @field:JsonProperty("level")
    val level: String = "INFO",
    @field:JsonProperty("file")
    val file: String = "",
    @field:JsonProperty("format")
    val format: String = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n",
    @field:JsonProperty("max-size")
    val maxSize: String = "10MB",
    @field:JsonProperty("max-history")
    val maxHistory: Int = 10,
)
