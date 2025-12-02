package com.rkhamatyarov.laret.output

object PlainOutput : OutputStrategy {
    override val name = "plain"

    override fun <T> render(data: T): String = data.toString()
}
