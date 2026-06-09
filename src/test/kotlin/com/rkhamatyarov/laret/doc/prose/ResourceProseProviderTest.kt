package com.rkhamatyarov.laret.doc.prose

import com.rkhamatyarov.laret.model.Command
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("ResourceProseProvider")
class ResourceProseProviderTest {

    private val provider = ResourceProseProvider()

    private fun cmd(name: String, description: String = "dsl description"): Command =
        Command(name = name, description = description)

    @Nested
    @DisplayName("happy path")
    inner class HappyPath {

        @Test
        fun `parses frontmatter and body for requested language`() {
            val prose = provider.resolve("testgroup", cmd("known"), "en")

            assertEquals("Known Command", prose.title)
            assertEquals("An English summary line.", prose.summary)
            assertEquals(listOf("laret testgroup known foo", "laret testgroup known bar"), prose.examples)
        }

        @Test
        fun `uses requested language file as-is when present`() {
            val prose = provider.resolve("testgroup", cmd("known"), "es")

            assertEquals("Comando Conocido", prose.title)
            assertEquals("Una línea de resumen en español.", prose.summary)
        }

        @Test
        fun `omitted frontmatter fields are empty and are not merged from english`() {
            val prose = provider.resolve("testgroup", cmd("known"), "es")

            assertTrue(prose.examples.isEmpty())
        }
    }

    @Nested
    @DisplayName("fallback")
    inner class Fallback {

        @Test
        fun `missing requested language falls back to english`() {
            val prose = provider.resolve("testgroup", cmd("known"), "fr")

            assertEquals("Known Command", prose.title)
        }

        @Test
        fun `missing file entirely falls back to DSL description`() {
            val prose = provider.resolve("testgroup", cmd("missing", "the dsl description"), "en")

            assertEquals("missing", prose.title)
            assertEquals("the dsl description", prose.summary)
            assertTrue(prose.body.isBlank())
        }
    }

    @Nested
    @DisplayName("error handling")
    inner class ErrorHandling {

        @Test
        fun `malformed frontmatter falls back to english`() {
            val prose = provider.resolve("testgroup", cmd("broken"), "es")

            assertEquals("Valid English Fallback", prose.title)
        }

        @Test
        fun `malformed english-only file falls back to DSL description`() {
            val prose = provider.resolve("testgroup", cmd("onlybad", "dsl fallback"), "en")

            assertEquals("dsl fallback", prose.summary)
        }
    }
}
