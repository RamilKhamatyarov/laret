package io.github.laretframework.completion.generators

import io.github.laretframework.completion.CompletionGenerator
import io.github.laretframework.completion.template.TemplateContext
import io.github.laretframework.completion.template.TemplateEngine
import io.github.laretframework.core.CliApp

class BashCompletionGenerator(val templateEngine: TemplateEngine = TemplateEngine()) : CompletionGenerator {
    override fun generate(app: CliApp, dynamic: Boolean): String {
        val template = loadTemplate(if (dynamic) "bash_dynamic" else "bash")
        val baseContext = buildContext(app)
        val contextMap = baseContext.toMap().toMutableMap()
        val items = mutableListOf<Map<String, String>>()

        app.groups.forEach { group ->
            items.add(mapOf("name" to group.name, "type" to "group"))
        }

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

    private fun loadTemplate(name: String): String = javaClass.classLoader.getResource("templates/$name.tpl")
        ?.readText()
        ?: throw IllegalStateException("Bash template '$name' not found")
}
