package com.rkhamatyarov.laret.man

import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import java.io.File

/**
 * Generates valid Groff man(7) source for a [Command] or an entire application.
 *
 * The generated output passes `mandoc -T lint` without errors.  All special
 * Groff characters are escaped via [GroffFormatter.escape].
 *
 * No new runtime dependencies are introduced — formatting is pure string
 * building.  GraalVM native-image does not need additional reflection config
 * for this module.
 *
 * ### Sections produced
 * `NAME` · `SYNOPSIS` · `DESCRIPTION` · `OPTIONS` · `EXAMPLES` · `SEE ALSO`
 */
class ManPageGenerator {

    /**
     * Generate a complete man page for a single [command].
     *
     * @param command   The command whose metadata is rendered.
     * @param appName   Application name (e.g. `"laret"`).
     * @param version   Application version string (e.g. `"1.0.0"`).
     * @param groupName Name of the group this command belongs to, used in
     *                  the `NAME` line and for `SEE ALSO` cross-references.
     *                  Empty string for top-level commands.
     * @param seeAlso   Additional related page names for `SEE ALSO`.
     * @return Groff man(7) source as a [String].
     */
    fun generate(
        command: Command,
        appName: String,
        version: String,
        groupName: String = "",
        seeAlso: List<String> = emptyList(),
    ): String {
        require(appName.isNotBlank()) { "appName must not be blank" }
        require(version.isNotBlank()) { "version must not be blank" }

        val fullName = if (groupName.isNotBlank()) {
            "$appName-$groupName-${command.name}"
        } else {
            "$appName-${command.name}"
        }

        return buildString {
            appendLine(titleHeading(fullName, appName, version))
            appendLine()
            appendSection(ManSection.NAME)
            appendLine("${GroffFormatter.escape(fullName)} \\- ${GroffFormatter.escape(command.description)}")
            appendLine()
            appendSection(ManSection.SYNOPSIS)
            appendSynopsis(appName, groupName, command)
            appendLine()
            appendSection(ManSection.DESCRIPTION)
            appendDescription(command)
            appendLine()
            if (command.options.isNotEmpty()) {
                appendSection(ManSection.OPTIONS)
                appendOptions(command)
                appendLine()
            }
            appendSection(ManSection.EXAMPLES)
            appendExamples(appName, groupName, command)
            appendLine()
            appendSection(ManSection.SEE_ALSO)
            appendSeeAlso(appName, groupName, command, seeAlso)
        }.trimEnd() + "\n"
    }

    /**
     * Generate man pages for every command in [group] and write each to a
     * file named `<appName>-<groupName>-<commandName>.1` inside [outputDir].
     *
     * @return List of files written.
     */
    fun generateGroup(group: CommandGroup, appName: String, version: String, outputDir: File): List<File> {
        outputDir.mkdirs()
        return group.commands.map { command ->
            val content = generate(command, appName, version, group.name)
            val file = File(outputDir, "$appName-${group.name}-${command.name}.1")
            file.writeText(content)
            file
        }
    }

    private fun titleHeading(fullName: String, appName: String, version: String): String = GroffFormatter.titleHeading(
        title = fullName,
        section = 1,
        date = "",
        source = "$appName $version",
        manual = "User Commands",
    )

    private fun StringBuilder.appendSection(section: ManSection) {
        appendLine(GroffFormatter.sectionHeading(section))
    }

    private fun StringBuilder.appendSynopsis(appName: String, groupName: String, command: Command) {
        val parts = buildList {
            add(appName)
            if (groupName.isNotBlank()) add(groupName)
            add(command.name)
            command.arguments.forEach { arg ->
                add(if (arg.required) "<${arg.name}>" else "[<${arg.name}>]")
            }
            command.options.forEach { opt ->
                val flag = if (opt.takesValue) "[--${opt.long} <value>]" else "[--${opt.long}]"
                add(flag)
            }
        }
        appendLine(GroffFormatter.boldLine(parts.joinToString(" ")))
    }

    private fun StringBuilder.appendDescription(command: Command) {
        if (command.description.isNotBlank()) {
            appendLine(GroffFormatter.paragraph(command.description))
        } else {
            appendLine(GroffFormatter.paragraph("No description available."))
        }
        if (command.arguments.isNotEmpty()) {
            appendLine()
            appendLine(".PP")
            appendLine("${GroffFormatter.bold("Arguments:")}")
            command.arguments.forEach { arg ->
                val req = if (arg.required) " (required)" else " (optional)"
                val tag = GroffFormatter.bold("<${arg.name}>") + req
                val body = if (arg.description.isNotBlank()) arg.description else "No description."
                appendLine(GroffFormatter.taggedParagraph(tag, body))
            }
        }
    }

    private fun StringBuilder.appendOptions(command: Command) {
        command.options.forEach { opt ->
            val shortFlag = GroffFormatter.bold("-${opt.short}")
            val longFlag = GroffFormatter.bold("--${opt.long}")
            val tag = "$shortFlag, $longFlag"
            val body = buildString {
                append(if (opt.description.isNotBlank()) opt.description else "No description.")
                if (opt.default.isNotBlank()) append(" Default: ${opt.default}.")
                if (opt.persistent) append(" (persistent: may be set via config file)")
            }
            appendLine(GroffFormatter.taggedParagraph(tag, body))
        }
    }

    private fun StringBuilder.appendExamples(appName: String, groupName: String, command: Command) {
        val cmdParts = buildList {
            add(appName)
            if (groupName.isNotBlank()) add(groupName)
            add(command.name)
        }.joinToString(" ")

        appendLine(".PP")
        appendLine("Basic usage:")
        appendLine(GroffFormatter.boldLine(cmdParts))

        if (command.arguments.isNotEmpty()) {
            appendLine(GroffFormatter.lineBreak())
            appendLine("With arguments:")
            val argParts = command.arguments.joinToString(" ") { "<${it.name}>" }
            appendLine(GroffFormatter.boldLine("$cmdParts $argParts"))
        }
    }

    private fun StringBuilder.appendSeeAlso(
        appName: String,
        groupName: String,
        command: Command,
        extra: List<String>,
    ) {
        val refs = buildList {
            if (groupName.isNotBlank()) {
                add("$appName-$groupName(1)")
            }
            add("$appName(1)")
            addAll(extra.map { r -> if (r.contains("(")) r else "$r(1)" })
        }.distinct()

        appendLine(refs.joinToString(", "))
    }
}
