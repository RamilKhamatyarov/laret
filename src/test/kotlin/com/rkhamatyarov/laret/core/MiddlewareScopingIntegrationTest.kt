package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises scoping through the real DSL and dispatcher rather than by driving
 * [MiddlewareChain] directly, so a regression in the builder wiring is caught.
 */
class MiddlewareScopingIntegrationTest {
    private lateinit var buf: ByteArrayOutputStream
    private val originalOut = System.out
    private val originalErr = System.err

    @BeforeEach
    fun setUp() {
        buf = ByteArrayOutputStream()
        System.setOut(PrintStream(buf))
        System.setErr(PrintStream(buf))
        @Suppress("DEPRECATION")
        CommandRunner.globalMiddlewares = emptyList()
        @Suppress("DEPRECATION")
        CommandRunner.groupMiddlewares = emptyMap()
        @Suppress("DEPRECATION")
        CommandRunner.commandMiddlewares = emptyMap()
    }

    @AfterEach
    fun tearDown() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    private fun output() = buf.toString()

    private fun recorder(log: MutableList<String>, label: String, prio: Int = 0) = object : Middleware {
        override val priority = prio
        override val name = label
        override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
            log.add("$label:enter")
            next()
            log.add("$label:exit")
        }
    }

    @Test
    fun `group middleware runs for its group and not for another`() {
        val log = mutableListOf<String>()
        val app = cli(name = "test", version = "1.0.0") {
            group(name = "file") {
                use(recorder(log, "audit"))
                command(name = "create") { action { log.add("file-create") } }
            }
            group(name = "dir") {
                command(name = "create") { action { log.add("dir-create") } }
            }
        }

        assertEquals(0, app.runForTest(arrayOf("file", "create")))
        assertEquals(listOf("audit:enter", "file-create", "audit:exit"), log)

        log.clear()
        assertEquals(0, app.runForTest(arrayOf("dir", "create")))
        assertEquals(listOf("dir-create"), log)
    }

    @Test
    fun `command middleware runs only for its own command`() {
        val log = mutableListOf<String>()
        val app = cli(name = "test", version = "1.0.0") {
            group(name = "file") {
                command(name = "delete") {
                    use(recorder(log, "confirm"))
                    action { log.add("delete") }
                }
                command(name = "create") { action { log.add("create") } }
            }
        }

        assertEquals(0, app.runForTest(arrayOf("file", "delete")))
        assertEquals(listOf("confirm:enter", "delete", "confirm:exit"), log)

        log.clear()
        assertEquals(0, app.runForTest(arrayOf("file", "create")))
        assertEquals(listOf("create"), log)
    }

    @Test
    fun `command middleware does not leak to the same command name in another group`() {
        val log = mutableListOf<String>()
        val app = cli(name = "test", version = "1.0.0") {
            group(name = "plugin") {
                command(name = "list") {
                    use(recorder(log, "plugin-only"))
                    action { log.add("plugin-list") }
                }
            }
            group(name = "stats") {
                command(name = "list") { action { log.add("stats-list") } }
            }
        }

        assertEquals(0, app.runForTest(arrayOf("stats", "list")))
        assertEquals(listOf("stats-list"), log)
    }

    @Test
    fun `lower priority wraps higher priority across scopes`() {
        val log = mutableListOf<String>()
        val app = cli(name = "test", version = "1.0.0") {
            use(recorder(log, "stats", -1000))
            use(recorder(log, "log", 100))
            group(name = "file") {
                use(recorder(log, "audit", 0))
                command(name = "delete") {
                    use(recorder(log, "confirm", 10))
                    action { log.add("action") }
                }
            }
        }

        assertEquals(0, app.runForTest(arrayOf("file", "delete")))
        assertEquals(
            listOf(
                "stats:enter", "audit:enter", "confirm:enter", "log:enter",
                "action",
                "log:exit", "confirm:exit", "audit:exit", "stats:exit",
            ),
            log,
        )
    }

    @Test
    fun `command scoped middleware with lowest priority becomes outermost`() {
        val log = mutableListOf<String>()
        val app = cli(name = "test", version = "1.0.0") {
            use(recorder(log, "stats", -1000))
            group(name = "file") {
                command(name = "delete") {
                    use(recorder(log, "trace", -9999))
                    action { log.add("action") }
                }
            }
        }

        assertEquals(0, app.runForTest(arrayOf("file", "delete")))
        assertEquals(
            listOf("trace:enter", "stats:enter", "action", "stats:exit", "trace:exit"),
            log,
        )
    }

    @Test
    fun `middleware list is auto-registered and reports every scope`() {
        val log = mutableListOf<String>()
        val app = cli(name = "test", version = "1.0.0") {
            use(recorder(log, "StatsMiddleware", -1000))
            group(name = "file") {
                use(recorder(log, "AuditMiddleware", 0))
                command(name = "delete") {
                    use(recorder(log, "ConfirmMiddleware", 10))
                    action { }
                }
            }
        }

        assertEquals(0, app.runForTest(arrayOf("middleware", "list")))

        val out = output()
        assertTrue(out.contains("StatsMiddleware"), out)
        assertTrue(out.contains("GLOBAL"), out)
        assertTrue(out.contains("AuditMiddleware"), out)
        assertTrue(out.contains("GROUP"), out)
        assertTrue(out.contains("ConfirmMiddleware"), out)
        assertTrue(out.contains("COMMAND"), out)
        assertTrue(out.contains("file delete"), out)
    }

    @Test
    fun `middleware list command preview matches real execution order`() {
        val log = mutableListOf<String>()
        val app = cli(name = "test", version = "1.0.0") {
            use(recorder(log, "StatsMiddleware", -1000))
            use(recorder(log, "LogMiddleware", 100))
            group(name = "file") {
                use(recorder(log, "AuditMiddleware", 0))
                command(name = "delete") {
                    use(recorder(log, "ConfirmMiddleware", 10))
                    action { log.add("action") }
                }
            }
        }

        assertEquals(0, app.runForTest(arrayOf("middleware", "list", "--command", "file delete")))
        val previewed = output().lines()
            .mapNotNull { line -> Regex("""^\s*\d+\.\s+(\S+)""").find(line)?.groupValues?.get(1) }

        log.clear()
        app.runForTest(arrayOf("file", "delete"))
        val executed = log.filter { it.endsWith(":enter") }.map { it.removeSuffix(":enter") }

        assertEquals(executed, previewed, "preview must match real order")
    }

    @Test
    fun `middleware list rejects a malformed command target`() {
        val app = cli(name = "test", version = "1.0.0") {
            group(name = "file") { command(name = "delete") { action { } } }
        }

        assertEquals(0, app.runForTest(arrayOf("middleware", "list", "--command", "file")))
        assertTrue(output().contains("Invalid --command value"), output())
    }

    @Test
    fun `a user defined middleware group is not overwritten`() {
        val app = cli(name = "test", version = "1.0.0") {
            group(name = "middleware") {
                command(name = "list") { action { println("custom") } }
            }
        }

        assertEquals(0, app.runForTest(arrayOf("middleware", "list")))
        assertTrue(output().contains("custom"), output())
        assertEquals(1, app.groups.count { it.name == "middleware" })
    }
}
