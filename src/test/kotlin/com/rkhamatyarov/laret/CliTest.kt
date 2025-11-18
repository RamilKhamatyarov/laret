package com.rkhamatyarov.laret

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.dsl.cli
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
        // Create test directory
        testDir.mkdirs()

        // Capture output
        outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        System.setErr(PrintStream(outputStream))

        // Initialize CLI app
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
                            println("✓ File created: $path")
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
                                println("✓ File deleted: $path")
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
                                println("✓ Directory created: $path")
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
    fun fileCreate_withContent_createsFileWithContent() {
        // given
        val testFile = File(testDir, "test.txt")
        // when
        app.run(arrayOf("file", "create", testFile.absolutePath, "-c", "Hello World"))
        // then
        assertTrue(testFile.exists())
        assertEquals("Hello World", testFile.readText())
        assertTrue(getOutput().contains("✓ File created"))
    }

    @Test
    fun fileCreate_withoutContent_createsEmptyFile() {
        val testFile = File(testDir, "empty.txt")
        app.run(arrayOf("file", "create", testFile.absolutePath))
        assertTrue(testFile.exists())
        assertEquals("", testFile.readText())
    }

    @Test
    fun fileCreate_whenFileExistsAndNoForce_doesNotOverwrite() {
        val testFile = File(testDir, "duplicate.txt")
        testFile.writeText("Original")
        clearOutput()
        app.run(arrayOf("file", "create", testFile.absolutePath, "-c", "New content"))
        assertEquals("Original", testFile.readText())
        assertTrue(getOutput().contains("File already exists"))
    }

    @Test
    fun fileCreate_whenForceFlag_overwritesFile() {
        val testFile = File(testDir, "force.txt")
        testFile.writeText("Original")
        clearOutput()
        app.run(arrayOf("file", "create", testFile.absolutePath, "-c", "New content", "-f", "true"))
        assertEquals("New content", testFile.readText())
        assertTrue(getOutput().contains("✓ File created"))
    }

    @Test
    fun fileRead_existingFile_outputsContent() {
        val testFile = File(testDir, "read.txt")
        testFile.writeText("Test content")
        clearOutput()
        app.run(arrayOf("file", "read", testFile.absolutePath))
        assertTrue(getOutput().contains("Test content"))
    }

    @Test
    fun fileRead_nonExistentFile_outputsError() {
        clearOutput()
        app.run(arrayOf("file", "read", File(testDir, "nonexistent.txt").absolutePath))
        assertTrue(getOutput().contains("File not found"))
    }

    @Test
    fun fileDelete_existingFile_deletesFile() {
        val testFile = File(testDir, "delete.txt")
        testFile.writeText("To delete")
        assertTrue(testFile.exists())
        clearOutput()
        app.run(arrayOf("file", "delete", testFile.absolutePath))
        assertFalse(testFile.exists())
        assertTrue(getOutput().contains("✓ File deleted"))
    }

    @Test
    fun fileDelete_nonExistentFile_outputsError() {
        clearOutput()
        app.run(arrayOf("file", "delete", File(testDir, "nonexistent.txt").absolutePath))
        assertTrue(getOutput().contains("File not found"))
    }

    @Test
    fun dirCreate_simple_createsDirectory() {
        val testDir2 = File(testDir, "newdir")
        assertFalse(testDir2.exists())
        app.run(arrayOf("dir", "create", testDir2.absolutePath))
        assertTrue(testDir2.isDirectory)
        assertTrue(getOutput().contains("✓ Directory created"))
    }

    @Test
    fun dirCreate_withParents_createsNestedDirectories() {
        val nested = File(testDir, "parent/child/grandchild")
        assertFalse(nested.exists())
        clearOutput()
        app.run(arrayOf("dir", "create", nested.absolutePath, "-p", "true"))
        assertTrue(nested.isDirectory)
        assertTrue(getOutput().contains("✓ Directory created"))
    }

    @Test
    fun dirList_existingDirectory_listsContents() {
        File(testDir, "file1.txt").createNewFile()
        File(testDir, "file2.txt").createNewFile()
        clearOutput()
        app.run(arrayOf("dir", "list", testDir.absolutePath))
        val output = getOutput()
        assertTrue(output.contains("file1.txt"))
        assertTrue(output.contains("file2.txt"))
    }

    @Test
    fun dirList_withLongFormat_displaysFileSizeOrDir() {
        File(testDir, "test.txt").writeText("content")
        clearOutput()
        app.run(arrayOf("dir", "list", testDir.absolutePath, "-l", "true"))
        val output = getOutput()
        assertTrue(output.contains("test.txt"))
        assertTrue(output.contains("B") || output.contains("<dir>"))
    }

    @Test
    fun dirList_nonExistentDirectory_outputsError() {
        clearOutput()
        app.run(arrayOf("dir", "list", File(testDir, "nonexistent").absolutePath))
        assertTrue(getOutput().contains("Not a directory"))
    }

    @Test
    fun helpFlag_displaysHelpInfo() {
        clearOutput()
        app.run(arrayOf("--help"))
        val output = getOutput()
        assertTrue(output.contains("laret") && output.contains("v1.0.0"))
        assertTrue(output.contains("USAGE:"))
        assertTrue(output.contains("file"))
        assertTrue(output.contains("dir"))
    }

    @Test
    fun versionFlag_displaysVersionInfo() {
        clearOutput()
        app.run(arrayOf("--version"))
        assertTrue(getOutput().contains("laret version 1.0.0"))
    }

    @Test
    fun helpShortFlag_displaysHelpShortInfo() {
        clearOutput()
        app.run(arrayOf("-h"))
        val output = getOutput()
        assertTrue(output.contains("laret") && output.contains("v1.0.0"))
        assertTrue(output.contains("USAGE:"))
    }

    @Test
    fun versionShortFlag_displaysShortVersionInfo() {
        clearOutput()
        app.run(arrayOf("-v"))
        assertTrue(getOutput().contains("laret version 1.0.0"))
    }

    @Test
    fun emptyArgs_displaysHelpInfo() {
        clearOutput()
        app.run(arrayOf())
        val output = getOutput()
        assertTrue(output.contains("laret") && output.contains("v1.0.0"))
        assertTrue(output.contains("USAGE:"))
    }

    @Test
    fun invalidGroup_outputsErrorMessage() {
        clearOutput()
        app.run(arrayOf("invalid", "command"))
        assertTrue(getOutput().contains("Group not found"))
    }

    @Test
    fun invalidCommand_outputsErrorMessage() {
        clearOutput()
        app.run(arrayOf("file", "invalid"))
        assertTrue(getOutput().contains("Command not found"))
    }

    @Test
    fun missingRequiredArgument_outputsErrorMessage() {
        clearOutput()
        app.run(arrayOf("file", "create"))
        assertTrue(getOutput().contains("Error: Required argument"))
    }

    @Test
    fun workflow_fileCreateReadDelete_allOperationsWork() {
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
    fun workflow_directoryOperations_createsAndListsFiles() {
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

    @Test
    fun multipleOperations_createAndDeleteMultipleFilesAllWork() {
        val files =
            listOf(
                File(testDir, "test1.txt"),
                File(testDir, "test2.txt"),
                File(testDir, "test3.txt"),
            )
        files.forEach { file ->
            app.run(arrayOf("file", "create", file.absolutePath, "-c", "Content of ${file.name}"))
        }
        files.forEach { assertTrue(it.exists()) }
        files.forEach { file ->
            clearOutput()
            app.run(arrayOf("file", "delete", file.absolutePath))
        }
        files.forEach { assertFalse(it.exists()) }
    }
}
