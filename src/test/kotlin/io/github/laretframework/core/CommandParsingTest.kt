package io.github.laretframework.core

import io.github.laretframework.dsl.CommandBuilder
import io.github.laretframework.dsl.cli
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class CommandParsingTest {

    @Test
    fun anOptionValueBeforeTheArgumentIsNotTakenAsTheArgument() {
        val result = run("--content", "hello", "notes.txt", configure = fileCommand)

        assertThat(result.out).contains("path=notes.txt content=hello")
    }

    @Test
    fun anOptionValueDoesNotSatisfyAMissingRequiredArgument() {
        val result = run("--content", "hello", configure = fileCommand)

        assertThat(result.exit).isEqualTo(1)
        assertThat(result.out).contains("Required argument 'path' not provided")
    }

    @Test
    fun commandHelpIsShownWithoutRunningTheCommand() {
        val result = run("--help", configure = fileCommand)

        assertThat(result.exit).isZero()
        assertThat(result.out).doesNotContain("path=")
        assertThat(result.err).contains("Command: c").contains("--content")
    }

    @Test
    fun aCommandThatDefinesShortHKeepsIt() {
        val result = run("x", "-h", "example.org") {
            argument("path", "Path")
            option("h", "host", "Host")
        }

        assertThat(result.exit).isZero()
        assertThat(result.out).contains("path=x")
    }

    private data class Run(val exit: Int, val out: String, val err: String)

    private fun run(vararg args: String, configure: CommandBuilder.() -> Unit): Run {
        val app = cli(name = "parse", version = "0", description = "fixture") {
            group(name = "g") {
                command(name = "c") {
                    configure()
                    action { ctx -> println("path=${ctx.argument("path")} content=${ctx.option("content")}") }
                }
            }
        }
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val (originalOut, originalErr) = System.out to System.err
        val exit = try {
            System.setOut(PrintStream(out, true, Charsets.UTF_8))
            System.setErr(PrintStream(err, true, Charsets.UTF_8))
            app.runForTest(arrayOf("g", "c", *args))
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
        return Run(exit, out.toString(Charsets.UTF_8), err.toString(Charsets.UTF_8))
    }

    private val fileCommand: CommandBuilder.() -> Unit = {
        argument("path", "Path")
        option("c", "content", "Content")
    }
}
