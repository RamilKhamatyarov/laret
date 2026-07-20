package com.rkhamatyarov.laret.core

/**
 * Renders the middleware registry for `middleware list`.
 *
 * Chain previews resolve through [MiddlewareRegistry.resolve] — the same call
 * the runner uses — so the preview cannot drift from real execution order.
 */
class MiddlewareListCommand(private val registry: MiddlewareRegistry) {

    /** Every registration: name, priority, effective scope, target. */
    fun renderRegistry(): String {
        val registrations = registry.all()
        if (registrations.isEmpty()) return "No middleware registered\n"

        val rows = registrations.map {
            listOf(it.name, it.priority.toString(), it.scope.name, it.target)
        }
        return renderTable(listOf("NAME", "PRIORITY", "SCOPE", "TARGET"), rows, rightAligned = setOf(1))
    }

    /**
     * The chain that would execute for [groupName] `/` [commandName], outermost
     * first. Lower priority wraps more, so entry 1 enters first and exits last.
     */
    fun renderChain(groupName: String, commandName: String): String {
        val chain = registry.resolve(groupName, commandName)
        val header = "Chain for '$groupName $commandName' (outermost first):\n"
        if (chain.isEmpty()) {
            return header + "  (no middleware applies)\n  → action\n"
        }

        val width = chain.size.toString().length
        val nameWidth = chain.maxOf { it.name.length }
        val body = chain.mapIndexed { index, registration ->
            val position = (index + 1).toString().padStart(width)
            val name = registration.name.padEnd(nameWidth)
            val priority = registration.priority.toString().padStart(6)
            "  $position. $name $priority  ${registration.scope.name}"
        }.joinToString("\n")

        return "$header$body\n  → action\n"
    }

    /**
     * Splits a `--command` value into group and command. Accepts the natural
     * `"file delete"` as well as the colon form `"file:delete"` that the
     * dispatcher already understands.
     */
    fun parseTarget(raw: String): Pair<String, String>? {
        val parts = raw.trim().split(":", " ").filter { it.isNotBlank() }
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    private fun renderTable(
        headers: List<String>,
        rows: List<List<String>>,
        rightAligned: Set<Int> = emptySet(),
    ): String {
        val widths = headers.indices.map { column ->
            maxOf(headers[column].length, rows.maxOf { it[column].length })
        }
        val lines = (listOf(headers) + rows).map { row ->
            row.mapIndexed { column, cell ->
                when {
                    column == row.lastIndex -> cell
                    column in rightAligned -> cell.padStart(widths[column])
                    else -> cell.padEnd(widths[column])
                }
            }.joinToString("  ").trimEnd()
        }
        return lines.joinToString("\n") + "\n"
    }
}
