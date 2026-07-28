package com.rkhamatyarov.laret.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiddlewareRegistryTest {

    private fun mw(label: String, prio: Int = 0): Middleware = object : Middleware {
        override val priority = prio
        override val name = label
        override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) = next()
    }

    private fun registry(
        global: List<MiddlewareRegistration> = emptyList(),
        group: List<MiddlewareRegistration> = emptyList(),
        command: List<MiddlewareRegistration> = emptyList(),
    ) = MiddlewareRegistry.of(global, group, command)

    private fun global(label: String, prio: Int = 0) =
        MiddlewareRegistration(mw(label, prio), MiddlewareScope.GLOBAL, "*")

    private fun group(label: String, target: String, prio: Int = 0) =
        MiddlewareRegistration(mw(label, prio), MiddlewareScope.GROUP, target)

    private fun command(label: String, target: String, prio: Int = 0) =
        MiddlewareRegistration(mw(label, prio), MiddlewareScope.COMMAND, target)

    @Test
    fun `global middleware applies to every command`() {
        val reg = registry(global = listOf(global("g")))

        assertEquals(listOf("g"), reg.resolve("file", "create").map { it.name })
        assertEquals(listOf("g"), reg.resolve("dir", "list").map { it.name })
    }

    @Test
    fun `group middleware applies only to its own group`() {
        val reg = registry(group = listOf(group("audit", "file")))

        assertEquals(listOf("audit"), reg.resolve("file", "create").map { it.name })
        assertTrue(reg.resolve("dir", "create").isEmpty())
    }

    @Test
    fun `command middleware applies only to its own command`() {
        val reg = registry(command = listOf(command("confirm", "file delete")))

        assertEquals(listOf("confirm"), reg.resolve("file", "delete").map { it.name })
        assertTrue(reg.resolve("file", "create").isEmpty())
    }

    @Test
    fun `command middleware does not leak across groups sharing a command name`() {
        val reg = registry(command = listOf(command("plugin-only", "plugin list")))

        assertEquals(listOf("plugin-only"), reg.resolve("plugin", "list").map { it.name })
        assertTrue(reg.resolve("stats", "list").isEmpty())
        assertTrue(reg.resolve("locale", "list").isEmpty())
    }

    @Test
    fun `lower priority sorts outermost across all scopes`() {
        val reg = registry(
            global = listOf(global("stats", -1000), global("log", 100)),
            group = listOf(group("audit", "file", 0)),
            command = listOf(command("confirm", "file delete", 10)),
        )

        assertEquals(
            listOf("stats", "audit", "confirm", "log"),
            reg.resolve("file", "delete").map { it.name },
        )
    }

    @Test
    fun `command scoped middleware can wrap a global one with a lower priority`() {
        val reg = registry(
            global = listOf(global("stats", -1000)),
            command = listOf(command("trace", "file delete", -9999)),
        )

        assertEquals(listOf("trace", "stats"), reg.resolve("file", "delete").map { it.name })
    }

    @Test
    fun `equal priorities keep registration order`() {
        val reg = registry(
            global = listOf(global("first", 0), global("second", 0)),
            group = listOf(group("third", "file", 0)),
        )

        assertEquals(listOf("first", "second", "third"), reg.resolve("file", "create").map { it.name })
    }

    @Test
    fun `all returns every registration regardless of scope`() {
        val reg = registry(
            global = listOf(global("g")),
            group = listOf(group("gr", "file")),
            command = listOf(command("c", "file delete")),
        )

        assertEquals(setOf("g", "gr", "c"), reg.all().map { it.name }.toSet())
    }

    @Test
    fun `empty registry resolves to nothing`() {
        assertTrue(MiddlewareRegistry.EMPTY.isEmpty())
        assertTrue(MiddlewareRegistry.EMPTY.resolve("file", "create").isEmpty())
        assertTrue(MiddlewareRegistry.EMPTY.all().isEmpty())
    }

    @Test
    fun `commandKey qualifies with the group name`() {
        assertEquals("file:delete", MiddlewareRegistry.commandKey("file", "delete"))
    }

    @Test
    fun `default name falls back for anonymous middleware`() {
        val anonymous = object : Middleware {
            override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) = next()
        }

        assertEquals("<anonymous>", anonymous.name)
    }
}
