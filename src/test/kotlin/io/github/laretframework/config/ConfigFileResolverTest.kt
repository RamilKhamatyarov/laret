package io.github.laretframework.config

import io.github.laretframework.config.registry.ConfigFileReader
import io.github.laretframework.config.registry.FileLayer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigFileResolverTest {
    @Test
    fun test_working_directory_toml_precedes_home_yaml_for_file_layer() {
        val work = File("/workspace")
        val home = File("/home/user")
        val layer = FileLayer(
            reader = ConfigFileReader { mapOf("source" to it.extension) },
            exists = { it == File(work, ".laret.toml") || it == File(home, ".laret.yml") },
            workDir = work,
            homeDir = home,
        )

        assertEquals("toml", layer.get("source"))
        assertEquals(File(work, ".laret.toml"), layer.selectedFile())
    }

    @Test
    fun test_resolver_searches_yaml_in_home_and_keeps_explicit_profile_base_order() {
        val work = File("/workspace")
        val home = File("/home/user")
        val resolver = ConfigFileResolver(work, home) { it == File(home, ".laret.yaml") }

        assertEquals(File(home, ".laret.yaml"), resolver.resolve(null, null))
        assertNull(ConfigFileResolver(work, home) { false }.resolve(null, "dev"))
    }

    @Test
    fun test_explicit_then_profile_then_base_are_searched_in_order() {
        val work = File("/workspace")
        val home = File("/home/user")
        val candidates = mutableListOf<File>()
        val resolver = ConfigFileResolver(work, home) {
            candidates.add(it)
            it == File(home, ".laret.dev.toml") || it == File(work, ".laret.yml")
        }

        assertEquals(File(home, ".laret.dev.toml"), resolver.resolve("missing.json", "dev"))
        assertEquals(File("missing.json"), candidates.first())
        assertEquals(File(work, ".laret.dev.yml"), candidates[1])
    }

    @Test
    fun test_missing_explicit_path_is_unresolved_even_when_a_fallback_exists() {
        val work = File("/workspace")
        val resolver = ConfigFileResolver(work, File("/home/user")) { it == File(work, ".laret.yml") }

        assertEquals(File(work, ".laret.yml"), resolver.resolve("typo.yml", null))
        assertEquals(
            UnresolvedConfigSelection(UnresolvedConfigSelection.Selector.CONFIG, "typo.yml"),
            resolver.unresolved("typo.yml", null),
        )
    }

    @Test
    fun test_missing_profile_is_unresolved_even_when_a_base_file_exists() {
        val work = File("/workspace")
        val resolver = ConfigFileResolver(work, File("/home/user")) { it == File(work, ".laret.yml") }

        assertEquals(
            UnresolvedConfigSelection(UnresolvedConfigSelection.Selector.PROFILE, "prod"),
            resolver.unresolved(null, "prod"),
        )
    }

    @Test
    fun test_resolved_selectors_and_no_selectors_are_not_unresolved() {
        val work = File("/workspace")
        val home = File("/home/user")
        val resolver = ConfigFileResolver(work, home) {
            it == File("explicit.yml") || it == File(home, ".laret.dev.toml")
        }

        assertNull(resolver.unresolved("explicit.yml", null))
        assertNull(resolver.unresolved(null, "dev"))
        assertNull(resolver.unresolved("explicit.yml", "missing-profile"))
        assertNull(resolver.unresolved(null, null))
        assertNull(resolver.unresolved(null, " "))
    }

    @Test
    fun test_blank_profile_uses_base_candidates() {
        val work = File("/workspace")
        val resolver = ConfigFileResolver(work, File("/home/user")) { it == File(work, ".laret.yml") }
        assertEquals(File(work, ".laret.yml"), resolver.resolve(null, " "))
    }
}
