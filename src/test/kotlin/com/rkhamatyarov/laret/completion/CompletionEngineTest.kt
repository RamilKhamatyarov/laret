package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.completion.completers.StaticCompleter
import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("CompletionEngine")
class CompletionEngineTest {

    private lateinit var app: CliApp
    private lateinit var engine: CompletionEngine

    @BeforeEach
    fun setup() {
        app = cli(name = "testcli", version = "1.0.0", description = "Test CLI") {
            group(name = "file", description = "File operations") {
                command(name = "create", description = "Create file") {
                    argument("path", "File path", required = true)
                    option("c", "content", "File content", "", true)
                    option("f", "force", "Overwrite", "", false)
                    action {}
                }
                command(name = "secret", description = "Internal") {
                    hidden()
                    action {}
                }
            }
            group(name = "doc", description = "Documentation") {
                command(name = "generate", description = "Generate docs") {
                    option(
                        "f",
                        "format",
                        "Output format",
                        "md",
                        true,
                        completer = StaticCompleter("md", "man"),
                    )
                    action {}
                }
                command(name = "guide", description = "Scaffold a guide") {
                    argument(
                        "name",
                        "Guide name",
                        required = true,
                        completer = StaticCompleter("installation", "quick-start"),
                    )
                    action {}
                }
            }
        }
        engine = CompletionEngine(app)
    }

    @Test
    fun test_empty_line_completes_group_names_with_descriptions() {
        val result = engine.resolve(listOf(""))

        assertEquals(listOf("file", "doc"), result.candidates.map { it.value })
        assertEquals("File operations", result.candidates.first().description)
        assertEquals(CompletionResult.NO_FILE_COMP, result.directive)
    }

    @Test
    fun test_group_prefix_filters_groups() {
        val result = engine.resolve(listOf("fi"))

        assertEquals(listOf("file"), result.candidates.map { it.value })
    }

    @Test
    fun test_group_typed_completes_commands_and_excludes_hidden() {
        val result = engine.resolve(listOf("file", ""))

        assertEquals(listOf("create"), result.candidates.map { it.value })
    }

    @Test
    fun test_unknown_group_yields_no_candidates_and_no_file_fallback() {
        val result = engine.resolve(listOf("nope", ""))

        assertTrue(result.candidates.isEmpty())
        assertEquals(CompletionResult.NO_FILE_COMP, result.directive)
    }

    @Test
    fun test_dash_prefix_completes_flags_including_global_dry_run() {
        val result = engine.resolve(listOf("file", "create", "--"))

        val values = result.candidates.map { it.value }
        assertContains(values, "--content")
        assertContains(values, "--force")
        assertContains(values, "--dry-run")
    }

    @Test
    fun test_option_value_uses_option_completer_filtered_by_prefix() {
        val result = engine.resolve(listOf("doc", "generate", "--format", "m"))

        assertEquals(listOf("md", "man"), result.candidates.map { it.value })
    }

    @Test
    fun test_positional_argument_uses_argument_completer() {
        val result = engine.resolve(listOf("doc", "guide", "qu"))

        assertEquals(listOf("quick-start"), result.candidates.map { it.value })
    }

    @Test
    fun test_positional_without_completer_falls_back_to_shell_default_directive() {
        val result = engine.resolve(listOf("file", "create", ""))

        assertTrue(result.candidates.isEmpty())
        assertEquals(CompletionResult.DEFAULT, result.directive)
    }

    @Test
    fun test_flag_values_are_skipped_when_counting_positionals() {
        val result = engine.resolve(listOf("doc", "guide", "--dry-run", "inst"))

        assertEquals(listOf("installation"), result.candidates.map { it.value })
    }

    @Test
    fun test_complete_output_ends_with_directive_line_and_uses_tab_separator() {
        val output = engine.complete(listOf(""))

        val lines = output.trimEnd('\n').lines()
        assertEquals(":${CompletionResult.NO_FILE_COMP}", lines.last())
        assertContains(lines.first(), "\t")
    }

    @Test
    fun test_nested_complete_invocation_is_guarded_against_recursion() {
        val result = engine.resolve(listOf(CompletionEngine.COMPLETE_COMMAND, "file", ""))

        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun test_hidden_complete_command_is_intercepted_by_cli_app_and_exits_zero() {
        val originalOut = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true, Charsets.UTF_8))
        val exitCode = try {
            app.runForTest(arrayOf(CompletionEngine.COMPLETE_COMMAND, ""))
        } finally {
            System.setOut(originalOut)
        }

        val output = buffer.toString(Charsets.UTF_8)
        assertEquals(0, exitCode)
        assertContains(output, "file")
        assertContains(output, ":${CompletionResult.NO_FILE_COMP}")
        assertFalse(output.contains("Group not found"), "completion must not fall through to dispatch")
    }
}
