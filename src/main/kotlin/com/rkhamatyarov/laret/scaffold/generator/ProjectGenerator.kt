package com.rkhamatyarov.laret.scaffold.generator

import com.rkhamatyarov.laret.scaffold.model.Module
import com.rkhamatyarov.laret.scaffold.model.ScaffoldConfig
import com.rkhamatyarov.laret.scaffold.template.FileWriter
import com.rkhamatyarov.laret.scaffold.template.TemplateEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.file.Path

class ProjectGenerator(
    private val engine: TemplateEngine = TemplateEngine(),
    private val writer: FileWriter = FileWriter(),
    private val shellTests: ShellTestGenerator = ShellTestGenerator(engine),
) {
    data class Artifact(val relativePath: String, val content: String, val executable: Boolean = false)

    data class GenerationResult(val written: List<Path>, val failures: List<Throwable>)

    suspend fun generate(config: ScaffoldConfig, projectRoot: Path): GenerationResult = coroutineScope {
        val context = buildContext(config)
        val artifacts = buildArtifacts(config, context)

        val tasks = artifacts.map { artifact ->
            async(Dispatchers.IO) {
                val target = projectRoot.resolve(artifact.relativePath)
                writer.write(target, artifact.content, artifact.executable)
            }
        }
        val results = tasks.awaitAll()
        GenerationResult(
            written = results.mapNotNull { it.getOrNull() },
            failures = results.mapNotNull { it.exceptionOrNull() },
        )
    }

    private fun buildContext(config: ScaffoldConfig): Map<String, Any?> {
        val packagePath = config.packageName.replace('.', '/')
        val envPrefix = config.appName.uppercase().replace(Regex("[^A-Z0-9]"), "_").trim('_')
        return mapOf(
            "projectName" to config.projectName,
            "packageName" to config.packageName,
            "packagePath" to packagePath,
            "appName" to config.appName,
            "appNameUpper" to envPrefix,
            "groupId" to config.groupId,
            "artifactId" to config.artifactId,
            "laretVersion" to config.laretVersion,
            "graalvm" to config.graalvm,
            "moduleConfig" to config.modules.contains(Module.CONFIG),
            "moduleCompletion" to config.modules.contains(Module.COMPLETION),
            "moduleUi" to config.modules.contains(Module.UI),
            "moduleOutput" to config.modules.contains(Module.OUTPUT),
            "modules" to config.modules.joinToString(",") { it.id },
        )
    }

    private suspend fun buildArtifacts(config: ScaffoldConfig, context: Map<String, Any?>): List<Artifact> =
        withContext(Dispatchers.IO) {
            val pkgPath = context["packagePath"] as String
            val core = listOf(
                Artifact("build.gradle.kts", renderTpl("templates/scaffold/build.gradle.kts.tpl", context)),
                Artifact("settings.gradle.kts", renderTpl("templates/scaffold/settings.gradle.kts.tpl", context)),
                Artifact(".gitignore", renderTpl("templates/scaffold/gitignore.tpl", context)),
                Artifact(
                    "src/main/kotlin/$pkgPath/Main.kt",
                    renderTpl("templates/scaffold/Main.kt.tpl", context),
                ),
                Artifact(
                    "src/main/kotlin/$pkgPath/commands/HelloCommand.kt",
                    renderTpl("templates/scaffold/HelloCommand.kt.tpl", context),
                ),
            )
            val shell = shellTests.generate(config).map {
                Artifact(it.relativePath, it.content, it.executable)
            }
            core + shell
        }

    private fun renderTpl(resource: String, context: Map<String, Any?>): String {
        val template = engine.copyResourceTemplate(resource)
        return engine.render(template, context)
    }
}
