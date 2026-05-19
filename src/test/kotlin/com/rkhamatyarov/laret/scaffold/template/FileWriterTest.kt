package com.rkhamatyarov.laret.scaffold.template

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileWriterTest {
    private val writer = FileWriter()

    @Test
    fun `write creates file with content`(@TempDir tmp: Path) {
        val target = tmp.resolve("a/b/file.txt")
        val result = writer.write(target, "hello\n")
        assertTrue(result.isSuccess)
        assertEquals("hello\n", Files.readString(target))
    }

    @Test
    fun `write creates parent directories`(@TempDir tmp: Path) {
        val target = tmp.resolve("deep/nested/dir/out.txt")
        writer.write(target, "x")
        assertTrue(Files.isDirectory(target.parent))
    }

    @Test
    fun `write truncates existing content`(@TempDir tmp: Path) {
        val target = tmp.resolve("over.txt")
        writer.write(target, "old contents are longer")
        writer.write(target, "new")
        assertEquals("new", Files.readString(target))
    }

    @Test
    fun `write returns failure when target path is a directory`(@TempDir tmp: Path) {
        val asDir = tmp.resolve("collide")
        Files.createDirectory(asDir)
        val result = writer.write(asDir, "should fail")
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue(msg.contains("Failed to write"))
    }

    @Test
    fun `executable flag does not corrupt file content`(@TempDir tmp: Path) {
        val target = tmp.resolve("script.sh")
        val result = writer.write(target, "#!/bin/bash\necho hi\n", executable = true)
        assertTrue(result.isSuccess)
        assertEquals("#!/bin/bash\necho hi\n", Files.readString(target))
    }
}
