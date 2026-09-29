package io.github.laretframework.core

import io.github.laretframework.dsl.cli
import io.github.laretframework.plugin.model.LaretPlugin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class CliAppPublicApiTest {

    private fun app() = cli(name = "apitest", version = "0", description = "public api fixture") {
        group(name = "g") { command(name = "c") { action { } } }
    }

    private fun writeConfig(dir: Path, name: String, format: String, level: String): Path {
        val file = dir.resolve("laret.yml")
        file.writeText(
            """
            app:
              name: $name
              description: fixture app
            output:
              format: $format
            logging:
              level: $level
            """.trimIndent() + "\n",
        )
        return file
    }

    private class NamedPlugin(override val name: String) : LaretPlugin

    @Test
    fun exposesTheLoadedAppOutputAndLoggingConfig(@TempDir dir: Path) {
        val app = app().init(writeConfig(dir, "fixture", "json", "DEBUG").toString())

        assertThat(app.getAppMetadata().name).isEqualTo("fixture")
        assertThat(app.getAppMetadata().description).isEqualTo("fixture app")
        assertThat(app.getOutputConfig().format).isEqualTo("json")
        assertThat(app.getLoggingConfig().level).isEqualTo("DEBUG")
    }

    @Test
    fun saveConfigWritesAFileThatLoadsBackToTheSameValues(@TempDir dir: Path) {
        val original = app().init(writeConfig(dir, "fixture", "json", "DEBUG").toString())
        val saved = dir.resolve("nested/saved.yml")

        original.saveConfig(saved.toString())

        assertThat(Files.exists(saved)).isTrue()
        val reloaded = app().init(saved.toString())
        assertThat(reloaded.getAppMetadata().name).isEqualTo("fixture")
        assertThat(reloaded.getOutputConfig().format).isEqualTo("json")
        assertThat(reloaded.getLoggingConfig().level).isEqualTo("DEBUG")
    }

    @Test
    fun reloadConfigPicksUpChangesToTheFile(@TempDir dir: Path) {
        val app = app().init(writeConfig(dir, "before", "json", "DEBUG").toString())
        writeConfig(dir, "after", "yaml", "WARN")

        val returned = app.reloadConfig()

        assertThat(returned).isSameAs(app)
        assertThat(app.getAppMetadata().name).isEqualTo("after")
        assertThat(app.getOutputConfig().format).isEqualTo("yaml")
        assertThat(app.getLoggingConfig().level).isEqualTo("WARN")
    }

    @Test
    fun anAppStartsWithoutPlugins() {
        val app = app()

        assertThat(app.hasPlugins()).isFalse()
        assertThat(app.findPlugin("anything")).isNull()
    }

    @Test
    fun registerPluginAddsOneAndReturnsTheAppForChaining() {
        val app = app()
        val plugin = NamedPlugin("alpha")

        val returned = app.registerPlugin(plugin)

        assertThat(returned).isSameAs(app)
        assertThat(app.hasPlugins()).isTrue()
        assertThat(app.findPlugin("alpha")).isSameAs(plugin)
    }

    @Test
    fun registerPluginsAddsEveryOneGiven() {
        val app = app()
        val alpha = NamedPlugin("alpha")
        val beta = NamedPlugin("beta")

        val returned = app.registerPlugins(alpha, beta)

        assertThat(returned).isSameAs(app)
        assertThat(app.findPlugin("alpha")).isSameAs(alpha)
        assertThat(app.findPlugin("beta")).isSameAs(beta)
        assertThat(app.findPlugin("gamma")).isNull()
    }

    @Test
    fun removingASidecarPluginThatIsNotInstalledFails() {
        val result = app().removeSidecarPlugin("missing")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("missing")
    }
}
