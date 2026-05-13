package com.rkhamatyarov.laret.scaffold.generator

import com.rkhamatyarov.laret.scaffold.model.Module
import com.rkhamatyarov.laret.scaffold.model.ScaffoldConfig
import com.rkhamatyarov.laret.scaffold.model.ShellTarget
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellTestGeneratorTest {
    private val generator = ShellTestGenerator()

    private fun config(targets: Set<ShellTarget>) = ScaffoldConfig(
        projectName = "my-cli",
        packageName = "com.example.mycli",
        appName = "my-cli",
        laretVersion = "0.2.0",
        modules = Module.entries.toSet(),
        shellTests = targets,
        graalvm = false,
    )

    @Test
    fun `empty shellTests produces no artifacts`() {
        val artifacts = generator.generate(config(emptySet()))
        assertTrue(artifacts.isEmpty())
    }

    @Test
    fun `bash target produces executable bash script with appName substituted`() {
        val artifacts = generator.generate(config(setOf(ShellTarget.BASH)))
        assertEquals(1, artifacts.size)
        val a = artifacts.single()
        assertEquals("tests/test-precedence.bash", a.relativePath)
        assertTrue(a.executable)
        assertTrue(a.content.contains("#!/usr/bin/env bash"))
        assertTrue(a.content.contains("my-cli hello run"))
        assertTrue(a.content.contains("MY_CLI_GREETING_NAME"))
        assertTrue(a.content.contains("Precedence test passed"))
    }

    @Test
    fun `zsh target produces zsh script with zsh-specific syntax`() {
        val artifacts = generator.generate(config(setOf(ShellTarget.ZSH)))
        val a = artifacts.single()
        assertEquals("tests/test-precedence.zsh", a.relativePath)
        assertTrue(a.executable)
        assertTrue(a.content.contains("#!/usr/bin/env zsh"))
        assertTrue(a.content.contains("[[ \"\$OUTPUT\" =="))
    }

    @Test
    fun `powershell target produces ps1 script not marked executable`() {
        val artifacts = generator.generate(config(setOf(ShellTarget.POWERSHELL)))
        val a = artifacts.single()
        assertEquals("tests/test-precedence.ps1", a.relativePath)
        assertEquals(false, a.executable)
        assertTrue(a.content.contains("\$ErrorActionPreference"))
        assertTrue(a.content.contains("MY_CLI_GREETING_NAME"))
        assertTrue(a.content.contains("Precedence test passed"))
    }

    @Test
    fun `all three targets produce three distinct artifacts`() {
        val artifacts = generator.generate(config(ShellTarget.entries.toSet()))
        assertEquals(3, artifacts.size)
        val paths = artifacts.map { it.relativePath }.toSet()
        assertTrue(paths.contains("tests/test-precedence.bash"))
        assertTrue(paths.contains("tests/test-precedence.zsh"))
        assertTrue(paths.contains("tests/test-precedence.ps1"))
    }

    @Test
    fun `env prefix sanitises hyphens to underscores`() {
        val cfg = config(setOf(ShellTarget.BASH)).copy(appName = "my-cool-cli")
        val a = generator.generate(cfg).single()
        assertTrue(a.content.contains("MY_COOL_CLI_GREETING_NAME"))
    }
}
