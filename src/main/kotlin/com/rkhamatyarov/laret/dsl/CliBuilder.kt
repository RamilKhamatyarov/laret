package com.rkhamatyarov.laret.dsl

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.core.CommandRunner
import com.rkhamatyarov.laret.core.Middleware
import com.rkhamatyarov.laret.core.MiddlewareScope
import com.rkhamatyarov.laret.model.CommandGroup

/** Builds CLI application */
class CliBuilder(val name: String, val version: String, val description: String) {
    private val groups = mutableListOf<CommandGroup>()
    private val middlewares = mutableListOf<Middleware>()

    var onAppInit: suspend (CliApp) -> Unit = {}
    var onAppShutdown: suspend (CliApp) -> Unit = {}

    fun group(name: String, description: String = "", block: GroupBuilder.() -> Unit) {
        val groupBuilder = GroupBuilder(name, description)
        groupBuilder.block()
        groups.add(groupBuilder.build())
    }

    fun use(vararg middleware: Middleware) {
        middlewares.addAll(middleware)
    }

    fun build(): CliApp {
        val global = middlewares.filter { it.scope == MiddlewareScope.GLOBAL }
        val groupScoped = middlewares.filter { it.scope == MiddlewareScope.GROUP }
        val commandScoped = middlewares.filter { it.scope == MiddlewareScope.COMMAND }

        CommandRunner.globalMiddlewares = global
        CommandRunner.groupMiddlewares = groupScoped.groupBy { it::class.simpleName ?: "" }

        val app = CliApp(name, version, description, groups)
        app.onInitHook = onAppInit
        app.onShutdownHook = onAppShutdown
        return app
    }
}
