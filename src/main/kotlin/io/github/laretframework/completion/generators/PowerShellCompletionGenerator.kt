package io.github.laretframework.completion.generators

import io.github.laretframework.completion.CompletionGenerator
import io.github.laretframework.completion.template.TemplateContext
import io.github.laretframework.completion.template.TemplateEngine
import io.github.laretframework.core.CliApp

class PowerShellCompletionGenerator(val templateEngine: TemplateEngine = TemplateEngine()) : CompletionGenerator {
    override fun generate(app: CliApp, dynamic: Boolean): String {
        val template = loadTemplate(if (dynamic) "powershell_dynamic" else "powershell")
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

    private fun loadTemplate(name: String): String = javaClass.classLoader.getResource("templates/$name.tpl")
        ?.readText()
        ?: throw IllegalStateException("PowerShell template '$name' not found")
}
