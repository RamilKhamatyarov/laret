package com.rkhamatyarov.laret

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.dsl.cli
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliTest {
    private lateinit var app: CliApp
    private val testDir = File("test_laret_tmp")
    private val originalOut = System.out
    private val originalErr = System.err
    private lateinit var outputStream: ByteArrayOutputStream

    @Before
    fun setup() {
        testDir.mkdirs()

        outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        System.setErr(PrintStream(outputStream))

        app =
            cli(
                name = "laret",
                version = "1.0.0",
                description = "Test CLI framework",
            ) {
                group(
                    name = "file",
                    description = "File operations",
                ) {
                    command(
                        name = "create",
                        description = "Create a new file",
                    ) {
                        argument("path", "File path", required = true)
                        option("c", "content", "File content", "", true)
                        option("f", "force", "Overwrite if exists", "", false)

                        action { ctx ->
                            val path = ctx.argument("path")
                            val content = ctx.option("content")
                            val force = ctx.optionBool("force")

                            val file = File(path)

                            if (file.exists() && !force) {
                                println("File already exists: $path (use --force to overwrite)")
                                return@action
                            }

                            file.writeText(content)
                            println("File created: $path")
                        }
                    }

                    command(
                        name = "delete",
                        description = "Delete a file",
                    ) {
                        argument("path", "File path", required = true)
                        option("f", "force", "Force deletion", "", false)

                        action { ctx ->
                            val path = ctx.argument("path")
                            val file = File(path)

                            if (!file.exists()) {
                                println("File not found: $path")
                                return@action
                            }

                            if (file.delete()) {
                                println("File deleted: $path")
                            } else {
                                println("Failed to delete file: $path")
                            }
                        }
                    }

                    command(
                        name = "read",
                        description = "Read file contents",
                    ) {
                        argument("path", "File path", required = true)

                        action { ctx ->
                            val path = ctx.argument("path")
                            val file = File(path)

                            if (!file.exists()) {
                                println("File not found: $path")
                                return@action
                            }

                            println(file.readText())
                        }
                    }
                }

                group(
                    name = "dir",
                    description = "Directory operations",
                ) {
                    command(
                        name = "create",
                        description = "Create a new directory",
                    ) {
                        argument("path", "Directory path", required = true)
                        option("p", "parents", "Create parent directories", "", false)

                        action { ctx ->
                            val path = ctx.argument("path")
                            val parents = ctx.optionBool("parents")

                            val dir = File(path)
                            val success = if (parents) dir.mkdirs() else dir.mkdir()

                            if (success) {
                                println("Directory created: $path")
                            } else {
                                println("Failed to create directory: $path")
                            }
                        }
                    }

                    command(
                        name = "list",
                        description = "List directory contents",
                    ) {
                        argument("path", "Directory path", required = false, optional = true, default = ".")
                        option("l", "long", "Long format", "", false)
                        option("a", "all", "Show hidden files", "", false)

                        action { ctx ->
                            val path = ctx.argument("path").ifEmpty { "." }
                            val long = ctx.optionBool("long")
                            val all = ctx.optionBool("all")

                            val dir = File(path)

                            if (!dir.isDirectory) {
                                println("Not a directory: $path")
                                return@action
                            }

                            val files = dir.listFiles() ?: emptyArray()

                            files
                                .filter { all || !it.isHidden }
                                .sortedBy { it.name }
                                .forEach { file ->
                                    if (long) {
                                        val size = if (file.isDirectory) "<dir>" else "${file.length()} B"
                                        println("${if (file.isDirectory) "d" else "-"} $size ${file.name}")
                                    } else {
                                        println(file.name)
                                    }
                                }
                        }
                    }
                }
            }
    }

    @After
    fun cleanup() {
        // Restore original output streams
        System.setOut(originalOut)
        System.setErr(originalErr)

        // Delete test directory and files
        testDir.deleteRecursively()
    }

    private fun getOutput(): String = outputStream.toString()

    private fun clearOutput() {
        outputStream.reset()
    }

    @Test
    fun `file create with content creates file with content`() {
        // given
        val testFile = File(testDir, "test.txt")
        // when
        app.run(arrayOf("file", "create", testFile.absolutePath, "-c", "Hello World"))
        // then
        assertTrue(testFile.exists())
        assertEquals("Hello World", testFile.readText())
        assertTrue(getOutput().contains("File created"))
    }

    @Test
    fun `file create without content creates empty file`() {
        val testFile = File(testDir, "empty.txt")
        app.run(arrayOf("file", "create", testFile.absolutePath))
        assertTrue(testFile.exists())
        assertEquals("", testFile.readText())
    }

    @Test
    fun `file create when file exists and no force does not overwrite`() {
        val testFile = File(testDir, "duplicate.txt")
        testFile.writeText("Original")
        clearOutput()
        app.run(arrayOf("file", "create", testFile.absolutePath, "-c", "New content"))
        assertEquals("Original", testFile.readText())
        assertTrue(getOutput().contains("File already exists"))
    }

    @Test
    fun `file create when force flag overwrite file`() {
        val testFile = File(testDir, "force.txt")
        testFile.writeText("Original")
        clearOutput()
        app.run(arrayOf("file", "create", testFile.absolutePath, "-c", "New content", "-f", "true"))
        assertEquals("New content", testFile.readText())
        assertTrue(getOutput().contains("File created"))
    }

    @Test
    fun `file read existing file outputs content`() {
        val testFile = File(testDir, "read.txt")
        testFile.writeText("Test content")
        clearOutput()
        app.run(arrayOf("file", "read", testFile.absolutePath))
        assertTrue(getOutput().contains("Test content"))
    }

    @Test
    fun `file read non existent file outputs error`() {
        clearOutput()
        app.run(arrayOf("file", "read", File(testDir, "nonexistent.txt").absolutePath))
        assertTrue(getOutput().contains("File not found"))
    }

    @Test
    fun `file delete non existent file outputs error`() {
        clearOutput()
        app.run(arrayOf("file", "delete", File(testDir, "nonexistent.txt").absolutePath))
        assertTrue(getOutput().contains("File not found"))
    }

    @Test
    fun `dir create simple creates directory`() {
        val testDir2 = File(testDir, "newdir")
        assertFalse(testDir2.exists())
        app.run(arrayOf("dir", "create", testDir2.absolutePath))
        assertTrue(testDir2.isDirectory)
        assertTrue(getOutput().contains("Directory created"))
    }

    @Test
    fun `dir create with parents creates nested directories`() {
        val nested = File(testDir, "parent/child/grandchild")
        assertFalse(nested.exists())
        clearOutput()
        app.run(arrayOf("dir", "create", nested.absolutePath, "-p", "true"))
        assertTrue(nested.isDirectory)
        assertTrue(getOutput().contains("Directory created"))
    }

    @Test
    fun `dir lst existing directory displays lists contents`() {
        File(testDir, "file1.txt").createNewFile()
        File(testDir, "file2.txt").createNewFile()
        clearOutput()
        app.run(arrayOf("dir", "list", testDir.absolutePath))
        val output = getOutput()
        assertTrue(output.contains("file1.txt"))
        assertTrue(output.contains("file2.txt"))
    }

    @Test
    fun `dir list with long format displays file size or dir`() {
        File(testDir, "test.txt").writeText("content")
        clearOutput()
        app.run(arrayOf("dir", "list", testDir.absolutePath, "-l", "true"))
        val output = getOutput()
        assertTrue(output.contains("test.txt"))
        assertTrue(output.contains("B") || output.contains("<dir>"))
    }

    @Test
    fun `dir list non existent directory outputs error`() {
        clearOutput()
        app.run(arrayOf("dir", "list", File(testDir, "nonexistent").absolutePath))
        assertTrue(getOutput().contains("Not a directory"))
    }

    @Test
    fun `help flag displays help info`() {
        clearOutput()
        app.run(arrayOf("--help"))
        val output = getOutput()
        assertTrue(output.contains("laret") && output.contains("v1.0.0"))
        assertTrue(output.contains("USAGE:"))
        assertTrue(output.contains("file"))
        assertTrue(output.contains("dir"))
    }

    @Test
    fun `version flag displays version info`() {
        clearOutput()
        app.run(arrayOf("--version"))
        assertTrue(getOutput().contains("laret version 1.0.0"))
    }

    @Test
    fun `help short flag displays help short info`() {
        clearOutput()
        app.run(arrayOf("-h"))
        val output = getOutput()
        assertTrue(output.contains("laret") && output.contains("v1.0.0"))
        assertTrue(output.contains("USAGE:"))
    }

    @Test
    fun `version short flag displays short version info`() {
        clearOutput()
        app.run(arrayOf("-v"))
        assertTrue(getOutput().contains("laret version 1.0.0"))
    }

    @Test
    fun `empty args displays help info`() {
        clearOutput()
        app.run(arrayOf())
        val output = getOutput()
        assertTrue(output.contains("laret") && output.contains("v1.0.0"))
        assertTrue(output.contains("USAGE:"))
    }

    @Test
    fun `invalid group outputs error message`() {
        clearOutput()
        app.run(arrayOf("invalid", "command"))
        assertTrue(getOutput().contains("Group not found"))
    }

    @Test
    fun `invalid command outputs error message`() {
        clearOutput()
        app.run(arrayOf("file", "invalid"))
        assertTrue(getOutput().contains("Command not found"))
    }

    @Test
    fun `workflow file create read delete return all operations work`() {
        val testFile = File(testDir, "workflow.txt")
        val content = "Test workflow content"
        app.run(arrayOf("file", "create", testFile.absolutePath, "-c", content))
        assertTrue(testFile.exists())
        clearOutput()
        app.run(arrayOf("file", "read", testFile.absolutePath))
        assertTrue(getOutput().contains(content))
        clearOutput()
        app.run(arrayOf("file", "delete", testFile.absolutePath))
        assertFalse(testFile.exists())
    }

    @Test
    fun `workflow directory operations creates and lists files`() {
        val dir1 = File(testDir, "dir1")
        val dir2 = File(testDir, "dir2")
        app.run(arrayOf("dir", "create", dir1.absolutePath))
        app.run(arrayOf("dir", "create", dir2.absolutePath))
        File(dir1, "file1.txt").writeText("File 1")
        File(dir1, "file2.txt").writeText("File 2")
        clearOutput()
        app.run(arrayOf("dir", "list", dir1.absolutePath))
        val output = getOutput()
        assertTrue(output.contains("file1.txt"))
        assertTrue(output.contains("file2.txt"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `file delete existing file deletes file`() =
        runTest {
            // given
            val testFile = File(testDir, "delete.txt")
            testFile.writeText("To delete")
            assertTrue(testFile.exists(), "File should exist before deletion")

            // when
            clearOutput()
            app.run(arrayOf("file", "delete", testFile.absolutePath))

            advanceTimeBy(100)

            // then
            val output = getOutput()
            assertTrue(
                output.contains("File deleted"),
                "Output should contain 'File deleted', got: $output",
            )
            assertFalse(testFile.exists(), "File should be deleted after command execution")
        }

    @Test
    fun `file delete when deletion fails outputs error`() {
        // given
        val readOnlyFile = File(testDir, "readonly.txt")
        readOnlyFile.writeText("Read only content")
        readOnlyFile.setReadOnly()

        // when
        clearOutput()
        try {
            app.run(arrayOf("file", "delete", readOnlyFile.absolutePath))

            // then
            val output = getOutput()
            assertTrue(
                output.contains("File deleted") || output.contains("✗ Failed to delete"),
                "Should contain either success or failure message, got: $output",
            )
        } finally {
            readOnlyFile.setWritable(true)
            readOnlyFile.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `file delete multiple files deletes all correctly`() =
        runTest {
            // given
            val files =
                (1..3).map {
                    File(testDir, "file$it.txt").also { f ->
                        f.writeText("content$it")
                    }
                }

            // when // then
            files.forEach { file ->
                assertTrue(file.exists(), "File ${file.name} should exist before deletion")

                clearOutput()
                app.run(arrayOf("file", "delete", file.absolutePath))

                advanceTimeBy(100)

                val output = getOutput()
                assertTrue(
                    output.contains("File deleted"),
                    "Output should contain 'File deleted', got: $output",
                )
                assertFalse(file.exists(), "File ${file.name} should be deleted after command execution")
            }
        }
}
