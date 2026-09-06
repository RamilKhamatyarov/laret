package com.rkhamatyarov.laret.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidationBuilderTest {

    private fun errors(value: String, block: ValidationBuilder.() -> Unit): List<String> =
        buildValidators(block).mapNotNull { it.validate(value) }

    @Test
    fun `regex accepts a matching value and rejects others`() {
        assertTrue(errors("v12") { regex("^v\\d+$") }.isEmpty())
        assertEquals(1, errors("nope") { regex("^v\\d+$") }.size)
    }

    @Test
    fun `range accepts inside and rejects outside`() {
        assertTrue(errors("8080") { range(1, 65535) }.isEmpty())
        assertEquals(1, errors("99999") { range(1, 65535) }.size)
        assertEquals(1, errors("0") { range(1, 65535) }.size)
    }

    @Test
    fun `numeric validators reject a non-number with a number message`() {
        val messages = errors("abc") { range(1, 10) }
        assertEquals(1, messages.size)
        assertTrue(messages.first().contains("number"), messages.first())
    }

    @Test
    fun `min and max bound the value`() {
        assertTrue(errors("5") { min(5) }.isEmpty())
        assertEquals(1, errors("4") { min(5) }.size)
        assertTrue(errors("5") { max(5) }.isEmpty())
        assertEquals(1, errors("6") { max(5) }.size)
    }

    @Test
    fun `length validators bound the string`() {
        assertTrue(errors("abc") { minLength(3) }.isEmpty())
        assertEquals(1, errors("ab") { minLength(3) }.size)
        assertTrue(errors("abc") { maxLength(3) }.isEmpty())
        assertEquals(1, errors("abcd") { maxLength(3) }.size)
    }

    @Test
    fun `oneOf restricts to the listed choices`() {
        assertTrue(errors("json") { oneOf("json", "yaml") }.isEmpty())
        val messages = errors("xml") { oneOf("json", "yaml") }
        assertEquals(1, messages.size)
        assertTrue(messages.first().contains("json, yaml"), messages.first())
    }

    @Test
    fun `notBlank runs on blank values`() {
        val validator = buildValidators { notBlank() }.single()
        assertTrue(validator.runsOnBlank)
        assertNotNull(validator.validate(""))
        assertNotNull(validator.validate("   "))
        assertNull(validator.validate("x"))
    }

    @Test
    fun `other validators do not run on blank values by default`() {
        val validator = buildValidators { regex("^v\\d+$") }.single()
        assertFalse(validator.runsOnBlank)
    }

    @Test
    fun `fileExists accepts a real file and rejects a missing one`(@TempDir dir: Path) {
        val file = Files.createFile(dir.resolve("present.txt"))
        assertTrue(errors(file.toString()) { fileExists() }.isEmpty())
        assertEquals(1, errors(dir.resolve("missing.txt").toString()) { fileExists() }.size)

        assertEquals(1, errors(dir.toString()) { fileExists() }.size)
    }

    @Test
    fun `dirExists accepts a real directory and rejects a missing one`(@TempDir dir: Path) {
        assertTrue(errors(dir.toString()) { dirExists() }.isEmpty())
        assertEquals(1, errors(dir.resolve("nope").toString()) { dirExists() }.size)
    }

    @Test
    fun `custom uses its own predicate and literal message`() {
        val messages = errors("3") { custom("must be even") { it.toInt() % 2 == 0 } }
        assertEquals(listOf("must be even"), messages)
        assertTrue(errors("4") { custom("must be even") { it.toInt() % 2 == 0 } }.isEmpty())
    }

    @Test
    fun `an explicit message overrides the built-in text`() {
        val messages = errors("nope") { regex("^v\\d+$", message = "use vN form") }
        assertEquals(listOf("use vN form"), messages)
    }

    @Test
    fun `several rules on one field all report`() {
        val messages = errors("ab") {
            minLength(3)
            regex("^\\d+$")
        }
        assertEquals(2, messages.size)
    }

    @Test
    fun `no validate block yields no validators`() {
        assertTrue(buildValidators(null).isEmpty())
    }
}
