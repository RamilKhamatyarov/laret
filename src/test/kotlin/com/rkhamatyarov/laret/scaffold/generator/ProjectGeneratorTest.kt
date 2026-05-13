package com.rkhamatyarov.laret.scaffold.generator

import com.rkhamatyarov.laret.scaffold.model.Module
import com.rkhamatyarov.laret.scaffold.model.ScaffoldConfig
import com.rkhamatyarov.laret.scaffold.model.ShellTarget
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectGeneratorTest {
    private val generator = ProjectGenerator()

    private fun cfg(graalvm: Boolean = false) = ScaffoldConfig(
        projectName = "my-cli",
        packageName = "com.example.mycli",
        appName = "my-cli",
        laretVersion = "0.2.0",
        modules = Module.entries.toSet(),
        shellTests = setOf(ShellTarget.BASH),
        graalvm = graalvm,
    )

    @Test
    fun `generate writes expected core files`(@TempDir tmp: Path) = runTest {
        val result = generator.generate(cfg(), tmp)
        assertTrue(result.failures.isEmpty(), "Unexpected failures: ${result.failures}")
        assertTrue(Files.exists(tmp.resolve("build.gradle.kts")))
        assertTrue(Files.exists(tmp.resolve("settings.gradle.kts")))
        assertTrue(Files.exists(tmp.resolve(".gitignore")))
        assertTrue(Files.exists(tmp.resolve("src/main/kotlin/com/example/mycli/Main.kt")))
        assertTrue(
            Files.exists(tmp.resolve("src/main/kotlin/com/example/mycli/commands/HelloCommand.kt")),
        )
        assertTrue(Files.exists(tmp.resolve("tests/test-precedence.bash")))
    }

    @Test
    fun `generated build_gradle_kts contains substituted group and artifact`(@TempDir tmp: Path) = runTest {
        generator.generate(cfg(), tmp)
        val build = Files.readString(tmp.resolve("build.gradle.kts"))
        assertTrue(build.contains("""group = "com.example""""))
        assertTrue(build.contains("""com.rkhamatyarov:laret:0.2.0"""))
    }

    @Test
    fun `graalvm false omits native-image plugin`(@TempDir tmp: Path) = runTest {
        generator.generate(cfg(graalvm = false), tmp)
        val build = Files.readString(tmp.resolve("build.gradle.kts"))
        assertTrue(!build.contains("graalvmNative"))
        assertTrue(!build.contains("org.graalvm.buildtools.native"))
    }

    @Test
    fun `graalvm true includes native-image plugin and config`(@TempDir tmp: Path) = runTest {
        generator.generate(cfg(graalvm = true), tmp)
        val build = Files.readString(tmp.resolve("build.gradle.kts"))
        assertTrue(build.contains("org.graalvm.buildtools.native"))
        assertTrue(build.contains("graalvmNative"))
        assertTrue(build.contains("""imageName.set("my-cli")"""))
    }

    @Test
    fun `Main_kt is generated with correct package declaration`(@TempDir tmp: Path) = runTest {
        generator.generate(cfg(), tmp)
        val main = Files.readString(tmp.resolve("src/main/kotlin/com/example/mycli/Main.kt"))
        assertTrue(main.contains("package com.example.mycli"))
        assertTrue(main.contains("""name = "my-cli""""))
    }

    @Test
    fun `HelloCommand_kt uses uppercase env var prefix`(@TempDir tmp: Path) = runTest {
        generator.generate(cfg(), tmp)
        val hello = Files.readString(tmp.resolve("src/main/kotlin/com/example/mycli/commands/HelloCommand.kt"))
        assertTrue(hello.contains("MY_CLI_GREETING_NAME"))
        assertTrue(hello.contains("\$resolved"))
    }

    @Test
    fun `settings_gradle_kts has correct rootProject name`(@TempDir tmp: Path) = runTest {
        generator.generate(cfg(), tmp)
        val settings = Files.readString(tmp.resolve("settings.gradle.kts"))
        assertEquals("""rootProject.name = "my-cli"""", settings.trim())
    }

    @Test
    fun `result lists every written path`(@TempDir tmp: Path) = runTest {
        val result = generator.generate(cfg(), tmp)
        assertEquals(6, result.written.size)
    }
}
