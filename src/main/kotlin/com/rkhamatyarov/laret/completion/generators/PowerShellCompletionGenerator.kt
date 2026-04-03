package com.rkhamatyarov.laret.completion.generators

import com.rkhamatyarov.laret.completion.CompletionGenerator
import com.rkhamatyarov.laret.completion.template.TemplateContext
import com.rkhamatyarov.laret.completion.template.TemplateEngine
import com.rkhamatyarov.laret.core.CliApp

class PowerShellCompletionGenerator(val templateEngine: TemplateEngine = TemplateEngine()) : CompletionGenerator {
    override fun generate(app: CliApp): String {
        val template = loadTemplate()
        val context = buildContext(app)
        return templateEngine.render(template, context)
    }

    private fun buildContext(app: CliApp): TemplateContext {
        val uniqueOptions = mutableSetOf<Pair<String, String>>()
        app.groups.forEach { group ->
            group.commands.forEach { cmd ->
                cmd.options.forEach { opt ->
                    uniqueOptions.add(opt.long.trim() to opt.short.trim())
                }
            }
        }
        val globalOptions = uniqueOptions.map { (long, short) ->
            TemplateContext.OptionContext(
                long = long,
                short = short,
                description = "",
            )
        }.sortedBy { it.long }

        return TemplateContext(
            appName = app.name.trim(),
            groups = app.groups.map { group ->
                TemplateContext.GroupContext(
                    name = group.name.trim(),
                    commands = group.commands.map { cmd ->
                        TemplateContext.CommandContext(
                            name = cmd.name.trim(),
                            description = cmd.description.trim(),
                            options = cmd.options.map { opt ->
                                TemplateContext.OptionContext(
                                    long = opt.long.trim(),
                                    short = opt.short.trim(),
                                    description = opt.description.trim(),
                                )
                            },
                        )
                    },
                )
            },
            globalOptions = globalOptions,
        )
    }

    private fun loadTemplate(): String = javaClass.classLoader.getResource("templates/powershell.tpl")
        ?.readText()
        ?: throw IllegalStateException("PowerShell template not found")
}
