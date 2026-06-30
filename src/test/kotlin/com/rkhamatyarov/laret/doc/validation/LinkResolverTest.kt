package com.rkhamatyarov.laret.doc.validation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkResolverTest {

    private val resolver = LinkResolver(
        setOf("en/index.md", "en/file/index.md", "en/file/create.md", "en/dir/list.md"),
    )

    @Test
    fun test_resolves_sibling_link() {
        assertEquals("en/file/create.md", resolver.resolve("en/file/index.md", "create.md"))
    }

    @Test
    fun test_resolves_parent_traversal() {
        assertEquals("en/dir/list.md", resolver.resolve("en/file/create.md", "../dir/list.md"))
    }

    @Test
    fun test_resolves_root_absolute_link() {
        assertEquals("en/index.md", resolver.resolve("en/file/create.md", "/en/index.md"))
    }

    @Test
    fun test_escaping_the_root_is_unresolvable() {
        assertNull(resolver.resolve("en/index.md", "../../etc/passwd"))
    }

    @Test
    fun test_isKnown_reflects_registry() {
        assertTrue(resolver.isKnown("en/file/create.md"))
        assertFalse(resolver.isKnown("en/file/missing.md"))
    }
}
