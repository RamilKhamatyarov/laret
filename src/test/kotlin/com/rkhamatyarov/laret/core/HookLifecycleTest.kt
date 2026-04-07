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

class HookLifecycleTest {
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

    @Test
    fun `preExecute runs before action and can prevent execution`() = runTest {
        var preCalled = false
        var actionCalled = false

        val command = Command(
            name = "test",
            action = { actionCalled = true },
            preExecute = {
                preCalled = true
                throw RuntimeException("prevent")
            },
        )

        val ctx = CommandContext(command, null, groupName = "g")
        val result = runCatching {
            command.preExecute(ctx)
            command.action(ctx)
        }

        assertTrue(result.isFailure)
        assertTrue(preCalled)
        assertFalse(actionCalled)
    }

    @Test
    fun `postExecute receives action result and can modify context`() = runTest {
        var postExecuted = false
        var resultValue = ""

        val command = Command(
            name = "test",
            action = { ctx ->
                ctx.arguments["result"] = "ok"
            },
            postExecute = { ctx ->
                postExecuted = true
                resultValue = ctx.arguments["result"] ?: ""
            },
        )

        val ctx = CommandContext(command, null, groupName = "g")
        command.action(ctx)
        command.postExecute(ctx)

        assertTrue(postExecuted)
        assertEquals("ok", resultValue)
    }

    @Test
    fun `onError catches exception from action`() = runTest {
        var errorCaught = false
        var errorMessage = ""

        val command = Command(
            name = "test",
            action = { throw RuntimeException("action failed") },
            onError = { _, e ->
                errorCaught = true
                errorMessage = e.message ?: ""
            },
        )

        val ctx = CommandContext(command, null, groupName = "g")
        try {
            command.action(ctx)
        } catch (e: Exception) {
            command.onError(ctx, e)
        }

        assertTrue(errorCaught)
        assertEquals("action failed", errorMessage)
    }

    @Test
    fun `onError catches exception from preExecute`() = runTest {
        var errorCaught = false

        val command = Command(
            name = "test",
            preExecute = { throw RuntimeException("pre error") },
            onError = { _, _ -> errorCaught = true },
        )

        val ctx = CommandContext(command, null, groupName = "g")
        try {
            command.preExecute(ctx)
        } catch (e: Exception) {
            command.onError(ctx, e)
        }

        assertTrue(errorCaught)
    }

    @Test
    fun `hooks are optional - commands without hooks still work`() = runTest {
        var actionCalled = false
        val command = Command(
            name = "test",
            action = { actionCalled = true },
        )

        val ctx = CommandContext(command, null, groupName = "g")
        command.action(ctx)

        assertTrue(actionCalled)
    }

    @Test
    fun `app-level hooks onAppInit and onAppShutdown execute once`() = runTest {
        var initCalled = 0
        var shutdownCalled = 0

        val app = cli(name = "test", version = "1.0") {
            onAppInit = { initCalled++ }
            onAppShutdown = { shutdownCalled++ }
        }

        app.init()
        app.shutdown()

        assertEquals(1, initCalled)
        assertEquals(1, shutdownCalled)
    }
}
