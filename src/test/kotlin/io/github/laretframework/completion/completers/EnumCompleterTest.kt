package io.github.laretframework.completion.completers

import io.github.laretframework.completion.CompletionContext
import io.github.laretframework.completion.ShellType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("EnumCompleter")
class EnumCompleterTest {

    @Test
    fun test_completes_lowercased_enum_constants() {
        val result = EnumCompleter(ShellType.entries).complete(CompletionContext(""))

        assertEquals(
            ShellType.entries.map { it.name.lowercase() },
            result.candidates.map { it.value },
        )
    }

    @Test
    fun test_prefix_filters_constants() {
        val result = EnumCompleter(ShellType.entries).complete(CompletionContext("ba"))

        assertEquals(listOf("bash"), result.candidates.map { it.value })
    }

    @Test
    fun test_no_match_returns_empty() {
        val result = EnumCompleter(ShellType.entries).complete(CompletionContext("fish"))

        assertTrue(result.candidates.isEmpty())
    }
}
