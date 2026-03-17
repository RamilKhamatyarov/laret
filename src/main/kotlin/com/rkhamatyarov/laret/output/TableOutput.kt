package com.rkhamatyarov.laret.output

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal

object TableOutput : OutputStrategy {
    override val name = "table"

    override fun <T> render(data: T): String {
        val terminal = Terminal(AnsiLevel.NONE)
        return when (data) {
            is List<*> -> renderList(data, terminal)
            is Map<*, *> -> renderMap(data, terminal)
            else -> data.toString()
        }
    }

    private fun renderList(
        list: List<*>,
        terminal: Terminal,
    ): String {
        if (list.isEmpty()) return "No data"

        val first = list.firstOrNull() as? Map<*, *> ?: return list.joinToString("\n")
        val headers = first.keys.map { it.toString() }

        val t =
            table {
                header { row(*headers.toTypedArray()) }
                tableBorders = Borders.ALL
                body {
                    list.forEach { item ->
                        if (item is Map<*, *>) {
                            val rowData = headers.map { header -> item[header]?.toString() ?: "" }
                            row(*rowData.toTypedArray())
                        }
                    }
                }
            }

        return terminal.render(t)
    }

    private fun renderMap(
        map: Map<*, *>,
        terminal: Terminal,
    ): String {
        val headers = map.keys.map { it.toString() }
        val t =
            table {
                header { row(*headers.toTypedArray()) }
                tableBorders = Borders.ALL
                body {
                    val rowData = headers.map { header -> map[header]?.toString() ?: "" }
                    row(*rowData.toTypedArray())
                }
            }

        return terminal.render(t)
    }
}
