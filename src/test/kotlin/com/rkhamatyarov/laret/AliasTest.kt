package com.rkhamatyarov.laret

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.dsl.cli
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AliasTest {
    private lateinit var app: CliApp
    private val testDir = File("test_alias_tmp")
    private val originalOut = System.out
    private val originalErr = System.err
    private lateinit var buf: ByteArrayOutputStream

    @BeforeEach
    fun setup() {
        testDir.mkdirs()
        buf = ByteArrayOutputStream()
        val ps = PrintStream(buf)
        System.setOut(ps)
        System.setErr(ps)

        app =
            cli(name = "laret", version = "1.0.0", description = "Alias test CLI") {
                group(name = "file", description = "File operations") {
                    aliases("f", "files")

                    command(name = "create", description = "Create a file") {
                        aliases("c", "new")
                        argument("path", "File path", required = true)
                        option("c", "content", "Content", "", true)
                        action { ctx ->
                            val path = ctx.argument("path")
                            File(path).writeText(ctx.option("content"))
                            println("File created: $path")
                        }
                    }

                    command(name = "delete", description = "Delete a file") {
                        aliases("rm", "del")
                        argument("path", "File path", required = true)
                        action { ctx ->
                            val path = ctx.argument("path")
                            println(
                                if (File(path).delete()) "File deleted: $path" else "Not found: $path"
                            )
                        }
                    }

                    command(name = "read", description = "Read a file") {
                        aliases("r", "cat")
                        argument("path", "File path", required = true)
                        action { ctx ->
                            val f = File(ctx.argument("path"))
                            println(if (f.exists()) f.readText() else "Not found: ${ctx.argument("path")}")
                        }
                    }
                }

                group(name = "dir", description = "Directory operations") {
                    aliases("d")

                    command(name = "list", description = "List directory") {
                        aliases("ls", "l")
                        argument("path", "Directory path", required = false, optional = true, default = ".")
                        action { ctx ->
                            val path = ctx.argument("path").ifEmpty { "." }
                            val dir = File(path)
                            if (!dir.isDirectory) {
                                println("Not a directory: $path")
                                return@action
                            }
                            dir.listFiles()?.sortedBy { it.name }?.forEach { println(it.name) }
                        }
                    }
                }
            }
    }

    @AfterEach
    fun cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        testDir.deleteRecursively()
    }

    /** Returns everything written to stdout/stderr since the last [clear]. */
    private fun output() = buf.toString()

    /** Resets the capture buffer. */
    private fun clear() = buf.reset()

    @Nested
    inner class CommandMatchesTests {
        @Test
        fun `matches returns true for primary name`() {
            val cmd = app.groups[0].commands.find { it.name == "create" }!!
            assertTrue(cmd.matches("create"))
        }

        @Test
        fun `matches returns true for first alias`() {
            val cmd = app.groups[0].commands.find { it.name == "create" }!!
            assertTrue(cmd.matches("c"))
        }

        @Test
        fun `matches returns true for second alias`() {
            val cmd = app.groups[0].commands.find { it.name == "create" }!!
            assertTrue(cmd.matches("new"))
        }

        @Test
        fun `matches returns false for unrelated string`() {
            val cmd = app.groups[0].commands.find { it.name == "create" }!!
            assertFalse(cmd.matches("delete"))
            assertFalse(cmd.matches("xyz"))
            assertFalse(cmd.matches(""))
        }

        @Test
        fun `matches is case sensitive`() {
            val cmd = app.groups[0].commands.find { it.name == "create" }!!
            assertFalse(cmd.matches("CREATE"))
            assertFalse(cmd.matches("C"))
        }
    }

    @Nested
    inner class GroupMatchesTests {
        @Test
        fun `matches returns true for primary name`() {
            val grp = app.groups.find { it.name == "file" }!!
            assertTrue(grp.matches("file"))
        }

        @Test
        fun `matches returns true for alias f`() {
            val grp = app.groups.find { it.name == "file" }!!
            assertTrue(grp.matches("f"))
        }

        @Test
        fun `matches returns true for alias files`() {
            val grp = app.groups.find { it.name == "file" }!!
            assertTrue(grp.matches("files"))
        }

        @Test
        fun `matches returns false for unrelated string`() {
            val grp = app.groups.find { it.name == "file" }!!
            assertFalse(grp.matches("dir"))
            assertFalse(grp.matches("d"))
        }
    }

    @Nested
    inner class ModelPopulationTests {
        @Test
        fun `file group has aliases f and files in order`() {
            val grp = app.groups.find { it.name == "file" }!!
            assertEquals(listOf("f", "files"), grp.aliases)
        }

        @Test
        fun `dir group has one alias d`() {
            val grp = app.groups.find { it.name == "dir" }!!
            assertEquals(listOf("d"), grp.aliases)
        }

        @Test
        fun `create command has aliases c and new`() {
            val cmd = app.groups[0].commands.find { it.name == "create" }!!
            assertEquals(listOf("c", "new"), cmd.aliases)
        }

        @Test
        fun `delete command has aliases rm and del`() {
            val cmd = app.groups[0].commands.find { it.name == "delete" }!!
            assertEquals(listOf("rm", "del"), cmd.aliases)
        }

        @Test
        fun `read command has aliases r and cat`() {
            val cmd = app.groups[0].commands.find { it.name == "read" }!!
            assertEquals(listOf("r", "cat"), cmd.aliases)
        }

        @Test
        fun `list command has aliases ls and l`() {
            val cmd = app.groups[1].commands.find { it.name == "list" }!!
            assertEquals(listOf("ls", "l"), cmd.aliases)
        }

        @Test
        fun `command registered without aliases call has empty list`() {
            val app2 =
                cli(name = "t", version = "1.0") {
                    group(name = "g") {
                        command(name = "cmd") { action {} }
                    }
                }
            assertTrue(
                app2.groups[0]
                    .commands[0]
                    .aliases
                    .isEmpty()
            )
        }

        @Test
        fun `group registered without aliases call has empty list`() {
            val app2 =
                cli(name = "t", version = "1.0") {
                    group(name = "g") {
                        command(name = "cmd") { action {} }
                    }
                }
            assertTrue(app2.groups[0].aliases.isEmpty())
        }
    }

    @Nested
    inner class GroupAliasIntegrationTests {
        @Test
        fun `group alias f routes to file group and creates file`() {
            val path = File(testDir, "via_f.txt").absolutePath
            app.runForTest(arrayOf("f", "create", path, "-c", "hello"))
            assertTrue(File(path).exists(), "file should exist after creation via group alias 'f'")
            assertTrue(output().contains("File created"), "expected 'File created' in output")
        }

        @Test
        fun `group alias files routes to file group`() {
            val path = File(testDir, "via_files.txt").absolutePath
            app.runForTest(arrayOf("files", "create", path, "-c", "world"))
            assertTrue(File(path).exists())
            assertTrue(output().contains("File created"))
        }

        @Test
        fun `group alias d routes to dir group`() {
            File(testDir, "alpha.txt").createNewFile()
            clear()
            app.runForTest(arrayOf("d", "list", testDir.absolutePath))
            assertTrue(output().contains("alpha.txt"))
        }
    }

    @Nested
    inner class CommandAliasIntegrationTests {
        @Test
        fun `command alias c routes to create`() {
            val path = File(testDir, "cmd_c.txt").absolutePath
            app.runForTest(arrayOf("file", "c", path, "-c", "via c"))
            assertTrue(File(path).exists())
            assertEquals("via c", File(path).readText())
        }

        @Test
        fun `command alias new routes to create`() {
            val path = File(testDir, "cmd_new.txt").absolutePath
            app.runForTest(arrayOf("file", "new", path, "-c", "via new"))
            assertTrue(File(path).exists())
            assertEquals("via new", File(path).readText())
        }

        @Test
        fun `command alias rm routes to delete`() {
            val path = File(testDir, "rm_me.txt").absolutePath
            File(path).writeText("bye")
            clear()
            app.runForTest(arrayOf("file", "rm", path))
            assertFalse(File(path).exists(), "file should be deleted via alias 'rm'")
            assertTrue(output().contains("File deleted"))
        }

        @Test
        fun `command alias del routes to delete`() {
            val path = File(testDir, "del_me.txt").absolutePath
            File(path).writeText("bye")
            clear()
            app.runForTest(arrayOf("file", "del", path))
            assertFalse(File(path).exists())
            assertTrue(output().contains("File deleted"))
        }

        @Test
        fun `command alias r routes to read`() {
            val path = File(testDir, "read_r.txt").absolutePath
            File(path).writeText("read via r")
            clear()
            app.runForTest(arrayOf("file", "r", path))
            assertTrue(output().contains("read via r"))
        }

        @Test
        fun `command alias cat routes to read`() {
            val path = File(testDir, "read_cat.txt").absolutePath
            File(path).writeText("read via cat")
            clear()
            app.runForTest(arrayOf("file", "cat", path))
            assertTrue(output().contains("read via cat"))
        }

        @Test
        fun `command alias ls routes to list`() {
            File(testDir, "x.txt").createNewFile()
            clear()
            app.runForTest(arrayOf("dir", "ls", testDir.absolutePath))
            assertTrue(output().contains("x.txt"))
        }

        @Test
        fun `command alias l routes to list`() {
            File(testDir, "y.txt").createNewFile()
            clear()
            app.runForTest(arrayOf("dir", "l", testDir.absolutePath))
            assertTrue(output().contains("y.txt"))
        }
    }

    @Nested
    inner class CombinedAliasTests {
        @Test
        fun `group alias f and command alias rm combined`() {
            val path = File(testDir, "combo_rm.txt").absolutePath
            File(path).writeText("remove me")
            clear()
            app.runForTest(arrayOf("f", "rm", path))
            assertFalse(File(path).exists())
            assertTrue(output().contains("File deleted"))
        }

        @Test
        fun `group alias f and command alias c combined`() {
            val path = File(testDir, "combo_c.txt").absolutePath
            app.runForTest(arrayOf("f", "c", path, "-c", "combo"))
            assertTrue(File(path).exists())
            assertEquals("combo", File(path).readText())
        }

        @Test
        fun `group alias d and command alias ls combined`() {
            File(testDir, "z.txt").createNewFile()
            clear()
            app.runForTest(arrayOf("d", "ls", testDir.absolutePath))
            assertTrue(output().contains("z.txt"))
        }

        @Test
        fun `group alias files and command alias cat combined`() {
            val path = File(testDir, "combo_cat.txt").absolutePath
            File(path).writeText("cat content")
            clear()
            app.runForTest(arrayOf("files", "cat", path))
            assertTrue(output().contains("cat content"))
        }
    }

    @Nested
    inner class PrimaryNameTests {
        @Test
        fun `primary group and command name file create still works`() {
            val path = File(testDir, "primary.txt").absolutePath
            app.runForTest(arrayOf("file", "create", path, "-c", "primary"))
            assertTrue(File(path).exists())
            assertEquals("primary", File(path).readText())
        }

        @Test
        fun `primary group name dir list still works`() {
            File(testDir, "primary_list.txt").createNewFile()
            clear()
            app.runForTest(arrayOf("dir", "list", testDir.absolutePath))
            assertTrue(output().contains("primary_list.txt"))
        }
    }

    @Nested
    inner class ErrorPathTests {
        @Test
        fun `unknown group produces group not found message`() {
            clear()
            app.runForTest(arrayOf("xyz", "create", "somepath"))
            assertTrue(
                output().contains("Group not found"),
                "Expected 'Group not found' in: ${output()}"
            )
        }

        @Test
        fun `unknown command produces error message`() {
            clear()
            app.runForTest(arrayOf("file", "xyz"))
            val out = output()
            assertTrue(
                out.contains("Command not found") || out.contains("not found"),
                "Expected not-found message, got: $out"
            )
        }

        @Test
        fun `typo alias is not matched by group`() {
            val grp = app.groups.find { it.name == "file" }!!
            assertFalse(grp.matches("filez"))
            assertFalse(grp.matches("FILE"))
        }

        @Test
        fun `typo alias is not matched by command`() {
            val cmd = app.groups[0].commands.find { it.name == "create" }!!
            assertFalse(cmd.matches("creat"))
            assertFalse(cmd.matches("C"))
        }
    }
}
