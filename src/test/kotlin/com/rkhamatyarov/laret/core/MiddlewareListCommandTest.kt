package com.rkhamatyarov.laret.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiddlewareListCommandTest {

    private fun mw(label: String, prio: Int): Middleware = object : Middleware {
        override val priority = prio
        override val name = label
        override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) = next()
    }

    private val populated = MiddlewareRegistry.of(
        global = listOf(
            MiddlewareRegistration(mw("StatsMiddleware", -1000), MiddlewareScope.GLOBAL, "*"),
            MiddlewareRegistration(mw("LoggingMiddleware", 0), MiddlewareScope.GLOBAL, "*"),
        ),
        group = listOf(
            MiddlewareRegistration(mw("AuditMiddleware", 0), MiddlewareScope.GROUP, "file"),
        ),
        command = listOf(
            MiddlewareRegistration(mw("ConfirmMiddleware", 10), MiddlewareScope.COMMAND, "file delete"),
        ),
    )

    @Test
    fun `registry listing shows name priority scope and target`() {
        val output = MiddlewareListCommand(populated).renderRegistry()

        assertTrue(output.startsWith("NAME"), "expected a header row, got: $output")
        assertTrue(output.contains("PRIORITY"))
        assertTrue(output.contains("SCOPE"))
        assertTrue(output.contains("TARGET"))
        assertTrue(output.contains("StatsMiddleware"))
        assertTrue(output.contains("-1000"))
        assertTrue(output.contains("GLOBAL"))
        assertTrue(output.contains("AuditMiddleware"))
        assertTrue(output.contains("GROUP"))
        assertTrue(output.contains("ConfirmMiddleware"))
        assertTrue(output.contains("COMMAND"))
        assertTrue(output.contains("file delete"))
    }

    @Test
    fun `empty registry reports nothing registered`() {
        assertEquals("No middleware registered\n", MiddlewareListCommand(MiddlewareRegistry.EMPTY).renderRegistry())
    }

    @Test
    fun `chain preview lists middleware outermost first`() {
        val output = MiddlewareListCommand(populated).renderChain("file", "delete")

        assertTrue(output.startsWith("Chain for 'file delete' (outermost first):"))
        val positions = listOf("StatsMiddleware", "LoggingMiddleware", "AuditMiddleware", "ConfirmMiddleware")
            .map { output.indexOf(it) }
        assertTrue(positions.all { it >= 0 }, "all middleware should appear: $output")
        assertEquals(positions.sorted(), positions, "expected priority order in output:\n$output")
        assertTrue(output.trimEnd().endsWith("→ action"))
    }

    @Test
    fun `chain preview excludes middleware scoped to other targets`() {
        val output = MiddlewareListCommand(populated).renderChain("dir", "list")

        assertTrue(output.contains("StatsMiddleware"))
        assertTrue(output.contains("LoggingMiddleware"))
        assertTrue(!output.contains("AuditMiddleware"), "group middleware must not leak: $output")
        assertTrue(!output.contains("ConfirmMiddleware"), "command middleware must not leak: $output")
    }

    @Test
    fun `chain preview reports when nothing applies`() {
        val output = MiddlewareListCommand(MiddlewareRegistry.EMPTY).renderChain("file", "delete")

        assertTrue(output.contains("(no middleware applies)"))
        assertTrue(output.trimEnd().endsWith("→ action"))
    }

    @Test
    fun `target parsing accepts space and colon forms`() {
        val renderer = MiddlewareListCommand(populated)

        assertEquals("file" to "delete", renderer.parseTarget("file delete"))
        assertEquals("file" to "delete", renderer.parseTarget("file:delete"))
        assertEquals("file" to "delete", renderer.parseTarget("  file   delete  "))
    }

    @Test
    fun `target parsing rejects malformed values`() {
        val renderer = MiddlewareListCommand(populated)

        assertNull(renderer.parseTarget("file"))
        assertNull(renderer.parseTarget(""))
        assertNull(renderer.parseTarget("file delete extra"))
    }
}
