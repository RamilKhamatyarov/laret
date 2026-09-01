package com.rkhamatyarov.laret.watch

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobMatcherTest {

    @Test
    fun `extension glob matches nested files by filename`() {
        val m = GlobMatcher(listOf("*.kt"))
        assertTrue(m.matches("Main.kt"))
        assertTrue(m.matches("src/app/Main.kt"))
        assertFalse(m.matches("src/app/Main.java"))
    }

    @Test
    fun `double-star glob matches across directories`() {
        val m = GlobMatcher(listOf("src/**/*.kt"))
        assertTrue(m.matches("src/app/Main.kt"))
        assertFalse(m.matches("test/app/Main.kt"))
    }

    @Test
    fun `exclude pattern suppresses a match`() {
        val m = GlobMatcher(listOf("**/*.kt", "!**/generated/**"))
        assertTrue(m.matches("src/app/Main.kt"))
        assertFalse(m.matches("src/generated/Gen.kt"))
    }

    @Test
    fun `double-star exclude also matches a top-level directory`() {
        // Regression: NIO's `**/gen/**` alone misses a root-level `gen/`.
        val m = GlobMatcher(listOf("**/*.kt", "!**/gen/**"))
        assertTrue(m.matches("src/app/Main.kt"))
        assertFalse(m.matches("gen/Z.kt"))
        assertFalse(m.matches("gen/nested/Z.kt"))
    }

    @Test
    fun `no include patterns matches everything except excludes`() {
        val m = GlobMatcher(listOf("!**/build/**"))
        assertTrue(m.matches("src/app/Main.kt"))
        assertTrue(m.matches("README.md"))
        assertFalse(m.matches("app/build/tmp/x.class"))
    }

    @Test
    fun `brace expansion matches alternatives`() {
        val m = GlobMatcher(listOf("*.{kt,kts}"))
        assertTrue(m.matches("Main.kt"))
        assertTrue(m.matches("build.gradle.kts"))
        assertFalse(m.matches("Main.java"))
    }

    @Test
    fun `empty pattern list matches everything`() {
        val m = GlobMatcher(emptyList())
        assertTrue(m.matches("anything.xyz"))
        assertTrue(m.matches("a/b/c.txt"))
    }
}
