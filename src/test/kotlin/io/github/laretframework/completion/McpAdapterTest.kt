package io.github.laretframework.completion

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.laretframework.core.CliApp
import io.github.laretframework.example.buildLaretApp
import io.github.laretframework.model.Argument
import io.github.laretframework.model.Command
import io.github.laretframework.model.CommandGroup
import io.github.laretframework.model.CommandValueType
import io.github.laretframework.model.ImpactCategory
import io.github.laretframework.model.ImpactProfile
import io.github.laretframework.model.Option
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
            arguments = listOf(Argument(name = "path", description = "File path", valueType = CommandValueType.STRING)),
            options = listOf(
                Option(short = "f", long = "force", description = "Overwrite", takesValue = false),
                Option(short = "l", long = "limit", valueType = CommandValueType.INTEGER, default = "4"),
            ),
            impactProfile = ImpactProfile(ImpactCategory.LOCAL_MUTATION),
        )
        val adapter = McpAdapter(appWith(command))

        val response = adapter.handleRequest("""{"jsonrpc":"2.0","method":"tools/list","id":2}""")
        val tool = json.readTree(response)["result"]["tools"][0]

        assertEquals("laret.file.create", tool["name"].asText())
        assertEquals("object", tool["inputSchema"]["type"].asText())
        assertTrue(tool["inputSchema"]["properties"].has("path"))
        assertEquals("local_mutation", tool["impactProfile"]["category"].asText())
        assertTrue(tool["impactProfile"]["capabilityNeeds"].isEmpty)
        assertEquals("integer", tool["inputSchema"]["properties"]["limit"]["type"].asText())
        assertEquals(4, tool["inputSchema"]["properties"]["limit"]["default"].asInt())
    }

    @Test
    fun test_tools_list_serializes_empty_contract_lists_without_kotlin_empty_list_type() {
        val adapter = McpAdapter(appWith(Command("status")))

        val response = adapter.handleRequest("""{"jsonrpc":"2.0","method":"tools/list","id":9}""")
        val tool = json.readTree(response)["result"]["tools"][0]

        assertTrue(tool["inputSchema"]["required"].isEmpty)
        assertTrue(tool["impactProfile"]["capabilityNeeds"].isEmpty)
        assertTrue(!ImpactProfile().capabilityNeeds.javaClass.name.startsWith("kotlin.collections"))
        val required = McpToolMapper().buildInputSchema(Command("status"))["required"] as List<*>
        assertTrue(!required.javaClass.name.startsWith("kotlin.collections"))
    }

    @Test
    fun test_example_app_tools_list_advertises_valid_file_command_contracts() {
        val adapter = McpAdapter(buildLaretApp())

        val response = adapter.handleRequest("""{"jsonrpc":"2.0","method":"tools/list","id":2}""")
        val parsed = json.readTree(response)
        val tools = parsed["result"]["tools"]
        val byName = tools.associateBy { it["name"].asText() }

        assertTrue(byName.containsKey("laret.file.create"), response)
        assertTrue(byName.containsKey("laret.file.read"), response)
        val createSchema = byName.getValue("laret.file.create")["inputSchema"]
        assertTrue(createSchema["required"].any { it.asText() == "path" }, response)
        assertTrue(createSchema["properties"].has("content"), response)
        byName.values.forEach { tool ->
            val properties = tool["inputSchema"]["properties"]
            properties.fieldNames().forEachRemaining { name ->
                assertTrue(properties[name].has("type"), response)
            }
        }
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
