package com.rkhamatyarov.laret.scaffold.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ScaffoldConfigTest {
    private fun base(
        projectName: String = "my-cli",
        packageName: String = "com.example.mycli",
        appName: String = "my-cli",
    ) = ScaffoldConfig(
        projectName = projectName,
        packageName = packageName,
        appName = appName,
        laretVersion = "0.2.0",
        modules = Module.entries.toSet(),
        shellTests = ShellTarget.entries.toSet(),
        graalvm = false,
    )

    @Test
    fun `valid kebab project name is accepted`() {
        val cfg = base(projectName = "my-cool-cli")
        assertEquals("my-cool-cli", cfg.projectName)
    }

    @Test
    fun `uppercase project name is rejected`() {
        assertThrows<IllegalArgumentException> { base(projectName = "MyCli") }
    }

    @Test
    fun `project name starting with digit is rejected`() {
        assertThrows<IllegalArgumentException> { base(projectName = "1cli") }
    }

    @Test
    fun `valid dotted package name is accepted`() {
        val cfg = base(packageName = "com.acme.tools.mycli")
        assertEquals("com.acme.tools.mycli", cfg.packageName)
    }

    @Test
    fun `single segment package name is rejected`() {
        assertThrows<IllegalArgumentException> { base(packageName = "mycli") }
    }

    @Test
    fun `package with uppercase is rejected`() {
        assertThrows<IllegalArgumentException> { base(packageName = "com.Acme.cli") }
    }

    @Test
    fun `blank app name is rejected`() {
        assertThrows<IllegalArgumentException> { base(appName = "  ") }
    }

    @Test
    fun `groupId derives from package by dropping last segment`() {
        val cfg = base(packageName = "com.example.mycli")
        assertEquals("com.example", cfg.groupId)
    }

    @Test
    fun `artifactId equals project name`() {
        val cfg = base(projectName = "my-cli")
        assertEquals("my-cli", cfg.artifactId)
    }
}
