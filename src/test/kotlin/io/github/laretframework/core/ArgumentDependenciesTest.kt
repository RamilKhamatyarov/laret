package io.github.laretframework.core

import io.github.laretframework.dsl.CommandBuilder
import io.github.laretframework.dsl.cli
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.writeText

class ArgumentDependenciesTest {

    private data class Run(val exit: Int, val out: String, val err: String, val ran: Boolean)

    @AfterEach
    fun resetLocale() = Localization.setLocale(Locale.ENGLISH)

    @Test
    fun requiresFailsWhenTheTriggerIsSetWithoutItsDependencies() {
        val result = run(tls(), "--tls", "--cert", "c.pem")

        assertThat(result.exit).isEqualTo(1)
        assertThat(result.ran).isFalse()
        assertThat(result.out).contains("Invalid usage: --tls requires --key")
    }

    @Test
    fun requiresPassesWhenEveryDependencyHasAValue() {
        assertThat(run(tls(), "--tls", "--cert", "c.pem", "--key", "k.pem").ran).isTrue()
    }

    @Test
    fun requiresIsIgnoredWhenTheTriggerIsNotSet() {
        assertThat(run(tls()).ran).isTrue()
    }

    @Test
    fun aDependencyCanBeSatisfiedFromTheConfigFile(@TempDir dir: Path) {
        val config = dir.resolve("laret.yml").also { it.writeText("g:\n  cert: c.pem\n  key: k.pem\n") }
        val app = tls().init(config.toString())

        assertThat(run(app, "--tls").ran).isTrue()
    }

    @Test
    fun anOptionAtItsDefaultNeverTriggersARule() {
        val app = app {
            option("f", "format", "Output format", default = "plain")
            option("o", "output", "Output file")
            requires("format", "output")
        }

        assertThat(run(app).ran).isTrue()
        assertThat(run(app, "--format", "plain").ran).isTrue()
        assertThat(run(app, "--format", "json").exit).isEqualTo(1)
    }

    private fun source() = app {
        argument("file", "Input file", required = false, optional = true)
        option("u", "url", "Input URL")
        option("s", "stdin", "Read stdin", takesValue = false)
        oneOf("file", "url", "stdin")
    }

    @Test
    fun oneOfFailsWhenNoneIsGiven() {
        val result = run(source())

        assertThat(result.ran).isFalse()
        assertThat(result.out).contains("exactly one of <file>, --url, --stdin is required")
    }

    @Test
    fun oneOfFailsWhenMoreThanOneIsGiven() {
        val result = run(source(), "in.txt", "--stdin")

        assertThat(result.ran).isFalse()
        assertThat(result.out).contains("only one of <file>, --url, --stdin may be given, got <file>, --stdin")
    }

    @Test
    fun oneOfPassesWithExactlyOneIncludingAPositionalArgument() {
        assertThat(run(source(), "in.txt").ran).isTrue()
        assertThat(run(source(), "--url", "https://example.org").ran).isTrue()
    }

    private fun credentials() = app {
        option("u", "user", "User")
        option("p", "password", "Password")
        allOrNone("user", "password")
    }

    @Test
    fun allOrNonePassesWithAllOrNone() {
        assertThat(run(credentials()).ran).isTrue()
        assertThat(run(credentials(), "--user", "ann", "--password", "secret").ran).isTrue()
    }

    @Test
    fun allOrNoneFailsWithOnlySomeAndNamesTheMissing() {
        val result = run(credentials(), "--user", "ann")

        assertThat(result.ran).isFalse()
        assertThat(result.out).contains("--user, --password must be given together; missing --password")
    }

    private fun remote() = app {
        option("m", "mode", "Mode", default = "local")
        option("h", "host", "Host")
        option("p", "port", "Port")
        requiredWhen("mode", equals = "remote", "host", "port")
    }

    @Test
    fun requiredWhenAppliesOnlyForTheMatchingValue() {
        assertThat(run(remote()).ran).isTrue()
        assertThat(run(remote(), "--mode", "remote", "--host", "h", "--port", "1").ran).isTrue()

        val result = run(remote(), "--mode", "remote", "--host", "h")
        assertThat(result.ran).isFalse()
        assertThat(result.out).contains("--port is required when --mode is remote")
    }

    @Test
    fun everyBrokenRuleIsReportedTogether() {
        val app = app {
            option("t", "tls", "Enable TLS", takesValue = false)
            option("c", "cert", "Certificate")
            option("u", "user", "User")
            option("p", "password", "Password")
            requires("tls", "cert")
            allOrNone("user", "password")
        }

        val result = run(app, "--tls", "--user", "ann")

        assertThat(result.out).contains("--tls requires --cert").contains("missing --password")
    }

    @Test
    fun helpListsTheConstraints() {
        val result = run(remote(), "--help")

        assertThat(result.err)
            .contains("Constraints:")
            .contains("--host, --port required when --mode is remote")
    }

    @Test
    fun messagesAreLocalised() {
        Localization.setLocale("es")

        val result = run(tls(), "--tls")

        assertThat(result.out).contains("Uso invalido: --tls requiere --cert, --key")
    }

    @Test
    fun aRuleNamingAnUnknownNameFailsAtBuild() {
        assertThatThrownBy {
            app {
                option("t", "tls", "Enable TLS", takesValue = false)
                requires("tls", "certificate")
            }
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("unknown names: [certificate]")
    }

    @Test
    fun aNameThatIsBothArgumentAndOptionFailsAtBuild() {
        assertThatThrownBy {
            app {
                argument("file", "File", required = false, optional = true)
                option("f", "file", "File")
                option("s", "stdin", "Stdin", takesValue = false)
                oneOf("file", "stdin")
            }
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("both an argument and an option")
    }

    @Test
    fun aGroupOfOneIsRejected() {
        assertThatThrownBy {
            app {
                option("a", "alpha", "A")
                oneOf("alpha")
            }
        }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun app(configure: CommandBuilder.() -> Unit) = cli(name = "deps", version = "0", description = "fixture") {
        group(name = "g") {
            command(name = "c") {
                configure()
                action { println("ACTION RAN") }
            }
        }
    }

    private fun run(app: CliApp, vararg args: String): Run {
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
        val stdout = out.toString(Charsets.UTF_8)
        return Run(exit, stdout, err.toString(Charsets.UTF_8), "ACTION RAN" in stdout)
    }

    private fun tls() = app {
        option("t", "tls", "Enable TLS", takesValue = false)
        option("c", "cert", "Certificate")
        option("k", "key", "Private key")
        requires("tls", "cert", "key")
    }
}
