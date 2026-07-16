package com.rkhamatyarov.laret.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rkhamatyarov.laret.core.CliApp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

class TestCli(private val app: CliApp) {
    fun initialize(configPath: String? = null, profile: String? = null): TestCli = apply {
        app.init(configPath, profile)
    }

    fun run(vararg args: String): TestCliResult = run(args.toList())

    fun run(args: List<String>, stdin: String = ""): TestCliResult = runBlocking { runSuspending(args, stdin) }

    suspend fun runSuspending(vararg args: String): TestCliResult = runSuspending(args.toList())

    suspend fun runSuspending(args: List<String>, stdin: String = ""): TestCliResult = executionLock.withLock {
        capture(stdin) { app.runForTestSuspending(args.toTypedArray()) }
    }

    private suspend fun capture(stdin: String, execute: suspend () -> Int): TestCliResult {
        val previousIn = System.`in`
        val previousOut = System.out
        val previousErr = System.err
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val combined = ByteArrayOutputStream()
        val capturedOut = PrintStream(TeeOutputStream(stdout, combined), true, StandardCharsets.UTF_8)
        val capturedErr = PrintStream(TeeOutputStream(stderr, combined), true, StandardCharsets.UTF_8)

        return try {
            System.setIn(ByteArrayInputStream(stdin.toByteArray(StandardCharsets.UTF_8)))
            System.setOut(capturedOut)
            System.setErr(capturedErr)
            val exitCode = execute()
            capturedOut.flush()
            capturedErr.flush()
            TestCliResult(
                exitCode = exitCode,
                stdoutRaw = stdout.toString(StandardCharsets.UTF_8),
                stderrRaw = stderr.toString(StandardCharsets.UTF_8),
                outputRaw = combined.toString(StandardCharsets.UTF_8),
            )
        } finally {
            System.setIn(previousIn)
            System.setOut(previousOut)
            System.setErr(previousErr)
        }
    }

    private companion object {
        val executionLock = Mutex()
    }
}

class TestCliResult internal constructor(
    val exitCode: Int,
    val stdoutRaw: String,
    val stderrRaw: String,
    val outputRaw: String,
) {
    val stdout: String = normalize(stdoutRaw)
    val stderr: String = normalize(stderrRaw)
    val output: String = normalize(outputRaw)

    fun assertSuccess(): TestCliResult = assertExitCode(0)

    fun assertFailure(expectedExitCode: Int? = null): TestCliResult {
        if (expectedExitCode != null) return assertExitCode(expectedExitCode)
        if (exitCode == 0) fail("Expected a non-zero exit code, but command succeeded")
        return this
    }

    fun assertExitCode(expected: Int): TestCliResult {
        if (exitCode != expected) fail("Expected exit code $expected, but was $exitCode")
        return this
    }

    fun assertStdoutEquals(expected: String): TestCliResult {
        if (stdout != normalize(expected)) fail("Expected stdout:\n$expected\nActual stdout:\n$stdout")
        return this
    }

    fun assertStdoutContains(expected: String): TestCliResult {
        if (!stdout.contains(expected)) fail("Expected stdout to contain '$expected', but was:\n$stdout")
        return this
    }

    fun assertStderrContains(expected: String): TestCliResult {
        if (!stderr.contains(expected)) fail("Expected stderr to contain '$expected', but was:\n$stderr")
        return this
    }

    fun assertOutputContains(expected: String): TestCliResult {
        if (!output.contains(expected)) fail("Expected output to contain '$expected', but was:\n$output")
        return this
    }

    fun assertStderrEmpty(): TestCliResult {
        if (stderr.isNotEmpty()) fail("Expected stderr to be empty, but was:\n$stderr")
        return this
    }

    fun assertStdoutLines(vararg expected: String): TestCliResult {
        val actualLines = stdout.trimEnd().lines()
        if (actualLines != expected.toList()) {
            fail("Expected stdout lines $expected, but were $actualLines")
        }
        return this
    }

    fun assertStdoutRawContains(expected: String): TestCliResult {
        if (!stdoutRaw.contains(expected)) fail("Expected raw stdout to contain '$expected'")
        return this
    }

    fun assertStdoutJson(expected: String): TestCliResult {
        val expectedNode = parseJson(expected, "expected JSON")
        val actualNode = parseJson(stdout, "stdout")
        if (actualNode != expectedNode) fail("Expected JSON $expectedNode, but was $actualNode")
        return this
    }

    fun assertJsonPath(path: String, expected: Any?): TestCliResult {
        val root = parseJson(stdout, "stdout")
        val actual = root.at(jsonPointer(path))
        if (actual.isMissingNode) fail("JSON path '$path' was not found in stdout")
        val expectedNode = json.valueToTree<JsonNode>(expected)
        if (actual != expectedNode) fail("Expected JSON path '$path' to be $expectedNode, but was $actual")
        return this
    }

    private fun parseJson(value: String, label: String): JsonNode = try {
        json.readTree(value)
    } catch (error: Exception) {
        fail("Could not parse $label as JSON: ${error.message}")
    }

    private fun jsonPointer(path: String): String {
        require(path == "$" || path.startsWith("$.")) { "JSON path must start with '$' or '$.'" }
        if (path == "$") return ""
        val tokens = Regex("""\.([^.\[\]]+)|\[(\d+)]""")
            .findAll(path.removePrefix("$"))
            .map { match -> match.groups[1]?.value ?: match.groups[2]!!.value }
            .toList()
        return tokens.joinToString(separator = "/", prefix = "/") { token ->
            token.replace("~", "~0").replace("/", "~1")
        }
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)

    private companion object {
        val ansi = Regex("""\u001B\[[0-?]*[ -/]*[@-~]""")
        val json = jacksonObjectMapper()

        fun normalize(value: String): String = value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(ansi, "")
    }
}

private class TeeOutputStream(private val first: OutputStream, private val second: OutputStream) : OutputStream() {
    override fun write(value: Int) {
        first.write(value)
        second.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        first.write(bytes, offset, length)
        second.write(bytes, offset, length)
    }

    override fun flush() {
        first.flush()
        second.flush()
    }
}
