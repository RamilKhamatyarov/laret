package io.github.laretframework.model

import io.github.laretframework.completion.CompletionEngine
import io.github.laretframework.completion.generators.BashCompletionGenerator
import io.github.laretframework.dsl.cli
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class HiddenGroupTest {

    private fun app() = cli(name = "hidetest", version = "0", description = "hidden group fixture") {
        group(name = "shown", description = "A visible group") {
            command(name = "run", description = "Visible command") { action { } }
            command(name = "secret", description = "Hidden command") {
                hidden()
                action { }
            }
        }
        group(name = "internal", description = "A hidden group") {
            hidden()
            command(name = "probe", description = "Reachable but unlisted") {
                action { println("probed") }
            }
        }
    }

    private fun capture(block: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        val original = System.out
        return try {
            System.setOut(PrintStream(buffer, true, Charsets.UTF_8))
            block()
            buffer.toString(Charsets.UTF_8)
        } finally {
            System.setOut(original)
        }
    }

    @Test
    fun visibleDropsHiddenGroupsAndHiddenCommands() {
        val groups = app().groups.visible()

        assertThat(groups.map { it.name }).contains("shown").doesNotContain("internal")
        assertThat(groups.first { it.name == "shown" }.commands.map { it.name }).containsExactly("run")
    }

    @Test
    fun visibleKeepsEverythingWhenHiddenIsRequested() {
        val groups = app().groups.visible(includeHidden = true)

        assertThat(groups.map { it.name }).contains("shown", "internal")
        assertThat(groups.first { it.name == "shown" }.commands.map { it.name })
            .containsExactly("run", "secret")
    }

    @Test
    fun helpListsNeitherHiddenGroupsNorHiddenCommands() {
        val help = capture { app().runForTest(arrayOf("--help")) }

        assertThat(help).contains("shown")
        assertThat(help).contains("run")
        assertThat(help).doesNotContain("internal")
        assertThat(help).doesNotContain("secret")
    }

    @Test
    fun completionDoesNotOfferHiddenGroups() {
        val candidates = CompletionEngine(app()).complete(listOf(""))

        assertThat(candidates).contains("shown")
        assertThat(candidates).doesNotContain("internal")
    }

    @Test
    fun generatedCompletionScriptsOmitHiddenGroups() {
        val script = BashCompletionGenerator().generate(app())

        assertThat(script).contains("shown")
        assertThat(script).doesNotContain("internal")
    }

    @Test
    fun aHiddenGroupIsStillInvocable() {
        val output = capture { app().runForTest(arrayOf("internal", "probe")) }

        assertThat(output).contains("probed")
    }
}
