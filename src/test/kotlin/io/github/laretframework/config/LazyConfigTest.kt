package io.github.laretframework.config

import io.github.laretframework.config.registry.ConfigFileReader
import io.github.laretframework.config.registry.FileLayer
import io.github.laretframework.dsl.cli
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.writeText

class LazyConfigTest {

    private fun mapperInitialised(loader: ConfigLoader, name: String): Boolean {
        val field = ConfigLoader::class.java.getDeclaredField("$name\$delegate")
        field.isAccessible = true
        return (field.get(loader) as Lazy<*>).isInitialized()
    }

    @Test
    fun aNewConfigLoaderBuildsNoMapper() {
        val loader = ConfigLoader()

        assertThat(mapperInitialised(loader, "jsonMapper")).isFalse()
        assertThat(mapperInitialised(loader, "yamlMapper")).isFalse()
        assertThat(mapperInitialised(loader, "tomlMapper")).isFalse()
    }

    @Test
    fun readingYamlBuildsOnlyTheYamlMapper(@TempDir dir: Path) {
        val file = dir.resolve("laret.yml").also { it.writeText("app:\n  name: lazy\n") }
        val loader = ConfigLoader()

        val config = loader.loadFromFile(file.toFile())

        assertThat(config.app.name).isEqualTo("lazy")
        assertThat(mapperInitialised(loader, "yamlMapper")).isTrue()
        assertThat(mapperInitialised(loader, "jsonMapper")).isFalse()
        assertThat(mapperInitialised(loader, "tomlMapper")).isFalse()
    }

    @Test
    fun everyFormatStillLoadsAfterTheChange(@TempDir dir: Path) {
        val loader = ConfigLoader()
        val yaml = dir.resolve("a.yml").also { it.writeText("app:\n  name: from-yaml\n") }
        val toml = dir.resolve("a.toml").also { it.writeText("[app]\nname = \"from-toml\"\n") }
        val json = dir.resolve("a.json").also { it.writeText("""{"app": {"name": "from-json"}}""") }

        assertThat(loader.loadFromFile(yaml.toFile()).app.name).isEqualTo("from-yaml")
        assertThat(loader.loadFromFile(toml.toFile()).app.name).isEqualTo("from-toml")
        assertThat(loader.loadFromFile(json.toFile()).app.name).isEqualTo("from-json")
    }

    @Test
    fun aFileLayerTouchesNoFileUntilItIsAsked() {
        val probed = mutableListOf<File>()
        val layer = FileLayer(
            reader = ConfigFileReader { emptyMap() },
            exists = {
                probed += it
                false
            },
        )

        assertThat(probed).isEmpty()

        layer.get("anything")

        assertThat(probed).isNotEmpty()
    }

    @Test
    fun aFileLayerStillPicksTheSameFile(@TempDir dir: Path) {
        val chosen = dir.resolve(".laret.yml").also { it.writeText("app:\n  name: x\n") }.toFile()
        val layer = FileLayer(
            reader = ConfigFileReader { mapOf("greeting" to mapOf("name" to "from-file")) },
            workDir = dir.toFile(),
            homeDir = dir.resolve("no-home").toFile(),
        )

        assertThat(layer.get("greeting.name")).isEqualTo("from-file")
        assertThat(layer.selectedFile()).isEqualTo(chosen)
    }

    @Test
    fun runningACommandThatReadsNoConfigBuildsNoMapper() {
        val app = cli(name = "lazyapp", version = "0", description = "lazy config fixture") {
            group(name = "g") { command(name = "c") { action { println("ran") } } }
        }
        val original = System.out
        try {
            System.setOut(PrintStream(ByteArrayOutputStream(), true))
            app.runForTest(arrayOf("g", "c"))
        } finally {
            System.setOut(original)
        }

        val loaderField = app.javaClass.getDeclaredField("configLoader").apply { isAccessible = true }
        val loader = loaderField.get(app) as ConfigLoader
        assertThat(mapperInitialised(loader, "jsonMapper")).isFalse()
        assertThat(mapperInitialised(loader, "yamlMapper")).isFalse()
        assertThat(mapperInitialised(loader, "tomlMapper")).isFalse()
    }
}
