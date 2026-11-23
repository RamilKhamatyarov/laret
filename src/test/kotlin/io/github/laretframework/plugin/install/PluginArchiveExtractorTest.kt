package io.github.laretframework.plugin.install

import io.github.laretframework.plugin.model.PluginArtifactKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginArchiveExtractorTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `ZIP and tar gz preserve companion files and return inventory`() {
        for (kind in listOf(PluginArtifactKind.ZIP, PluginArtifactKind.TAR_GZ)) {
            val archive = archive(kind, listOf("bin/viewer" to "program", "bin/runtime.dll" to "runtime"))
            val staging = directory.resolve(kind.name)
            val inventory = PluginArchiveExtractor().extract(archive, kind, staging)
            assertEquals(setOf("bin/viewer", "bin/runtime.dll"), inventory.keys)
            assertEquals("runtime", Files.readString(staging.resolve("bin/runtime.dll")))
            assertEquals(PluginInstaller.sha256(staging.resolve("bin/viewer")), inventory["bin/viewer"])
        }
    }

    @Test
    fun `unsafe archive paths and collisions remove staging`() {
        val names = listOf("../outside", "/absolute", "C:/drive", "a//b", "a/../b", "CON", "file.")
        for (kind in listOf(PluginArtifactKind.ZIP, PluginArtifactKind.TAR_GZ)) {
            names.forEach { name ->
                val archive = archive(kind, listOf(name to "unsafe"))
                val staging = directory.resolve("staging")
                assertFailsWith<IllegalArgumentException> { PluginArchiveExtractor().extract(archive, kind, staging) }
                assertFalse(Files.exists(staging))
            }
            val archive = archive(kind, listOf("Bin/viewer" to "one", "bin/runtime" to "two"))
            assertFailsWith<IllegalArgumentException> {
                PluginArchiveExtractor().extract(archive, kind, directory.resolve("collision"))
            }
        }
        assertFalse(Files.exists(directory.resolve("outside")))
    }

    @Test
    fun `byte and entry budgets fail without publishing partial files`() {
        for (kind in listOf(PluginArtifactKind.ZIP, PluginArtifactKind.TAR_GZ)) {
            val archive = archive(kind, listOf("one" to "12345", "two" to "12345"))
            val extractors =
                listOf(PluginArchiveExtractor(maxExpandedBytes = 9), PluginArchiveExtractor(maxEntries = 1))
            for (extractor in extractors) {
                val staging = directory.resolve("limited")
                assertFailsWith<IllegalArgumentException> { extractor.extract(archive, kind, staging) }
                assertFalse(Files.exists(staging))
            }
        }
    }

    @Test
    fun `tar links special files extensions and bad checksum are rejected`() {
        for (type in listOf('1', '2', '3', '4', '6', 'x', 'L')) {
            val archive = directory.resolve("link.tar.gz")
            GZIPOutputStream(Files.newOutputStream(archive)).use { it.write(tar("link", "", type)) }
            assertFailsWith<IllegalArgumentException> {
                PluginArchiveExtractor().extract(archive, PluginArtifactKind.TAR_GZ, directory.resolve("staging"))
            }
        }
        val bytes = tar("file", "payload", '0').also { it[0] = 'X'.code.toByte() }
        val bad = directory.resolve("bad.tar.gz")
        GZIPOutputStream(Files.newOutputStream(bad)).use { it.write(bytes) }
        assertFailsWith<IllegalArgumentException> {
            PluginArchiveExtractor().extract(bad, PluginArtifactKind.TAR_GZ, directory.resolve("staging"))
        }
    }

    @Test
    fun `ZIP unix symlink attribute is rejected`() {
        val archive = archive(PluginArtifactKind.ZIP, listOf("link" to "target"))
        val bytes = Files.readAllBytes(archive)
        val central = bytes.indices.first { i ->
            i + 3 < bytes.size && bytes.sliceArray(i..i + 3).contentEquals(byteArrayOf(0x50, 0x4b, 1, 2))
        }
        bytes[central + 40] = 0xff.toByte()
        bytes[central + 41] = 0xa1.toByte()
        Files.write(archive, bytes)
        assertFailsWith<IllegalArgumentException> {
            PluginArchiveExtractor().extract(archive, PluginArtifactKind.ZIP, directory.resolve("staging"))
        }
        assertFalse(Files.exists(directory.resolve("staging")))
    }

    @Test
    fun `existing staging and contents are never removed`() {
        val staging = Files.createDirectory(directory.resolve("existing"))
        Files.writeString(staging.resolve("keep"), "user data")
        assertFailsWith<IllegalArgumentException> {
            PluginArchiveExtractor().extract(
                archive(PluginArtifactKind.ZIP, listOf("file" to "x")),
                PluginArtifactKind.ZIP,
                staging,
            )
        }
        assertTrue(Files.exists(staging.resolve("keep")))
    }

    private fun archive(kind: PluginArtifactKind, entries: List<Pair<String, String>>): Path {
        val path = directory.resolve("fixture-${kind.name}")
        if (kind == PluginArtifactKind.ZIP) {
            ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
        } else {
            GZIPOutputStream(Files.newOutputStream(path)).use { gzip ->
                entries.forEach { (name, content) -> gzip.write(tar(name, content, '0', end = false)) }
                gzip.write(ByteArray(1024))
            }
        }
        return path
    }

    private fun tar(name: String, content: String, type: Char, end: Boolean = true): ByteArray {
        val header = ByteArray(512)
        fun text(offset: Int, value: String) = value.toByteArray().copyInto(header, offset)
        text(0, name)
        text(124, content.length.toString(8).padStart(11, '0'))
        (148..155).forEach { header[it] = 32 }
        header[156] = type.code.toByte()
        text(257, "ustar")
        val checksum = header.sumOf { it.toInt() and 255 }
        text(148, checksum.toString(8).padStart(6, '0') + "\u0000 ")
        return ByteArrayOutputStream().use { output ->
            output.write(header)
            output.write(content.toByteArray())
            output.write(ByteArray((512 - content.length % 512) % 512))
            if (end) output.write(ByteArray(1024))
            output.toByteArray()
        }
    }
}
