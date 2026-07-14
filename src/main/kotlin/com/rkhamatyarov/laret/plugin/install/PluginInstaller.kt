package com.rkhamatyarov.laret.plugin.install

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.rkhamatyarov.laret.plugin.model.PluginMetadata
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.HexFormat

private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
private const val MAX_REDIRECTS = 5

fun interface PluginDownloadClient {
    fun download(url: URI, target: Path): Result<Long>
}

class HttpPluginDownloadClient(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    private val maxBytes: Long = 100L * 1024L * 1024L,
    private val requestTimeout: Duration = Duration.ofMinutes(5),
) : PluginDownloadClient {
    override fun download(url: URI, target: Path): Result<Long> = runCatching {
        require(url.scheme.equals("https", ignoreCase = true)) { "Plugin URL must use HTTPS" }
        var currentUrl = url
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val request = HttpRequest.newBuilder(currentUrl)
                .timeout(requestTimeout)
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() in REDIRECT_STATUS_CODES) {
                require(redirectCount < MAX_REDIRECTS) { "Too many plugin redirects" }
                val location = response.headers().firstValue("Location")
                    .orElseThrow { IOException("Plugin redirect has no Location header") }
                currentUrl = currentUrl.resolve(location)
                require(currentUrl.scheme.equals("https", ignoreCase = true)) {
                    "Plugin redirects must use HTTPS"
                }
            } else {
                require(response.statusCode() in 200..299) {
                    "Plugin download failed with HTTP ${response.statusCode()}"
                }
                val contentLength = response.headers().firstValueAsLong("Content-Length")
                require(contentLength.orElse(0L) <= maxBytes) { "Plugin download exceeds 100 MiB" }
                require(response.body().size <= maxBytes) { "Plugin download exceeds 100 MiB" }
                Files.write(target, response.body())
                return@runCatching response.body().size.toLong()
            }
        }
        error("Plugin download redirect resolution failed")
    }
}

class PluginInstaller(
    private val downloadClient: PluginDownloadClient = HttpPluginDownloadClient(),
    private val now: () -> Instant = Instant::now,
) {
    fun install(
        name: String,
        url: String,
        expectedSha256: String,
        directory: Path,
        force: Boolean = false,
    ): Result<Path> = runCatching {
        validateName(name)
        val normalizedDigest = expectedSha256.lowercase()
        require(normalizedDigest.matches(Regex("[0-9a-f]{64}"))) {
            "SHA-256 must be exactly 64 hexadecimal characters"
        }
        val uri = URI(url)
        require(uri.scheme.equals("https", ignoreCase = true)) { "Plugin URL must use HTTPS" }

        val root = prepareDirectory(directory)
        val executable = root.resolve(platformFileName(name))
        val metadataFile = root.resolve("$name.toml")
        if ((Files.exists(executable) || Files.exists(metadataFile)) && !force) {
            throw IOException("Plugin '$name' is already installed; use --force to replace it")
        }

        val tempExecutable = Files.createTempFile(root, ".$name.", ".download")
        val tempMetadata = Files.createTempFile(root, ".$name.", ".metadata")
        try {
            downloadClient.download(uri, tempExecutable).getOrThrow()
            val actualDigest = sha256(tempExecutable)
            require(actualDigest == normalizedDigest) {
                "SHA-256 mismatch for plugin '$name': expected $normalizedDigest, got $actualDigest"
            }
            setExecutablePermissions(tempExecutable)
            val metadata = PluginMetadata(name, url, normalizedDigest, now().toString())
            TomlMapper().writeValue(tempMetadata.toFile(), metadata)
            setMetadataPermissions(tempMetadata)

            val backupExecutable = if (force && Files.exists(executable)) backup(executable) else null
            val backupMetadata = if (force && Files.exists(metadataFile)) backup(metadataFile) else null
            try {
                move(tempExecutable, executable)
                move(tempMetadata, metadataFile)
                deleteIfExists(backupExecutable)
                deleteIfExists(backupMetadata)
            } catch (error: Exception) {
                deleteIfExists(executable)
                deleteIfExists(metadataFile)
                restore(backupExecutable, executable)
                restore(backupMetadata, metadataFile)
                throw error
            }
            executable
        } finally {
            deleteIfExists(tempExecutable)
            deleteIfExists(tempMetadata)
        }
    }

    private fun prepareDirectory(directory: Path): Path {
        Files.createDirectories(directory)
        return directory.toRealPath()
    }

    private fun move(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun backup(path: Path): Path = Files.createTempFile(
        path.parent,
        ".${path.fileName}.",
        ".backup",
    ).also { backupPath ->
        move(path, backupPath)
    }

    private fun restore(backup: Path?, target: Path) {
        if (backup != null && Files.exists(backup)) move(backup, target)
    }

    private fun deleteIfExists(path: Path?) {
        if (path != null) Files.deleteIfExists(path)
    }

    private fun setExecutablePermissions(path: Path) {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            Files.setPosixFilePermissions(
                path,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        }
    }

    private fun setMetadataPermissions(path: Path) {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            Files.setPosixFilePermissions(
                path,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                ),
            )
        }
    }

    companion object {
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        private const val MAX_REDIRECTS = 5
        fun validateName(name: String) {
            require(name.matches(Regex("[a-z][a-z0-9-]*"))) {
                "Plugin name must use lowercase kebab-case"
            }
        }

        fun platformFileName(name: String): String =
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "$name.exe" else name

        fun sha256(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return HexFormat.of().formatHex(digest.digest())
        }
    }
}
