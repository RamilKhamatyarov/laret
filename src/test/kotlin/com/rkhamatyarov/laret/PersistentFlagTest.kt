package com.rkhamatyarov.laret

import com.rkhamatyarov.laret.config.model.AppConfig
import com.rkhamatyarov.laret.config.model.AppMetadata
import com.rkhamatyarov.laret.config.model.LoggingConfig
import com.rkhamatyarov.laret.config.model.OutputConfig
import com.rkhamatyarov.laret.config.model.PluginConfig
import com.rkhamatyarov.laret.core.FlagPersistence
import com.rkhamatyarov.laret.dsl.cli
import com.rkhamatyarov.laret.model.Option
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PersistentFlagTest {
    private val originalOut = System.out
    private val originalErr = System.err
    private lateinit var buf: ByteArrayOutputStream

    @BeforeEach
    fun setup() {
        buf = ByteArrayOutputStream()
        val ps = PrintStream(buf)
        System.setOut(ps)
        System.setErr(ps)
    }

    @AfterEach
    fun cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    private fun output() = buf.toString()

    private fun clear() = buf.reset()

    private fun configWithFlags(flags: Map<String, Any>): AppConfig = AppConfig(
        app = AppMetadata(),
        output = OutputConfig(),
        plugins = PluginConfig(),
        logging = LoggingConfig(),
        flags = flags
    )

    @Nested
    inner class KeyGenerationTests {
        @Test
        fun `buildKeys with group produces three keys in priority order`() {
            val keys = FlagPersistence.buildKeys("file", "create", "force")
            assertEquals(listOf("file.create.force", "create.force", "global.force"), keys)
        }

        @Test
        fun `buildKeys with null group produces two keys`() {
            val keys = FlagPersistence.buildKeys(null, "create", "force")
            assertEquals(listOf("create.force", "global.force"), keys)
        }

        @Test
        fun `buildKeys with blank group produces two keys`() {
            val keys = FlagPersistence.buildKeys("", "create", "force")
            assertEquals(listOf("create.force", "global.force"), keys)
        }

        @Test
        fun `buildKeys includes global key last always`() {
            val keys = FlagPersistence.buildKeys("dir", "list", "format")
            assertEquals("global.format", keys.last())
        }
    }

    @Nested
    inner class MapTraversalTests {
        @Test
        fun `getValueFromMap resolves nested three-level key`() {
            val map = mapOf("file" to mapOf("create" to mapOf("force" to true)))
            assertEquals(true, FlagPersistence.getValueFromMap(map, "file.create.force"))
        }

        @Test
        fun `getValueFromMap resolves two-level key`() {
            val map = mapOf("create" to mapOf("force" to "yes"))
            assertEquals("yes", FlagPersistence.getValueFromMap(map, "create.force"))
        }

        @Test
        fun `getValueFromMap returns null for missing intermediate key`() {
            val map = mapOf("file" to mapOf("delete" to mapOf("force" to true)))
            assertNull(FlagPersistence.getValueFromMap(map, "file.create.force"))
        }

        @Test
        fun `getValueFromMap returns null for missing leaf key`() {
            val map = mapOf("file" to mapOf("create" to mapOf("content" to "x")))
            assertNull(FlagPersistence.getValueFromMap(map, "file.create.force"))
        }

        @Test
        fun `getValueFromMap returns null for empty map`() {
            assertNull(FlagPersistence.getValueFromMap(emptyMap(), "any.key"))
        }
    }

    @Nested
    inner class ConversionTests {
        @Test
        fun `boolean true for toggle flag becomes string true`() {
            assertEquals("true", FlagPersistence.convertToString(true, takesValue = false))
        }

        @Test
        fun `boolean false for toggle flag becomes string false`() {
            assertEquals("false", FlagPersistence.convertToString(false, takesValue = false))
        }

        @Test
        fun `string True normalised to lowercase true for toggle`() {
            assertEquals("true", FlagPersistence.convertToString("True", takesValue = false))
        }

        @Test
        fun `string FALSE normalised to lowercase false for toggle`() {
            assertEquals("false", FlagPersistence.convertToString("FALSE", takesValue = false))
        }

        @Test
        fun `string value with takesValue true is returned as-is`() {
            assertEquals("json", FlagPersistence.convertToString("json", takesValue = true))
        }

        @Test
        fun `integer value with takesValue true becomes toString`() {
            assertEquals("42", FlagPersistence.convertToString(42, takesValue = true))
        }
    }

    @Nested
    inner class ResolveFlagTests {
        private val persistOpt = Option("f", "format", "Format", "plain", true, persistent = true)
        private val nonPersistOpt = Option("l", "long", "Long", "", false, persistent = false)
        private val boolOpt = Option("f", "force", "Force", "", false, persistent = true)

        @Test
        fun `returns null when config is null`() {
            assertNull(FlagPersistence.resolveFlag(persistOpt, "dir", "list", null))
        }

        @Test
        fun `returns null when flags map is null`() {
            val config = configWithFlags(emptyMap<String, Any>()).copy(flags = null)
            assertNull(FlagPersistence.resolveFlag(persistOpt, "dir", "list", config))
        }

        @Test
        fun `group-command level key wins over command level`() {
            val flags =
                mapOf(
                    "dir" to mapOf("list" to mapOf("format" to "yaml")),
                    "global" to mapOf("format" to "toml")
                )
            assertEquals("yaml", FlagPersistence.resolveFlag(persistOpt, "dir", "list", configWithFlags(flags)))
        }

        @Test
        fun `command level key wins over global`() {
            val flags =
                mapOf(
                    "list" to mapOf("format" to "json"),
                    "global" to mapOf("format" to "toml")
                )
            assertEquals("json", FlagPersistence.resolveFlag(persistOpt, "dir", "list", configWithFlags(flags)))
        }

        @Test
        fun `global key is used when no specific key matches`() {
            val flags = mapOf("global" to mapOf("format" to "toml"))
            assertEquals("toml", FlagPersistence.resolveFlag(persistOpt, "dir", "list", configWithFlags(flags)))
        }

        @Test
        fun `returns null when no key matches at any level`() {
            val flags = mapOf("file" to mapOf("create" to mapOf("force" to true)))
            assertNull(FlagPersistence.resolveFlag(persistOpt, "dir", "list", configWithFlags(flags)))
        }

        @Test
        fun `non-persistent option returns value if key exists`() {
            val flags = mapOf("global" to mapOf("long" to "true"))
            assertEquals("true", FlagPersistence.resolveFlag(nonPersistOpt, "dir", "list", configWithFlags(flags)))
        }

        @Test
        fun `boolean config value for toggle flag converts to string true`() {
            val flags = mapOf("file" to mapOf("create" to mapOf("force" to true)))
            assertEquals("true", FlagPersistence.resolveFlag(boolOpt, "file", "create", configWithFlags(flags)))
        }

        @Test
        fun `global format applies to different groups`() {
            val flags = mapOf("global" to mapOf("format" to "toml"))
            val cfg = configWithFlags(flags)
            assertEquals("toml", FlagPersistence.resolveFlag(persistOpt, "dir", "list", cfg))
            assertEquals("toml", FlagPersistence.resolveFlag(persistOpt, "file", "read", cfg))
        }
    }

    @Nested
    inner class RealConfigFileTests {
        @Test
        fun `force flag from config allows overwrite without CLI flag`(@TempDir tmp: File) {
            val yml = "flags:\n  file:\n    create:\n      force: true\n"
            val configFile = File(tmp, "laret.yml")
            configFile.writeText(yml)
            val testFile = File(tmp, "target.txt")
            testFile.writeText("original")

            val app =
                cli(name = "laret", version = "1.0.0", description = "t") {
                    group(name = "file", description = "File ops") {
                        command(name = "create", description = "Create") {
                            argument("path", "Path")
                            option("c", "content", "Content", "", true)
                            option("f", "force", "Force", "", false, persistent = true)
                            action { ctx ->
                                val path = ctx.argument("path")
                                val force = ctx.optionBool("force")
                                val file = File(path)
                                if (file.exists() && !force) {
                                    println("File already exists: " + path)
                                    return@action
                                }
                                file.writeText(ctx.option("content"))
                                println("File created: " + path)
                            }
                        }
                    }
                }

            app.runForTest(
                arrayOf(
                    "--config",
                    configFile.absolutePath,
                    "file",
                    "create",
                    testFile.absolutePath,
                    "-c",
                    "overwritten"
                )
            )

            val out = output()
            assertTrue(out.contains("File created"), "Expected 'File created', got: " + out)
            assertEquals("overwritten", testFile.readText())
        }

        @Test
        fun `global format from config applied when CLI flag absent`(@TempDir tmp: File) {
            val opt = Option("f", "format", "Format", "plain", true, persistent = true)
            val config = configWithFlags(mapOf("global" to mapOf("format" to "yaml")))
            assertEquals("yaml", FlagPersistence.resolveFlag(opt, "dir", "list", config))
        }
    }
}
