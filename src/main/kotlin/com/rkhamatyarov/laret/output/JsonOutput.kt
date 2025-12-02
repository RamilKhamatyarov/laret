package com.rkhamatyarov.laret.output

object JsonOutput : OutputStrategy {
    override val name = "json"

    override fun <T> render(data: T): String = OutputFormat.asJson(data)
}
