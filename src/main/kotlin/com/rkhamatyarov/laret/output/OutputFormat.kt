package com.rkhamatyarov.laret.output

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object OutputFormat {
    val json: ObjectMapper = jacksonObjectMapper()
    val yaml: ObjectMapper = YAMLMapper()

    fun <T> asJson(data: T): String = json.writeValueAsString(data)

    fun <T> asYaml(data: T): String = yaml.writeValueAsString(data)
}
