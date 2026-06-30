package com.rkhamatyarov.laret.doc.validation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnchorValidatorTest {

    @Test
    fun test_slugify_lowercases_and_hyphenates() {
        assertEquals("see-also", AnchorValidator.slugify("See Also"))
    }

    @Test
    fun test_slugify_drops_punctuation() {
        assertEquals("hello-world", AnchorValidator.slugify("Hello, World!"))
    }

    @Test
    fun test_extracts_heading_slugs() {
        val md = "# Create a File\n\n## Synopsis\n\ntext\n\n### See Also\n"

        val slugs = AnchorValidator.headingSlugs(md)

        assertEquals(setOf("create-a-file", "synopsis", "see-also"), slugs)
    }

    @Test
    fun test_ignores_hashes_inside_code_blocks() {
        val md = "# Real\n```\n# Fake Heading\n```\n"

        assertEquals(setOf("real"), AnchorValidator.headingSlugs(md))
    }

    @Test
    fun test_hasAnchor_matches_existing_heading() {
        val md = "# Title\n\n## Options\n"

        assertTrue(AnchorValidator.hasAnchor(md, "options"))
        assertFalse(AnchorValidator.hasAnchor(md, "missing"))
    }
}
