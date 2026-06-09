package com.rkhamatyarov.laret.doc.generators

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.doc.prose.Prose
import com.rkhamatyarov.laret.doc.prose.ProseProvider
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("GroffDocGenerator")
class GroffDocGeneratorTest {

    private val app = CliApp(
        name = "laret",
        version = "1.0.0",
        groups = listOf(
            CommandGroup(
                name = "file",
                commands = listOf(Command(name = "create", description = "Create a new file")),
            ),
        ),
    )

    private fun providerReturning(prose: Prose): ProseProvider = mockk {
        every { resolve(any(), any(), any()) } returns prose
    }

    private val prose = Prose(
        title = "Create a file",
        summary = "Create a new file.",
        synopsis = null,
        examples = emptyList(),
        seeAlso = listOf("laret-file-delete"),
        body = "",
    )

    @Test
    fun test_groff_generator_uses_flat_man1_structure() {
        val files = GroffDocGenerator(providerReturning(prose)).generate(app, "en")

        assertEquals("man1/laret-file-create.1", files.first().relativePath)
    }

    @Test
    fun test_groff_generator_emits_valid_troff_header() {
        val content = GroffDocGenerator(providerReturning(prose)).generate(app, "en").first().content

        assertTrue(content.startsWith(".TH "))
    }

    @Test
    fun test_groff_generator_includes_prose_see_also() {
        val content = GroffDocGenerator(providerReturning(prose)).generate(app, "en").first().content

        assertTrue(content.contains("laret-file-delete"))
    }
}
