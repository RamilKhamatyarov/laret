package io.github.laretframework.completion.generators

import io.github.laretframework.completion.CompletionGenerator
import io.github.laretframework.completion.template.TemplateContext
import io.github.laretframework.completion.template.TemplateEngine
import io.github.laretframework.core.CliApp
import io.github.laretframework.model.visible

class ZshCompletionGenerator(val templateEngine: TemplateEngine = TemplateEngine()) : CompletionGenerator {
    override fun generate(app: CliApp, dynamic: Boolean): String {
        val template = loadTemplate(if (dynamic) "zsh_dynamic" else "zsh")
        val baseContext = buildContext(app)
        val contextMap = baseContext.toMap().toMutableMap()
        val items = mutableListOf<Map<String, String>>()
        app.groups.visible().forEach { group ->
            items.add(mapOf("name" to group.name, "description" to zshQuoted("${group.name} command")))
        }
        app.groups.visible().forEach { group ->
            group.commands.forEach { cmd ->
                items.add(mapOf("name" to cmd.name, "description" to zshQuoted(cmd.description)))
            }
        }
        contextMap["items"] = items
        return templateEngine.render(template, contextMap)
    }

    private fun buildContext(app: CliApp): TemplateContext = TemplateContext(
        appName = app.name,
        groups = app.groups.visible().map { group ->
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
                                description = zshBracketed(opt.description),
                            )
                        },
                    )
                },
            )
        },
    )

    private fun loadTemplate(name: String): String = javaClass.classLoader.getResource("templates/$name.tpl")
        ?.readText()
        ?: throw IllegalStateException("Zsh template '$name' not found")

    private fun zshQuoted(text: String): String = text.replace("'", "'\\''")

    private fun zshBracketed(text: String): String = zshQuoted(text).replace("[", "\\[").replace("]", "\\]")
}
