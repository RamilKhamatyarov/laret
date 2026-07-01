package com.rkhamatyarov.laret.completion.completers

import com.rkhamatyarov.laret.completion.CompletionContext
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("StaticCompleter")
class StaticCompleterTest {

    @Test
    fun test_empty_prefix_returns_all_candidates() {
        val result = StaticCompleter("md", "man").complete(CompletionContext(""))

        assertEquals(listOf("md", "man"), result.candidates.map { it.value })
    }

    @Test
    fun test_prefix_filters_candidates() {
        val result = StaticCompleter("en", "es", "all").complete(CompletionContext("e"))

        assertEquals(listOf("en", "es"), result.candidates.map { it.value })
    }

    @Test
    fun test_map_constructor_carries_descriptions() {
        val completer = StaticCompleter(mapOf("md" to "Markdown", "man" to "Man pages"))

        val result = completer.complete(CompletionContext("md"))

        assertEquals("Markdown", result.candidates.single().description)
    }

    @Test
    fun test_no_match_returns_empty() {
        val result = StaticCompleter("md", "man").complete(CompletionContext("x"))

        assertTrue(result.candidates.isEmpty())
    }
}
