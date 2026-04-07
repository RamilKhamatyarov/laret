package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.dsl.cli
import com.rkhamatyarov.laret.model.Command
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiddlewareChainTest {
    private lateinit var buf: ByteArrayOutputStream
    private val originalOut = System.out
    private val originalErr = System.err

    @BeforeEach
    fun setup() {
        buf = ByteArrayOutputStream()
        val ps = PrintStream(buf)
        System.setOut(ps)
        System.setErr(ps)
        CommandRunner.globalMiddlewares = emptyList()
        CommandRunner.groupMiddlewares = emptyMap()
        CommandRunner.commandMiddlewares = emptyMap()
    }

    @AfterEach
    fun cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    private fun output() = buf.toString()
    private fun clear() = buf.reset()

    private fun createTestContext(command: Command = Command("test"), app: CliApp? = null): CommandContext =
        CommandContext(command, app, groupName = "test")

    @Test
    fun `middleware chain executes in priority order`() = runTest {
        val order = mutableListOf<String>()

        val m1 = object : Middleware {
            override val priority = 10
            override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
                order.add("m1")
                next()
            }
        }
        val m2 = object : Middleware {
            override val priority = 1
            override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
                order.add("m2")
                next()
            }
        }

        val sorted = listOf(m1, m2).sortedBy { it.priority }
        val chain = MiddlewareChain(sorted) { order.add("action") }
        chain.execute(createTestContext())

        assertEquals(listOf("m2", "m1", "action"), order)
    }

    @Test
    fun `middleware can short-circuit by not calling next`() = runTest {
        var actionCalled = false

        val blocking = object : Middleware {
            override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
                // do NOT call next()
            }
        }

        val chain = MiddlewareChain(listOf(blocking)) { actionCalled = true }
        chain.execute(createTestContext())

        assertFalse(actionCalled)
    }

    @Test
    fun `middleware can modify context for next middleware`() = runTest {
        val ctx = createTestContext()
        ctx.arguments["key"] = "original"

        val modifier = object : Middleware {
            override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
                ctx.arguments["key"] = "modified"
                next()
            }
        }

        var finalValue = ""
        val chain = MiddlewareChain(listOf(modifier)) {
            finalValue = it.arguments["key"] ?: ""
        }
        chain.execute(ctx)

        assertEquals("modified", finalValue)
    }

    @Test
    fun `GLOBAL middleware runs for all commands, COMMAND only for specific`() = runTest {
        val executed = mutableListOf<String>()

        val global = object : Middleware {
            override val scope = MiddlewareScope.GLOBAL
            override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
                executed.add("global")
                next()
            }
        }
        val commandOnly = object : Middleware {
            override val scope = MiddlewareScope.COMMAND
            override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
                executed.add("command-only")
                next()
            }
        }

        CommandRunner.globalMiddlewares = listOf(global)
        CommandRunner.commandMiddlewares = mapOf("test:cmd" to listOf(commandOnly))

        val app = cli("test") {
            group("g") {
                command("cmd") { action {} }
            }
        }
        val ctx = CommandContext(app.groups[0].commands[0], app, groupName = "g")
        val chain = MiddlewareChain(listOf(global, commandOnly)) {}
        chain.execute(ctx)

        assertTrue("global" in executed)
        assertTrue("command-only" in executed)
    }

    @Test
    fun `exception in middleware propagates correctly`() = runTest {
        var actionCalled = false
        val failing = object : Middleware {
            override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) =
                throw RuntimeException("Middleware failure")
        }

        val chain = MiddlewareChain(listOf(failing)) { actionCalled = true }

        val exception = runCatching { chain.execute(createTestContext()) }.exceptionOrNull()
        assertTrue(exception is RuntimeException)
        assertEquals("Middleware failure", exception.message)
        assertFalse(actionCalled)
    }
}
