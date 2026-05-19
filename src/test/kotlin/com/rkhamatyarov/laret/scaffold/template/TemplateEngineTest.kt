package com.rkhamatyarov.laret.scaffold.template

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateEngineTest {
    private val engine = TemplateEngine()

    @Test
    fun `single variable is substituted`() {
        val result = engine.render("Hello, \${name}!", mapOf("name" to "World"))
        assertEquals("Hello, World!", result)
    }

    @Test
    fun `multiple variables are substituted`() {
        val result = engine.render("\${a}-\${b}-\${a}", mapOf("a" to "X", "b" to "Y"))
        assertEquals("X-Y-X", result)
    }

    @Test
    fun `missing variable keeps placeholder`() {
        val result = engine.render("Hi \${unknown}", mapOf("name" to "World"))
        assertEquals("Hi \${unknown}", result)
    }

    @Test
    fun `single dollar without brace is preserved as literal`() {
        val result = engine.render("price = \$amount", mapOf("amount" to "wrong"))
        assertEquals("price = \$amount", result)
    }

    @Test
    fun `unknown nested braces are preserved as literal kotlin string template`() {
        val input = """println("v = ${'$'}{flagName.ifBlank { "<unset>" }}")"""
        val result = engine.render(input, emptyMap())
        assertEquals(input, result)
    }

    @Test
    fun `if conditional includes body when truthy boolean`() {
        val tpl = "A{{#if flag}}-B{{/if}}-C"
        assertEquals("A-B-C", engine.render(tpl, mapOf("flag" to true)))
    }

    @Test
    fun `if conditional skips body when falsy boolean`() {
        val tpl = "A{{#if flag}}-B{{/if}}-C"
        assertEquals("A-C", engine.render(tpl, mapOf("flag" to false)))
    }

    @Test
    fun `if conditional skips body when key is missing`() {
        val tpl = "A{{#if absent}}-B{{/if}}-C"
        assertEquals("A-C", engine.render(tpl, emptyMap()))
    }

    @Test
    fun `if conditional includes body for non-empty collection`() {
        val tpl = "{{#if items}}has-items{{/if}}"
        assertEquals("has-items", engine.render(tpl, mapOf("items" to listOf(1, 2))))
    }

    @Test
    fun `if conditional skips body for empty collection`() {
        val tpl = "{{#if items}}has-items{{/if}}"
        assertEquals("", engine.render(tpl, mapOf("items" to emptyList<Int>())))
    }

    @Test
    fun `substitution happens inside conditional body`() {
        val tpl = "{{#if show}}name=\${name}{{/if}}"
        assertEquals("name=Bob", engine.render(tpl, mapOf("show" to true, "name" to "Bob")))
    }

    @Test
    fun `nested conditionals are resolved`() {
        val tpl = "{{#if a}}A{{#if b}}B{{/if}}A{{/if}}"
        assertEquals("ABA", engine.render(tpl, mapOf("a" to true, "b" to true)))
        assertEquals("AA", engine.render(tpl, mapOf("a" to true, "b" to false)))
        assertEquals("", engine.render(tpl, mapOf("a" to false, "b" to true)))
    }

    @Test
    fun `copyResourceTemplate loads existing scaffold template`() {
        val content = engine.copyResourceTemplate("templates/scaffold/gitignore.tpl")
        assertTrue(content.contains(".gradle/"))
    }

    @Test
    fun `copyResourceTemplate throws for missing resource`() {
        assertThrows<IllegalArgumentException> {
            engine.copyResourceTemplate("templates/scaffold/does-not-exist.tpl")
        }
    }

    @Test
    fun `unclosed if block preserves placeholder`() {
        val tpl = "X{{#if flag}}Y"
        val result = engine.render(tpl, mapOf("flag" to true))
        assertTrue(result.contains("{{#if flag}}"))
    }
}
