package com.rkhamatyarov.laret.scaffold.wizard

import com.rkhamatyarov.laret.scaffold.model.Module
import com.rkhamatyarov.laret.scaffold.model.ShellTarget
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InteractiveWizardTest {
    private fun wizardFor(vararg lines: String): InteractiveWizard {
        val inStream = ByteArrayInputStream(lines.joinToString("\n", postfix = "\n").toByteArray())
        val outStream = PrintStream(ByteArrayOutputStream())
        return InteractiveWizard(input = inStream, out = outStream)
    }

    @Test
    fun `wizard with all defaults returns valid config`() {
        val cfg = wizardFor(
            "my-cli",
            "com.example.mycli",
            "",
            "",
            "y",
            "",
            "n",
        ).runWizard()

        assertEquals("my-cli", cfg.projectName)
        assertEquals("com.example.mycli", cfg.packageName)
        assertEquals("my-cli", cfg.appName)
        assertEquals(Module.entries.toSet(), cfg.modules)
        assertEquals(ShellTarget.entries.toSet(), cfg.shellTests)
        assertEquals(false, cfg.graalvm)
    }

    @Test
    fun `wizard with explicit module selection limits modules`() {
        val cfg = wizardFor(
            "tool",
            "com.example.tool",
            "tool",
            "1,3",
            "n",
            "y",
        ).runWizard()
        assertEquals(setOf(Module.CONFIG, Module.UI), cfg.modules)
        assertTrue(cfg.shellTests.isEmpty())
        assertEquals(true, cfg.graalvm)
    }

    @Test
    fun `wizard recovers from invalid project name then accepts valid`() {
        val cfg = wizardFor(
            "BAD NAME",
            "good-name",
            "com.example.good",
            "good",
            "",
            "n",
            "n",
        ).runWizard()
        assertEquals("good-name", cfg.projectName)
    }

    @Test
    fun `wizard with explicit shell target selection limits targets`() {
        val cfg = wizardFor(
            "cli",
            "com.example.cli",
            "cli",
            "",
            "y",
            "1",
            "n",
        ).runWizard()
        assertEquals(setOf(ShellTarget.BASH), cfg.shellTests)
    }
}
