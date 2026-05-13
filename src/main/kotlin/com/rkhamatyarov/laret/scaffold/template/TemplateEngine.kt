package com.rkhamatyarov.laret.scaffold.template

import java.util.logging.Level
import java.util.logging.Logger

class TemplateEngine {
    private val log = Logger.getLogger(TemplateEngine::class.java.name)

    fun render(template: String, context: Map<String, Any?>): String {
        val conditionsResolved = resolveConditionals(template, context)
        return substituteVariables(conditionsResolved, context)
    }

    fun copyResourceTemplate(resourcePath: String): String {
        val stream = TemplateEngine::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Template not found: $resourcePath")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun resolveConditionals(template: String, context: Map<String, Any?>): String {
        val sb = StringBuilder()
        var i = 0
        while (i < template.length) {
            val openIdx = template.indexOf(IF_OPEN, i)
            if (openIdx == -1) {
                sb.append(template, i, template.length)
                break
            }
            sb.append(template, i, openIdx)

            val tagEnd = template.indexOf(BLOCK_CLOSE, openIdx + IF_OPEN.length)
            if (tagEnd == -1) {
                sb.append(template, openIdx, template.length)
                break
            }
            val condition = template.substring(openIdx + IF_OPEN.length, tagEnd).trim()
            val bodyStart = tagEnd + BLOCK_CLOSE.length

            val bodyEnd = findMatchingClose(template, bodyStart)
            if (bodyEnd == -1) {
                log.log(Level.FINE) { "Unclosed {{#if $condition}} — keeping placeholder" }
                sb.append(template, openIdx, template.length)
                break
            }

            val body = template.substring(bodyStart, bodyEnd)
            if (isTruthy(context[condition])) {
                sb.append(resolveConditionals(body, context))
            }
            i = bodyEnd + IF_CLOSE.length
        }
        return sb.toString()
    }

    private fun findMatchingClose(template: String, from: Int): Int {
        var depth = 1
        var j = from
        while (j < template.length) {
            when {
                template.startsWith(IF_OPEN, j) -> {
                    depth++
                    val close = template.indexOf(BLOCK_CLOSE, j + IF_OPEN.length)
                    if (close == -1) return -1
                    j = close + BLOCK_CLOSE.length
                }
                template.startsWith(IF_CLOSE, j) -> {
                    depth--
                    if (depth == 0) return j
                    j += IF_CLOSE.length
                }
                else -> j++
            }
        }
        return -1
    }

    private fun substituteVariables(template: String, context: Map<String, Any?>): String {
        val sb = StringBuilder()
        var i = 0
        while (i < template.length) {
            val openIdx = template.indexOf(VAR_OPEN, i)
            if (openIdx == -1) {
                sb.append(template, i, template.length)
                break
            }
            sb.append(template, i, openIdx)

            val closeIdx = template.indexOf(VAR_CLOSE, openIdx + VAR_OPEN.length)
            if (closeIdx == -1) {
                sb.append(template, openIdx, template.length)
                break
            }
            val key = template.substring(openIdx + VAR_OPEN.length, closeIdx).trim()
            val value = context[key]
            if (value == null) {
                log.log(Level.FINE) { "Missing variable '$key' — keeping placeholder" }
                sb.append(template, openIdx, closeIdx + VAR_CLOSE.length)
            } else {
                sb.append(value.toString())
            }
            i = closeIdx + VAR_CLOSE.length
        }
        return sb.toString()
    }

    private fun isTruthy(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is Collection<*> -> value.isNotEmpty()
        is String -> value.isNotBlank() && !value.equals("false", ignoreCase = true)
        is Number -> value.toDouble() != 0.0
        else -> true
    }

    companion object {
        const val VAR_OPEN = "\${"
        const val VAR_CLOSE = "}"
        const val IF_OPEN = "{{#if "
        const val IF_CLOSE = "{{/if}}"
        const val BLOCK_CLOSE = "}}"
    }
}
