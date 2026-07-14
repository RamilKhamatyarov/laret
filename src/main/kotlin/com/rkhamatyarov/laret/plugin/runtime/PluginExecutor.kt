package com.rkhamatyarov.laret.plugin.runtime

import com.rkhamatyarov.laret.plugin.install.PluginInstaller

class PluginExecutor(private val catalog: PluginCatalog) {
    fun execute(name: String, args: List<String>, dryRun: Boolean, profile: String?): Int {
        val plugin = catalog.refresh().firstOrNull { it.name == name }
            ?: run {
                System.err.println("Plugin '$name' not found")
                return 127
            }
        val active = catalog.findActive(name)
            ?: run {
                System.err.println("Plugin '$name' cannot execute: ${plugin.reason ?: "invalid"}")
                return 126
            }
        val executable = active.executable ?: return 126
        if (PluginInstaller.sha256(executable) != active.metadata?.sha256) {
            System.err.println("Plugin '$name' failed integrity verification; reinstall with --force")
            return 126
        }
        System.err.println("[plugin: $name] executing...")
        val process = ProcessBuilder(listOf(executable.toString()) + args)
            .inheritIO()
            .apply {
                environment()["LARET_PLUGIN_NAME"] = name
                environment()["LARET_DRY_RUN"] = dryRun.toString()
                environment()["LARET_PROFILE"] = profile.orEmpty()
            }
            .start()
        return try {
            process.waitFor()
        } catch (_: InterruptedException) {
            process.destroy()
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
            Thread.currentThread().interrupt()
            130
        }
    }
}
