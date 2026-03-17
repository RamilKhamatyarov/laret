package com.rkhamatyarov.laret.output

/*
* Interface for pluggable output formatting strategies
*/
interface OutputStrategy {
    fun <T> render(data: T): String

    val name: String

    companion object {
        private val strategies: Map<String, OutputStrategy> by lazy {
            listOf(
                PlainOutput,
                JsonOutput,
                YamlOutput,
                TomlOutput,
                TableOutput,
            ).associateBy { it.name }
        }

        fun byName(name: String): OutputStrategy = strategies[name] ?: PlainOutput
    }
}
