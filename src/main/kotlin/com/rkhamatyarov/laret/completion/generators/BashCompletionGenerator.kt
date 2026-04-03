package com.rkhamatyarov.laret.completion.generators

import com.rkhamatyarov.laret.completion.CompletionGenerator
import com.rkhamatyarov.laret.completion.template.TemplateContext
import com.rkhamatyarov.laret.completion.template.TemplateEngine
import com.rkhamatyarov.laret.core.CliApp

class BashCompletionGenerator(val templateEngine: TemplateEngine = TemplateEngine()) : CompletionGenerator {
    override fun generate(app: CliApp): String {
        val template = loadTemplate()
        // Build the base TemplateContext (needed for options loops)
        val baseContext = buildContext(app)
        val contextMap = baseContext.toMap().toMutableMap()
        // Build a flat list of items: groups + commands
        val items = mutableListOf<Map<String, String>>()
        // Add groups
        app.groups.forEach { group ->
            items.add(mapOf("name" to group.name, "type" to "group"))
        }
        // Add commands (sub‑commands inside groups)
        app.groups.forEach { group ->
            group.commands.forEach { cmd ->
                items.add(mapOf("name" to cmd.name, "type" to "command"))
            }
        }
        contextMap["items"] = items
        return templateEngine.render(template, contextMap)
    }

    private fun buildContext(app: CliApp): TemplateContext = TemplateContext(
        appName = app.name,
        groups = app.groups.map { group ->
            TemplateContext.GroupContext(
                name = group.name,
                commands = group.commands.map { cmd ->
                    TemplateContext.CommandContext(
                        name = cmd.name,
                        description = cmd.description,
                        options = cmd.options.map { opt ->
                            TemplateContext.OptionContext(
                                long = opt.long,
                                short = opt.short,
                                description = opt.description,
                            )
                        },
                    )
                },
            )
        },
    )

    private fun loadTemplate(): String = javaClass.classLoader.getResource("templates/bash.tpl")
        ?.readText()
        ?: throw IllegalStateException("Bash template not found")
}
