package com.rkhamatyarov.laret.output

object YamlOutput : OutputStrategy {
    override val name = "yaml"

    override fun <T> render(data: T): String = OutputFormat.asYaml(data)
}
