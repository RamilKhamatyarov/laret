package com.rkhamatyarov.laret.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EditDistanceTest {

    @Test
    fun `identical strings have distance zero`() {
        assertEquals(0, EditDistance.damerau("create", "create"))
    }

    @Test
    fun `single substitution costs one`() {
        assertEquals(1, EditDistance.damerau("creata", "create"))
    }

    @Test
    fun `single deletion costs one`() {
        assertEquals(1, EditDistance.damerau("creat", "create"))
    }

    @Test
    fun `single insertion costs one`() {
        assertEquals(1, EditDistance.damerau("createe", "create"))
    }

    @Test
    fun `adjacent transposition costs one`() {
        // Plain Levenshtein would score this 2.
        assertEquals(1, EditDistance.damerau("craete", "create"))
    }

    @Test
    fun `comparison is case insensitive`() {
        assertEquals(0, EditDistance.damerau("CREATE", "create"))
    }

    @Test
    fun `empty string distance equals other length`() {
        assertEquals(6, EditDistance.damerau("", "create"))
        assertEquals(4, EditDistance.damerau("file", ""))
    }

    @Test
    fun `unrelated words are far apart`() {
        assertEquals(6, EditDistance.damerau("zzzzzz", "create"))
    }
}
