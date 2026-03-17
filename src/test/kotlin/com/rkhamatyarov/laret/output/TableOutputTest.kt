package com.rkhamatyarov.laret.output

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableOutputTest {
    @Test
    fun `table output should have name 'table'`() {
        assertEquals("table", TableOutput.name)
    }

    @Test
    fun `render empty list`() {
        val result = TableOutput.render(emptyList<Any>())
        assertTrue(result.contains("No data"))
    }

    @Test
    fun `render list of maps`() {
        val data =
            listOf(
                mapOf("name" to "file1.txt", "size" to 1024, "isDirectory" to false),
                mapOf("name" to "docs", "size" to 4096, "isDirectory" to true),
            )
        val result = TableOutput.render(data)

        assertTrue(result.contains("name"))
        assertTrue(result.contains("size"))
        assertTrue(result.contains("isDirectory"))
        assertTrue(result.contains("file1.txt"))
        assertTrue(result.contains("1024"))
        assertTrue(result.contains("docs"))
        assertTrue(result.contains("4096"))
        assertTrue(result.contains("true"))
    }

    @Test
    fun `render single map`() {
        val data = mapOf("name" to "file1.txt", "size" to 1024, "isDirectory" to false)
        val result = TableOutput.render(data)

        assertTrue(result.contains("name"))
        assertTrue(result.contains("size"))
        assertTrue(result.contains("isDirectory"))
        assertTrue(result.contains("file1.txt"))
        assertTrue(result.contains("1024"))
        assertTrue(result.contains("false"))
    }

    @Test
    fun `render nonmap list falls back to joinToString`() {
        val data = listOf("just", "strings")
        val result = TableOutput.render(data)
        assertTrue(result.contains("just"))
        assertTrue(result.contains("strings"))
        assertTrue(!result.contains("┌"))
    }
}
