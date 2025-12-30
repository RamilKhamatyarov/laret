package com.rkhamatyarov.laret.output

object TomlOutput : OutputStrategy {
    override val name = "toml"

    override fun <T> render(data: T): String = OutputFormat.asToml(data)
}
