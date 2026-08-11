package com.rkhamatyarov.laret.ui

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorSupportTest {

    private fun env(vararg pairs: Pair<String, String>): (String) -> String? {
        val map = pairs.toMap()
        return { map[it] }
    }

    private val noEnv: (String) -> String? = { null }

    @Test
    fun `windows 10 terminal enables color`() {
        assertTrue(
            ColorSupport.detect(noEnv, osName = "Windows 10", osVersion = "10.0", isTty = true),
        )
    }

    @Test
    fun `windows 11 terminal enables color`() {
        assertTrue(
            ColorSupport.detect(noEnv, osName = "Windows 11", osVersion = "11.0", isTty = true),
        )
    }

    @Test
    fun `windows 8_1 does not enable color despite lexical comparison`() {
        assertFalse(
            ColorSupport.detect(noEnv, osName = "Windows 8.1", osVersion = "6.3", isTty = true),
        )
    }

    @Test
    fun `non-terminal disables color even on windows 10`() {
        assertFalse(
            ColorSupport.detect(noEnv, osName = "Windows 10", osVersion = "10.0", isTty = false),
        )
    }

    @Test
    fun `NO_COLOR disables color on a capable terminal`() {
        assertFalse(
            ColorSupport.detect(env("NO_COLOR" to "1"), osName = "Windows 10", osVersion = "10.0", isTty = true),
        )
    }

    @Test
    fun `empty NO_COLOR is treated as unset`() {
        assertTrue(
            ColorSupport.detect(env("NO_COLOR" to ""), osName = "Windows 10", osVersion = "10.0", isTty = true),
        )
    }

    @Test
    fun `NO_COLOR wins over CLICOLOR_FORCE`() {
        assertFalse(
            ColorSupport.detect(
                env("NO_COLOR" to "1", "CLICOLOR_FORCE" to "1"),
                osName = "Windows 10",
                osVersion = "10.0",
                isTty = true,
            ),
        )
    }

    @Test
    fun `CLICOLOR_FORCE enables color even without a terminal`() {
        assertTrue(
            ColorSupport.detect(
                env("CLICOLOR_FORCE" to "1"),
                osName = "Linux",
                osVersion = "6.1",
                isTty = false,
            ),
        )
    }

    @Test
    fun `CLICOLOR_FORCE set to zero does not force color`() {
        assertFalse(
            ColorSupport.detect(
                env("CLICOLOR_FORCE" to "0"),
                osName = "Linux",
                osVersion = "6.1",
                isTty = false,
            ),
        )
    }

    @Test
    fun `linux terminal with a real TERM enables color`() {
        assertTrue(
            ColorSupport.detect(
                env("TERM" to "xterm-256color"),
                osName = "Linux",
                osVersion = "6.1",
                isTty = true,
            ),
        )
    }

    @Test
    fun `linux terminal with dumb TERM disables color`() {
        assertFalse(
            ColorSupport.detect(
                env("TERM" to "dumb"),
                osName = "Linux",
                osVersion = "6.1",
                isTty = true,
            ),
        )
    }

    @Test
    fun `linux terminal with no TERM disables color`() {
        assertFalse(
            ColorSupport.detect(noEnv, osName = "Linux", osVersion = "6.1", isTty = true),
        )
    }

    @Test
    fun `unparseable windows version disables color`() {
        assertFalse(
            ColorSupport.detect(noEnv, osName = "Windows", osVersion = "unknown", isTty = true),
        )
    }
}
