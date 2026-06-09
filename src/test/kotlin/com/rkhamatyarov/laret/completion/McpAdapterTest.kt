package com.rkhamatyarov.laret.completion

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.model.Option
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpAdapterTest {
    private val json = jacksonObjectMapper()

    private fun appWith(command: Command): CliApp = CliApp(
        name = "laret",
        version = "0.2.0",
        groups = listOf(CommandGroup(name = "file", commands = listOf(command))),
    )

    @Test
    fun test_initialize_returns_protocol_version_and_echoes_id() {
        val adapter = McpAdapter(CliApp(name = "laret", version = "0.2.0"))

        val response = adapter.handleRequest("""{"jsonrpc":"2.0","method":"initialize","id":1}""")
        val parsed = json.readTree(response)

        assertEquals("2.0", parsed["jsonrpc"].asText())
        assertEquals(1, parsed["id"].asInt())
        assertEquals("2024-11-05", parsed["result"]["protocolVersion"].asText())
    }

    @Test
    fun test_unknown_method_returns_json_rpc_error() {
        val adapter = McpAdapter(CliApp(name = "laret"))

        val response = adapter.handleRequest("""{"jsonrpc":"2.0","method":"unknown","id":42}""")
        val parsed = json.readTree(response)

        assertEquals(-32601, parsed["error"]["code"].asInt())
        assertTrue(parsed["error"]["message"].asText().contains("Method not found"))
    }

    @Test
    fun test_tools_list_returns_mcp_compliant_schema() {
        val command = Command(
            name = "create",
            description = "Create a file",
            arguments = listOf(Argument(name = "path", description = "File path")),
            options = listOf(Option(short = "f", long = "force", description = "Overwrite", takesValue = false)),
        )
        val adapter = McpAdapter(appWith(command))

        val response = adapter.handleRequest("""{"jsonrpc":"2.0","method":"tools/list","id":2}""")
        val tool = json.readTree(response)["result"]["tools"][0]

        assertEquals("laret.file.create", tool["name"].asText())
        assertEquals("object", tool["inputSchema"]["type"].asText())
        assertTrue(tool["inputSchema"]["properties"].has("path"))
    }

    @Test
    fun test_tools_call_with_missing_required_params_returns_invalid_params() {
        val command = Command(name = "create", arguments = listOf(Argument(name = "path")))
        val adapter = McpAdapter(appWith(command))
        val request = """
                {
                  "jsonrpc": "2.0",
                  "method": "tools/call",
                  "id": 3,
                  "params": {
                    "name": "laret.file.create",
                    "arguments": {}
                  }
                }
        """.trimIndent()

        val response = adapter.handleRequest(request)
        val parsed = json.readTree(response)

        assertEquals(-32602, parsed["error"]["code"].asInt())
    }

    @Test
    fun test_tools_call_converts_json_arguments_to_cli_args() {
        val command = Command(
            name = "create",
            arguments = listOf(Argument(name = "path")),
            options = listOf(Option(short = "f", long = "force", takesValue = false)),
        )
        var captured = emptyArray<String>()
        val adapter = McpAdapter(appWith(command)) { args ->
            captured = args
            McpCommandResult(0, "created")
        }
        val request = """
                {
                  "jsonrpc": "2.0",
                  "method": "tools/call",
                  "id": 4,
                  "params": {
                    "name": "laret.file.create",
                    "arguments": {
                      "path": "/tmp/a.txt",
                      "force": true
                    }
                  }
                }
        """.trimIndent()

        val response = adapter.handleRequest(request)
        val parsed = json.readTree(response)

        assertEquals(listOf("file", "create", "/tmp/a.txt", "--force"), captured.toList())
        assertEquals("created", parsed["result"]["content"][0]["text"].asText())
    }

    @Test
    fun test_tools_call_command_failure_returns_execution_error() {
        val command = Command(name = "delete", arguments = listOf(Argument(name = "path")))
        val adapter = McpAdapter(appWith(command)) {
            throw RuntimeException("permission denied")
        }
        val request = """
                {
                  "jsonrpc": "2.0",
                  "method": "tools/call",
                  "id": 5,
                  "params": {
                    "name": "laret.file.delete",
                    "arguments": {
                      "path": "/root/a.txt"
                    }
                  }
                }
        """.trimIndent()

        val response = adapter.handleRequest(request)
        val parsed = json.readTree(response)

        assertEquals(-32000, parsed["error"]["code"].asInt())
        assertTrue(parsed["error"]["message"].asText().contains("permission denied"))
    }
}
