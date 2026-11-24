package io.github.laretframework.plugin.install

import io.github.laretframework.plugin.model.PluginArtifactKind
import io.github.laretframework.plugin.model.requirePluginRelativePath
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/** Extracts only regular files and directories into a new staging directory. */
class PluginArchiveExtractor(
    private val maxExpandedBytes: Long = 1024L * 1024 * 1024,
    private val maxEntries: Int = 10_000,
) {
    init {
        require(maxExpandedBytes > 0 && maxEntries > 0) { "Archive limits must be positive" }
    }

    fun extract(archive: Path, kind: PluginArtifactKind, staging: Path): Map<String, String> {
        require(kind != PluginArtifactKind.BINARY) { "Binary artifacts do not require extraction" }
        require(!Files.exists(staging)) { "Archive staging directory must not already exist" }
        val root = Files.createDirectory(staging).toRealPath()
        val budget = Budget(root)
        try {
            when (kind) {
                PluginArtifactKind.ZIP -> unzip(archive, budget)

                PluginArtifactKind.TAR_GZ -> Files.newInputStream(archive).use { raw ->
                    GZIPInputStream(raw).use { untar(it, budget) }
                }

                PluginArtifactKind.BINARY -> error("Not an archive")
            }
            require(budget.inventory.isNotEmpty()) { "Archive contains no regular files" }
            return budget.inventory.toMap()
        } catch (error: Exception) {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
            throw error
        }
    }

    private fun unzip(archive: Path, budget: Budget) {
        validateZipDirectory(archive)
        ZipFile(archive.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                require(entry.size >= 0) { "ZIP entry has unknown size" }
                val target = budget.entry(entry.name, entry.isDirectory, entry.size)
                if (!entry.isDirectory) {
                    zip.getInputStream(entry).use {
                        budget.file(it, target, entry.size)
                        require(it.read() == -1) { "ZIP entry exceeds its declared size" }
                    }
                }
            }
        }
    }

    private fun validateZipDirectory(archive: Path) {
        RandomAccessFile(archive.toFile(), "r").use { file ->
            val length = file.length()
            require(length >= 22) { "Truncated ZIP archive" }
            var end = length - 22
            val minimum = (length - 65_557).coerceAtLeast(0)
            while (end >= minimum) {
                file.seek(end)
                if (file.u32() == 0x06054b50L) {
                    file.seek(end + 20)
                    if (end + 22 + file.u16() == length) break
                }
                end--
            }
            require(end >= minimum) { "ZIP end record missing" }
            file.seek(end + 4)
            require(file.u16() == 0 && file.u16() == 0) { "Multi-volume ZIP is unsupported" }
            val count = file.u16()
            require(count == file.u16() && count != 65535 && count <= maxEntries) {
                "ZIP entry limit or ZIP64 unsupported"
            }
            val centralSize = file.u32()
            val centralOffset = file.u32()
            require(centralOffset + centralSize == end) { "Invalid ZIP central directory" }
            file.seek(centralOffset)
            repeat(count) {
                require(file.u32() == 0x02014b50L) { "Invalid ZIP entry" }
                file.skipBytes(4)
                require(file.u16() and 1 == 0) { "Encrypted ZIP is unsupported" }
                file.skipBytes(18)
                val nameSize = file.u16()
                val extraSize = file.u16()
                val commentSize = file.u16()
                require(file.u16() == 0) { "Multi-volume ZIP entry unsupported" }
                file.skipBytes(2)
                val attributes = file.u32()
                val type = (attributes ushr 16).toInt() and 0xf000
                require(type == 0 || type == 0x8000 || type == 0x4000) { "ZIP links and special files are forbidden" }
                file.skipBytes(4 + nameSize + extraSize + commentSize)
            }
            require(file.filePointer == end) { "ZIP directory size mismatch" }
        }
    }

    private fun untar(input: InputStream, budget: Budget) {
        while (true) {
            val header = input.readNBytes(512)
            require(header.size == 512) { "Truncated TAR header" }
            if (header.all { it == 0.toByte() }) {
                require(input.readNBytes(512).let { it.size == 512 && it.all { byte -> byte == 0.toByte() } }) {
                    "TAR requires two end records"
                }
                val padding = ByteArray(8192)
                var trailing = 0L
                while (true) {
                    val size = input.read(padding)
                    if (size < 0) return
                    trailing += size
                    require(trailing <= 1024 * 1024 && padding.take(size).all { it == 0.toByte() }) {
                        "Unexpected trailing TAR content"
                    }
                }
            }
            val checksum = header.octal(148, 8)
            val actual = header.indices.sumOf { if (it in 148..155) 32L else (header[it].toInt() and 255).toLong() }
            require(checksum == actual) { "TAR header checksum mismatch" }
            val magic = header.text(257, 6)
            require(magic == "ustar" || magic.isEmpty()) { "Unsupported TAR format" }
            val prefix = if (magic == "ustar") header.text(345, 155) else ""
            val name = listOf(prefix, header.text(0, 100)).filter { it.isNotEmpty() }.joinToString("/")
            val type = header[156].toInt().toChar()
            require(type == '\u0000' || type == '0' || type == '5') {
                "TAR links, special files and extensions are forbidden"
            }
            require(header.text(157, 100).isEmpty()) { "TAR links are forbidden" }
            val size = header.octal(124, 12)
            val target = budget.entry(name, type == '5', size)
            if (type != '5') budget.file(input, target, size)
            val padding = ((512 - size % 512) % 512).toInt()
            require(input.readNBytes(padding).size == padding) { "Truncated TAR padding" }
        }
    }

    private inner class Budget(private val root: Path) {
        val inventory = linkedMapOf<String, String>()
        private val seen = mutableSetOf<String>()
        private val spellings = mutableMapOf<String, String>()
        private var count = 0
        private var bytes = 0L

        fun entry(raw: String, directory: Boolean, size: Long): Path {
            require(++count <= maxEntries) { "Archive exceeds entry limit" }
            val name = if (directory) raw.removeSuffix("/") else raw
            requirePluginRelativePath(name)
            require(
                name.split('/').none { segment ->
                    val base = segment.substringBefore('.').uppercase(Locale.ROOT)
                    segment.endsWith('.') || segment.endsWith(' ') || base in RESERVED_BASE_NAMES
                },
            ) { "Archive contains a non-portable filename" }
            require(seen.add(name.lowercase(Locale.ROOT))) { "Duplicate or case-colliding archive destination: $name" }
            val parts = name.split('/')
            parts.indices.forEach { index ->
                val prefix = parts.take(index + 1).joinToString("/")
                val previous = spellings.putIfAbsent(prefix.lowercase(Locale.ROOT), prefix)
                require(previous == null || previous == prefix) { "Case-colliding archive path: $name" }
            }
            require(size >= 0 && size <= maxExpandedBytes - bytes) { "Archive exceeds expanded size limit" }
            if (directory) require(size == 0L) { "Directory entry has file content" }
            bytes += size
            val path = root.resolve(name).normalize()
            require(path.startsWith(root)) { "Archive path escapes staging" }
            if (directory) Files.createDirectories(path) else Files.createDirectories(path.parent)
            return path
        }

        fun file(input: InputStream, path: Path, size: Long) {
            Files.newOutputStream(path, StandardOpenOption.CREATE_NEW).use { output ->
                var remaining = size
                val buffer = ByteArray(8192)
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    require(count > 0) { "Truncated archive file" }
                    output.write(buffer, 0, count)
                    remaining -= count
                }
            }
            inventory[root.relativize(path).toString().replace('\\', '/')] = PluginInstaller.sha256(path)
        }
    }

    private fun RandomAccessFile.u16(): Int = readUnsignedByte() or (readUnsignedByte() shl 8)

    private fun RandomAccessFile.u32(): Long = u16().toLong() or (u16().toLong() shl 16)

    private fun ByteArray.text(offset: Int, length: Int): String =
        copyOfRange(offset, offset + length).takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)

    private fun ByteArray.octal(offset: Int, length: Int): Long {
        val value = text(offset, length).trim()
        require(value.isNotEmpty() && value.all { it in '0'..'7' }) { "Unsupported TAR numeric field" }
        return value.toLong(8)
    }

    companion object {
        private val RESERVED_BASE_NAMES = setOf("CON", "PRN", "AUX", "NUL") +
            (1..9).flatMap { listOf("COM$it", "LPT$it") }
    }
}
