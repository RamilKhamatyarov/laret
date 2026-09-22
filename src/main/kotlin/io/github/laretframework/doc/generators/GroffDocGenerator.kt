package io.github.laretframework.doc.generators

import io.github.laretframework.completion.ManPageGenerator
import io.github.laretframework.core.CliApp
import io.github.laretframework.doc.DocFile
import io.github.laretframework.doc.DocFormat
import io.github.laretframework.doc.prose.ProseProvider
import io.github.laretframework.doc.prose.ResourceProseProvider
import io.github.laretframework.model.visible

/**
 * Generates Groff man(7) pages into a flat `man1/` tree, one file per command
 * named `man1/<app>-<group>-<command>.1`.
 *
 * This is a thin adapter over the existing [ManPageGenerator]: it delegates the
 * (already battle-tested) Groff string building to that class and wraps the
 * result in a [DocFile], adding the new pure-function [io.github.laretframework.doc.DocGenerator]
 * contract without disturbing the legacy `completion` code path.  The injected
 * [ProseProvider] contributes localized `see_also` cross-references.
 *
 * Man pages are conventionally language-neutral, so [generate] emits a single
 * flat set; the [lang] argument only selects which prose feeds `see_also`.
 */
class GroffDocGenerator(
    private val prose: ProseProvider = ResourceProseProvider(),
    private val manPageGenerator: ManPageGenerator = ManPageGenerator(),
) : io.github.laretframework.doc.DocGenerator {

    override val format: DocFormat = DocFormat.MAN

    override fun generate(app: CliApp, lang: String, includeHidden: Boolean): List<DocFile> =
        app.groups.visible(includeHidden).flatMap { group ->
            group.commands.filter { includeHidden || !it.hidden }.map { command ->
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
