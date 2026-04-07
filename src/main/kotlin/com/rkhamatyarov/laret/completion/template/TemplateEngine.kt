package com.rkhamatyarov.laret.completion.template

class TemplateEngine {
    fun render(template: String, context: Map<String, Any?>): String {
        val nodes = parse(template)
        return renderNodes(nodes, context)
    }

    fun render(template: String, context: TemplateContext): String = render(template, context.toMap())

    private sealed class Node {
        data class Text(val value: String) : Node()
        data class Variable(val path: String) : Node()
        data class Loop(val collectionPath: String, val body: List<Node>) : Node()
    }

    private fun parse(template: String): List<Node> {
        val nodes = mutableListOf<Node>()
        var i = 0
        val len = template.length

        while (i < len) {
            when {
                template.startsWith("{{#each", i) -> {
                    val tagEnd = template.indexOf("}}", i + 6)
                    if (tagEnd == -1) {
                        nodes.add(Node.Text(template.substring(i)))
                        break
                    }
                    val collectionPath = template.substring(i + 7, tagEnd).trim()
                    i = tagEnd + 2

                    var depth = 1
                    var j = i
                    while (j < len && depth > 0) {
                        when {
                            template.startsWith("{{#each", j) -> {
                                depth++
                                val nextClose = template.indexOf("}}", j + 6)
                                if (nextClose == -1) break
                                j = nextClose + 2
                            }

                            template.startsWith("{{/each}}", j) -> {
                                depth--
                                if (depth == 0) {
                                    val bodyText = template.substring(i, j)
                                    val bodyNodes = parse(bodyText)
                                    nodes.add(Node.Loop(collectionPath, bodyNodes))
                                    i = j + 9
                                    break
                                }
                                j += 9
                            }

                            else -> j++
                        }
                    }
                    if (depth != 0) {
                        nodes.add(Node.Text("{{#each $collectionPath}}"))
                    }
                }

                template.startsWith("{{", i) -> {
                    val end = template.indexOf("}}", i)
                    if (end == -1) {
                        nodes.add(Node.Text(template.substring(i)))
                        break
                    }
                    val varPath = template.substring(i + 2, end).trim()
                    nodes.add(Node.Variable(varPath))
                    i = end + 2
                }

                else -> {
                    val start = i
                    while (i < len && !template.startsWith("{{", i)) {
                        i++
                    }
                    if (start < i) {
                        nodes.add(Node.Text(template.substring(start, i)))
                    }
                }
            }
        }
        return nodes
    }

    private fun renderNodes(nodes: List<Node>, context: Map<String, Any?>): String {
        val result = StringBuilder()
        for (node in nodes) {
            when (node) {
                is Node.Text -> result.append(node.value)

                is Node.Variable -> {
                    val value = resolvePath(context, node.path)?.toString() ?: ""
                    result.append(value)
                }

                is Node.Loop -> {
                    val collection = resolvePath(context, node.collectionPath) as? Collection<*> ?: emptyList<Any?>()
                    val size = collection.size
                    for ((idx, item) in collection.withIndex()) {
                        val isLast = idx == size - 1
                        val sep = if (isLast) "" else ","
                        val itemMap = when (item) {
                            is Map<*, *> -> item
                            is TemplateContext.GroupContext -> item.toMap()
                            is TemplateContext.CommandContext -> item.toMap()
                            is TemplateContext.OptionContext -> item.toMap()
                            else -> mapOf("item" to item)
                        }
                        val loopContext = context.toMutableMap().apply {
                            this["item"] = itemMap
                            this["@index"] = idx
                            this["@last"] = isLast
                            this["sep"] = sep
                        }
                        result.append(renderNodes(node.body, loopContext))
                    }
                }
            }
        }
        return result.toString()
    }

    private fun resolvePath(context: Map<String, Any?>, path: String): Any? {
        if (path.isEmpty()) return null
        val parts = path.split(".")
        var current: Any? = context
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]

                is TemplateContext.GroupContext -> when (part) {
                    "name" -> current.name
                    "commands" -> current.commands
                    else -> null
                }

                is TemplateContext.CommandContext -> when (part) {
                    "name" -> current.name
                    "description" -> current.description
                    "options" -> current.options
                    else -> null
                }

                is TemplateContext.OptionContext -> when (part) {
                    "long" -> current.long
                    "short" -> current.short
                    "description" -> current.description
                    else -> null
                }

                else -> null
            }
            if (current == null) break
        }
        return current
    }
}
