package com.rkhamatyarov.laret.doc.generators

import com.rkhamatyarov.laret.completion.ManPageGenerator
import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.doc.DocFile
import com.rkhamatyarov.laret.doc.DocFormat
import com.rkhamatyarov.laret.doc.prose.ProseProvider
import com.rkhamatyarov.laret.doc.prose.ResourceProseProvider

/**
 * Generates Groff man(7) pages into a flat `man1/` tree, one file per command
 * named `man1/<app>-<group>-<command>.1`.
 *
 * This is a thin adapter over the existing [ManPageGenerator]: it delegates the
 * (already battle-tested) Groff string building to that class and wraps the
 * result in a [DocFile], adding the new pure-function [com.rkhamatyarov.laret.doc.DocGenerator]
 * contract without disturbing the legacy `completion` code path.  The injected
 * [ProseProvider] contributes localized `see_also` cross-references.
 *
 * Man pages are conventionally language-neutral, so [generate] emits a single
 * flat set; the [lang] argument only selects which prose feeds `see_also`.
 */
class GroffDocGenerator(
    private val prose: ProseProvider = ResourceProseProvider(),
    private val manPageGenerator: ManPageGenerator = ManPageGenerator(),
) : com.rkhamatyarov.laret.doc.DocGenerator {

    override val format: DocFormat = DocFormat.MAN

    override fun generate(app: CliApp, lang: String): List<DocFile> = app.groups.flatMap { group ->
        group.commands.map { command ->
            val resolved = prose.resolve(group.name, command, lang)
            val content = manPageGenerator.generate(
                command = command,
                appName = app.name,
                version = app.version,
                groupName = group.name,
                seeAlso = resolved.seeAlso,
            )
            DocFile(
                relativePath = "man1/${app.name}-${group.name}-${command.name}.1",
                content = content,
            )
        }
    }
}
