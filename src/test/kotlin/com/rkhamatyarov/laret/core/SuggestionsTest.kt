package com.rkhamatyarov.laret.core

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestionsTest {

    @Test
    fun `single close match formats a singular hint`() {
        assertEquals("Did you mean 'create'?", Suggestions.didYouMean("creat", listOf("create", "delete")))
    }

    @Test
    fun `several close matches format a list hint`() {
        val hint = Suggestions.didYouMean("cot", listOf("cat", "cut", "dog"))
        // cat and cut are both one edit away from cot.
        assertEquals("Did you mean one of: cat, cut?", hint)
    }

    @Test
    fun `no hint when nothing is close`() {
        assertNull(Suggestions.didYouMean("zzzzzz", listOf("create", "delete")))
    }

    @Test
    fun `unknown flag warning includes a suggestion`() {
        val err = captureErr {
            Suggestions.warnUnknownFlag("--focre", listOf("--force", "-f", "--content", "-c"))
        }
        assertTrue(err.contains("Unknown flag '--focre'"), err)
        assertTrue(err.contains("Did you mean '--force'?"), err)
    }

    @Test
    fun `unknown flag warning without a close option still reports the flag`() {
        val err = captureErr {
            Suggestions.warnUnknownFlag("--zzzzz", listOf("--force"))
        }
        assertTrue(err.contains("Unknown flag '--zzzzz'."), err)
        assertTrue(!err.contains("Did you mean"), err)
    }

    private fun captureErr(block: () -> Unit): String {
        val buf = ByteArrayOutputStream()
        val original = System.err
        System.setErr(PrintStream(buf))
        try {
            block()
        } finally {
            System.setErr(original)
        }
        return buf.toString()
    }
}
