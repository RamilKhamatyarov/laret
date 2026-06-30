package com.rkhamatyarov.laret.doc.validation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownLinkExtractorTest {

    @Test
    fun test_extracts_text_and_target() {
        val links = MarkdownLinkExtractor.extract("see [the page](file/create.md) now")

        assertEquals(1, links.size)
        assertEquals("the page", links[0].text)
        assertEquals("file/create.md", links[0].target)
    }

    @Test
    fun test_ignores_links_inside_fenced_code() {
        val md = "real [a](a.md)\n```\nfake [b](b.md)\n```\n"

        val targets = MarkdownLinkExtractor.extract(md).map { it.target }

        assertEquals(listOf("a.md"), targets)
    }

    @Test
    fun test_ignores_links_inside_inline_code() {
        val targets = MarkdownLinkExtractor.extract("use `[x](x.md)` but [y](y.md)").map { it.target }

        assertEquals(listOf("y.md"), targets)
    }

    @Test
    fun test_strips_optional_link_title() {
        val link = MarkdownLinkExtractor.extract("""[a](a.md "Title")""").single()

        assertEquals("a.md", link.target)
    }

    @Test
    fun test_classifies_link_kinds() {
        val external = MarkdownLink("e", "https://example.com")
        val anchor = MarkdownLink("a", "#section")
        val internal = MarkdownLink("i", "../dir/list.md#opts")

        assertTrue(external.isExternal)
        assertTrue(anchor.isAnchorOnly)
        assertTrue(internal.isInternalMarkdown)
        assertFalse(internal.isExternal)
        assertEquals("../dir/list.md", internal.filePart)
        assertEquals("opts", internal.anchorPart)
    }
}
