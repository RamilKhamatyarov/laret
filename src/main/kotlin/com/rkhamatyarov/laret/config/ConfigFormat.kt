package com.rkhamatyarov.laret.config

import java.io.File

enum class ConfigFormat {
    YAML,
    TOML,
    JSON,
    ;

    companion object {
        fun fromFile(file: File): ConfigFormat? =
            when (file.extension.lowercase()) {
                "yml", "yaml" -> YAML
                "toml" -> TOML
                "json" -> JSON
                else -> null
            }

        fun fromFileName(fileName: String): ConfigFormat? =
            when {
                fileName.endsWith(".yml") || fileName.endsWith(".yaml") -> YAML
                fileName.endsWith(".toml") -> TOML
                fileName.endsWith(".json") -> JSON
                else -> null
            }
    }
}
