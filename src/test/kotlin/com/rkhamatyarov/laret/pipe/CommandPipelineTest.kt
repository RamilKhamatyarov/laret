package com.rkhamatyarov.laret.pipe

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * JUnit 5 tests for [CommandPipeline].
 *
 * Categories:
 *  * [SplitStagesTests]   — pure input splitting, no side effects.
 *  * [SubstituteDashTests]— `-` token replacement.
 *  * [ExecuteTests]       — end-to-end execution with capture apps.
 *  * [EdgeCaseTests]      — empty/invalid input.
 */
class CommandPipelineTest {

    private val originalOut = System.out
    private val originalErr = System.err
    private val originalIn = System.`in`
    private lateinit var buf: ByteArrayOutputStream

    @BeforeEach
    fun setup() {
        buf = ByteArrayOutputStream()
        val ps = PrintStream(buf, true, Charsets.UTF_8)
        System.setOut(ps)
        System.setErr(ps)
    }

    @AfterEach
    fun cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setIn(originalIn)
    }

    private fun captured(): String = buf.toString(Charsets.UTF_8)
    private var pipeline = CommandPipeline(cli("t") { group("g") { command("c") { action {} } } })

    @Test
    fun `single stage without separator returns one stage`() {
        val stages = pipeline.splitStages(arrayOf("dir", "list", "/tmp"))
        assertEquals(1, stages.size)
        assertTrue(stages[0].contentEquals(arrayOf("dir", "list", "/tmp")))
    }

    @Test
    fun `two stages separated by --- produce two arrays`() {
        val stages = pipeline.splitStages(
            arrayOf("dir", "list", "/tmp", "---", "file", "create", "-"),
        )
        assertEquals(2, stages.size)
        assertTrue(stages[0].contentEquals(arrayOf("dir", "list", "/tmp")))
        assertTrue(stages[1].contentEquals(arrayOf("file", "create", "-")))
    }

    @Test
    fun `three stages split correctly`() {
        val stages = pipeline.splitStages(
            arrayOf("a", "1", "---", "b", "2", "---", "c", "3"),
        )
        assertEquals(3, stages.size)
        assertTrue(stages[2].contentEquals(arrayOf("c", "3")))
    }

    @Test
    fun `consecutive --- separators do not emit empty stages`() {
        val stages = pipeline.splitStages(
            arrayOf("a", "---", "---", "b"),
        )
        assertEquals(2, stages.size)
    }

    @Test
    fun `custom separator is respected`() {
        val stages = pipeline.splitStages(arrayOf("a", "|", "b"), separator = "|")
        assertEquals(2, stages.size)
    }

    @Test
    fun `empty input returns empty list`() {
        assertEquals(0, pipeline.splitStages(emptyArray()).size)
    }

    @Test
    fun `only separators return empty list`() {
        assertEquals(0, pipeline.splitStages(arrayOf("---", "---")).size)
    }

    @Test
    fun `single dash token is replaced with carry`() {
        pipeline = CommandPipeline(cli("t") { group("g") { command("c") { action {} } } })
        val result = pipeline.substituteDash(arrayOf("file", "create", "-"), "hello")
        assertTrue(result.contentEquals(arrayOf("file", "create", "hello")))
    }

    @Test
    fun `multiple dashes all get replaced`() {
        pipeline = CommandPipeline(cli("t") { group("g") { command("c") { action {} } } })
        val result = pipeline.substituteDash(arrayOf("cmd", "-", "-"), "X")
        assertTrue(result.contentEquals(arrayOf("cmd", "X", "X")))
    }

    @Test
    fun `dash inside longer token is NOT replaced`() {
        pipeline = CommandPipeline(cli("t") { group("g") { command("c") { action {} } } })
        val result = pipeline.substituteDash(arrayOf("cmd", "--force", "-x"), "Y")
        assertTrue(result.contentEquals(arrayOf("cmd", "--force", "-x")))
    }

    @Test
    fun `no dashes leave argv unchanged`() {
        pipeline = CommandPipeline(cli("t") { group("g") { command("c") { action {} } } })
        val result = pipeline.substituteDash(arrayOf("a", "b"), "carry")
        assertTrue(result.contentEquals(arrayOf("a", "b")))
    }

    /** Build an app with one group `echo` and one command `print` that just emits ctx.argument("text"). */
    private fun echoApp(): CliApp = cli("piper", version = "1.0") {
        group("echo") {
            command("print") {
                argument("text", required = false, optional = true, default = "")
                action { ctx -> print(ctx.argument("text")) }
            }
        }
        group("upper") {
            command("convert") {
                argument("text", required = false, optional = true, default = "")
                action { ctx ->
                    val input = if (ctx.argument("text").isNotEmpty()) {
                        ctx.argument("text")
                    } else {
                        CommandPipeline.captureStdin()
                    }
                    print(input.uppercase())
                }
            }
        }
    }

    @Test
    fun `single-stage pipeline runs and returns its output`() {
        val app = echoApp()
        val pipeline = CommandPipeline(app)
        val result = pipeline.execute(listOf(arrayOf("echo", "print", "hi")))
        assertEquals("hi", result)
        assertEquals("hi", captured())
    }

    @Test
    fun `dash token in second stage is replaced with first stage output`() {
        val app = echoApp()
        val pipeline = CommandPipeline(app)
        val result = pipeline.execute(
            listOf(
                arrayOf("echo", "print", "hello"),
                arrayOf("upper", "convert", "-"),
            ),
        )
        assertEquals("HELLO", result)
    }

    @Test
    fun `second stage without dash reads from stdin implicitly`() {
        val app = echoApp()
        val pipeline = CommandPipeline(app)
        val result = pipeline.execute(
            listOf(
                arrayOf("echo", "print", "world"),
                arrayOf("upper", "convert"),
            ),
        )
        assertEquals("WORLD", result)
    }

    @Test
    fun `three-stage pipeline chains output forward`() {
        val app = echoApp()
        val pipeline = CommandPipeline(app)
        val result = pipeline.execute(
            listOf(
                arrayOf("echo", "print", "abc"),
                arrayOf("upper", "convert", "-"),
                arrayOf("upper", "convert", "-"),
            ),
        )
        assertEquals("ABC", result)
    }

    @Test
    fun `real dir list piped into file create round-trips`(@TempDir tmp: File) {
        val app = cli("laret", version = "1.0") {
            group("dir") {
                command("list") {
                    argument("path")
                    action { ctx ->
                        val files = File(ctx.argument("path")).listFiles() ?: emptyArray()
                        files.sortedBy { it.name }.forEach { println(it.name) }
                    }
                }
            }
            group("file") {
                command("create") {
                    argument("path")
                    option("c", "content", default = "")
                    action { ctx ->
                        val content = ctx.option("content").ifEmpty {
                            CommandPipeline.captureStdin()
                        }
                        File(ctx.argument("path")).writeText(content)
                        System.err.println("wrote ${ctx.argument("path")}")
                    }
                }
            }
        }

        File(tmp, "a.txt").createNewFile()
        File(tmp, "b.txt").createNewFile()

        val out = File(tmp, "listing.txt")
        val pipeline = CommandPipeline(app)
        pipeline.execute(
            listOf(
                arrayOf("dir", "list", tmp.absolutePath),
                arrayOf("file", "create", out.absolutePath),
            ),
        )

        assertTrue(out.exists(), "target file should be created")
        val contents = out.readText()
        assertTrue(contents.contains("a.txt"), "pipeline should have fed listing into create: <$contents>")
        assertTrue(contents.contains("b.txt"))
    }

    @Test
    fun `execute with empty list throws IllegalArgumentException`() {
        val app = cli("t") { group("g") { command("c") { action {} } } }
        val pipeline = CommandPipeline(app)
        assertFailsWith<IllegalArgumentException> { pipeline.execute(emptyList()) }
    }

    @Test
    fun `captureStdin reads all bytes from the given stream`() {
        val stream = ByteArrayInputStream("hello stdin".toByteArray(Charsets.UTF_8))
        assertEquals("hello stdin", CommandPipeline.captureStdin(stream))
    }

    @Test
    fun `captureStdin of empty stream returns empty string`() {
        val stream = ByteArrayInputStream(ByteArray(0))
        assertEquals("", CommandPipeline.captureStdin(stream))
    }

    @Test
    fun `STAGE_SEPARATOR constant is three dashes`() {
        assertEquals("---", CommandPipeline.STAGE_SEPARATOR)
    }
}
