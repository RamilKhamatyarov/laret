package io.github.laretframework.output

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compile-checks the custom `OutputStrategy` example published in README.md.
 *
 * The `CsvOutput` implementation below is byte-for-byte the one documented in
 * the "Creating Custom Output Formats" section. If the `OutputStrategy`
 * signature changes, this test stops compiling and the README is caught before
 * it ships a snippet that does not build.
 */
class CustomOutputStrategyTest {

    private object CsvOutput : OutputStrategy {
        override val name = "csv"

        override fun <T> render(data: T): String = when (data) {
            is List<*> -> data.joinToString(System.lineSeparator()) { renderRow(it) }
            else -> renderRow(data)
        }

        private fun renderRow(row: Any?): String = when (row) {
            is Map<*, *> -> row.values.joinToString(",")
            else -> row.toString()
        }
    }

    @Test
    fun `custom strategy renders a list of maps as csv rows`() {
        val entries: List<Map<String, Any>> = listOf(
            mapOf("name" to "a.txt", "size" to 12L),
            mapOf("name" to "b.txt", "size" to 34L),
        )

        val rendered = CsvOutput.render(entries)

        assertEquals("a.txt,12${System.lineSeparator()}b.txt,34", rendered)
    }

    @Test
    fun `custom strategy renders a single value`() {
        assertEquals("plain", CsvOutput.render("plain"))
    }

    @Test
    fun `custom strategy exposes its name`() {
        assertEquals("csv", CsvOutput.name)
    }

    @Test
    fun `the documented when-dispatch compiles and selects a strategy`() {
        // Mirrors the README's dispatch snippet.
        fun formatterFor(format: String): OutputStrategy = when (format) {
            "csv" -> CsvOutput
            "json" -> JsonOutput
            else -> PlainOutput
        }

        assertEquals("csv", formatterFor("csv").name)
        assertEquals("json", formatterFor("json").name)
        assertTrue(formatterFor("anything else") is PlainOutput)
    }
}
