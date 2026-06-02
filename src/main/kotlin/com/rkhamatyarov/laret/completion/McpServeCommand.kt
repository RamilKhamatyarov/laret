package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.dsl.GroupBuilder
import java.io.BufferedReader
import java.io.InputStreamReader

object McpServeCommand {
    fun register(group: GroupBuilder) {
        group.command(name = "serve", description = "Start MCP server exposing CLI commands as tools") {
            option("t", "transport", "Transport protocol: stdio", "stdio", true)
            option("p", "port", "Reserved for future HTTP/SSE transport", "", true)

            action { ctx ->
                val transport = ctx.option("transport").ifBlank { "stdio" }
                if (transport != "stdio") {
                    System.err.println("Unsupported MCP transport: $transport. Only stdio is implemented.")
                    return@action
                }
                runStdio(ctx)
            }
        }
    }

    private fun runStdio(ctx: CommandContext) {
        val app = ctx.app ?: return
        val adapter = McpAdapter(app)
        val reader = BufferedReader(InputStreamReader(System.`in`))
        val shutdownHook = Thread { System.err.println("[laret-mcp] Server shutting down") }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        System.err.println("[laret-mcp] Server started with stdio transport")

        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                println(adapter.handleRequest(line))
                System.out.flush()
            }
        } finally {
            runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
            reader.close()
        }
    }
}
