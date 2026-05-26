package com.rkhamatyarov.laret.completion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class McpAdapter(
    private val app: CliApp,
    private val mapper: McpToolMapper = McpToolMapper(),
    private val executor: (Array<String>) -> McpCommandResult = { args -> executeApp(app, args) },
) {
    private val json = jacksonObjectMapper()
    private val commands = app.groups.flatMap { group -> group.commands.map { command -> ToolCommand(group, command) } }
    private val tools: List<McpTool> = commands.map { mapper.mapCommand(it.command, "${app.name}.${it.group.name}") }

    fun handleRequest(request: String): String = try {
        val node = json.readTree(request)
        val id = node.get("id")
        val method = node.get("method")?.asText()

        if (node.get("jsonrpc")?.asText() != "2.0" || method.isNullOrBlank()) {
            return write(error(id, INVALID_REQUEST, "Invalid JSON-RPC request"))
        }

        when (method) {
            "initialize" -> write(result(id, initializeResult()))
            "tools/list" -> write(result(id, mapOf("tools" to tools.map { it.toMap() })))
            "tools/call" -> write(handleToolCall(id, node.get("params")))
            else -> write(error(id, METHOD_NOT_FOUND, "Method not found: $method"))
        }
    } catch (e: Exception) {
        write(error(null, INVALID_REQUEST, "Invalid request: ${e.message}"))
    }

    private fun initializeResult(): Map<String, Any?> = mapOf(
        "protocolVersion" to "2024-11-05",
        "capabilities" to mapOf("tools" to mapOf("listChanged" to false)),
        "serverInfo" to mapOf("name" to "${app.name}-mcp", "version" to app.version),
    )

    private fun handleToolCall(id: JsonNode?, params: JsonNode?): Map<String, Any?> {
        if (params == null || !params.isObject) return error(id, INVALID_PARAMS, "Invalid params")
        val name = params.get("name")?.asText()
            ?: return error(id, INVALID_PARAMS, "Missing required param: name")
        val arguments = params.get("arguments") ?: json.createObjectNode()
        if (!arguments.isObject) return error(id, INVALID_PARAMS, "arguments must be an object")

        val target = commands.firstOrNull { toolName(it) == name || shortToolName(it) == name }
            ?: return error(id, INVALID_PARAMS, "Unknown tool: $name")
        val missing = target.command.arguments
            .filter { it.required && !it.optional && !arguments.has(it.name) }
            .map { it.name }
        if (missing.isNotEmpty()) {
            return error(id, INVALID_PARAMS, "Missing required arguments: ${missing.joinToString(", ")}")
        }

        return try {
            val result = executor(toCliArgs(target, arguments))
            if (result.exitCode == 0) {
                result(
                    id,
                    mapOf(
                        "content" to listOf(
                            mapOf("type" to "text", "text" to result.output),
                        ),
                    ),
                )
            } else {
                error(id, COMMAND_ERROR, result.output.ifBlank { "Command exited with code ${result.exitCode}" })
            }
        } catch (e: Exception) {
            error(id, COMMAND_ERROR, "Command execution failed: ${e.message}")
        }
    }

    private fun toCliArgs(target: ToolCommand, arguments: JsonNode): Array<String> {
        val args = mutableListOf(target.group.name, target.command.name)
        target.command.arguments.forEach { argument ->
            val value = arguments.get(argument.name)?.asText()
            if (value != null) args.add(value)
        }
        target.command.options.forEach { option ->
            val value = arguments.get(option.long) ?: return@forEach
            if (option.takesValue) {
                args.add("--${option.long}")
                args.add(value.asText())
            } else if (value.asBoolean(false)) {
                args.add("--${option.long}")
            }
        }
        return args.toTypedArray()
    }

    private fun toolName(target: ToolCommand): String = "${app.name}.${target.group.name}.${target.command.name}"

    private fun shortToolName(target: ToolCommand): String = "${target.group.name}.${target.command.name}"

    private fun write(payload: Map<String, Any?>): String = json.writeValueAsString(payload)

    private fun result(id: JsonNode?, result: Any?): Map<String, Any?> = mapOf(
        "jsonrpc" to "2.0",
        "id" to id,
        "result" to result,
    )

    private fun error(id: JsonNode?, code: Int, message: String): Map<String, Any?> = mapOf(
        "jsonrpc" to "2.0",
        "id" to id,
        "error" to mapOf(
            "code" to code,
            "message" to message,
        ),
    )

    private data class ToolCommand(val group: CommandGroup, val command: Command)

    companion object {
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val COMMAND_ERROR = -32000

        private fun executeApp(app: CliApp, args: Array<String>): McpCommandResult {
            val previousOut = System.out
            val previousErr = System.err
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            return try {
                System.setOut(PrintStream(out, true))
                System.setErr(PrintStream(err, true))
                val exitCode = app.runForTest(args)
                McpCommandResult(exitCode, (out.toString() + err.toString()).trim())
            } finally {
                System.setOut(previousOut)
                System.setErr(previousErr)
            }
        }
    }
}

data class McpCommandResult(val exitCode: Int, val output: String)
