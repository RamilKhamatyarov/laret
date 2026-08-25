package com.rkhamatyarov.laret.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggesterTest {

    private val groups = listOf("file", "dir", "config", "completion", "redo", "replay")

    @Test
    fun `ranks the closest candidate first`() {
        val ranked = Suggester.rank("creat", listOf("create", "delete", "config"))
        assertEquals("create", ranked.first().value)
        assertEquals(1, ranked.first().distance)
    }

    @Test
    fun `transposition is preferred over more distant words`() {
        val ranked = Suggester.rank("craete", listOf("create", "delete"))
        assertEquals("create", ranked.first().value)
    }

    @Test
    fun `returns at most the limit sorted by distance then name`() {
        val ranked = Suggester.rank("redoo", groups, limit = 3)
        assertTrue(ranked.size <= 3)
        // "redoo" is one edit from "redo" and two from "replay".
        assertEquals("redo", ranked.first().value)
        assertTrue(ranked.zipWithNext().all { (a, b) -> a.distance <= b.distance })
    }

    @Test
    fun `no suggestion when nothing is close enough`() {
        assertTrue(Suggester.rank("zzzzzz", groups).isEmpty())
    }

    @Test
    fun `short input only matches distance one`() {
        // "x" (len 1) has an allowed cap of 1, so distance-2 candidates are excluded.
        val ranked = Suggester.rank("x", listOf("ab", "cd"))
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `exact match is not suggested`() {
        // distance 0 is filtered out (the token would have resolved normally).
        assertTrue(Suggester.rank("file", groups).none { it.value == "file" })
    }

    @Test
    fun `autoFix returns the unique distance-one candidate`() {
        assertEquals("create", Suggester.autoFix("creat", listOf("create", "delete")))
    }

    @Test
    fun `autoFix returns null when several candidates tie at distance one`() {
        assertNull(Suggester.autoFix("cat", listOf("car", "cot")))
    }

    @Test
    fun `autoFix returns null when nothing is one edit away`() {
        assertNull(Suggester.autoFix("zzz", listOf("create", "delete")))
    }
}
