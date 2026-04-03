package com.rkhamatyarov.laret.completion.generators

import com.rkhamatyarov.laret.completion.CompletionGenerator
import com.rkhamatyarov.laret.completion.template.TemplateContext
import com.rkhamatyarov.laret.completion.template.TemplateEngine
import com.rkhamatyarov.laret.core.CliApp

class ZshCompletionGenerator(val templateEngine: TemplateEngine = TemplateEngine()) : CompletionGenerator {
    override fun generate(app: CliApp): String {
        val template = loadTemplate()
        val baseContext = buildContext(app)
        val contextMap = baseContext.toMap().toMutableMap()
        val items = mutableListOf<Map<String, String>>()
        app.groups.forEach { group ->
            items.add(mapOf("name" to group.name, "description" to "${group.name} command"))
        }
        app.groups.forEach { group ->
            group.commands.forEach { cmd ->
                items.add(mapOf("name" to cmd.name, "description" to cmd.description))
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

    private fun loadTemplate(): String = javaClass.classLoader.getResource("templates/zsh.tpl")
        ?.readText()
        ?: throw IllegalStateException("Zsh template not found")
}
