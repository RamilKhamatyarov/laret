package io.github.laretframework.output

object PlainOutput : OutputStrategy {
    override val name = "plain"

    override fun <T> render(data: T): String = data.toString()
}
