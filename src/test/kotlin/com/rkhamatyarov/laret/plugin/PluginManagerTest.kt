package com.rkhamatyarov.laret.plugin

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.dsl.cli
import com.rkhamatyarov.laret.model.Command
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginManagerTest {
    private lateinit var pluginManager: PluginManager
    private lateinit var app: CliApp
    private lateinit var testCommand: Command

    @BeforeEach
    fun setup() {
        pluginManager = PluginManager()
        app =
            cli(
                name = "test-app",
                version = "1.0.0",
                description = "Test application",
            ) {}
        testCommand =
            Command(
                name = "test",
                description = "Test command",
            )
    }

    @Test
    fun `register should add plugin to list`() {
        val plugin = TestPlugin("TestPlugin1")
        pluginManager.register(plugin)

        assertEquals(1, pluginManager.getPlugins().size)
        assertEquals(plugin, pluginManager.getPlugins()[0])
    }

    @Test
    fun `register should return PluginManager for chaining`() {
        val plugin = TestPlugin("TestPlugin1")
        val result = pluginManager.register(plugin)

        assertEquals(pluginManager, result)
    }

    @Test
    fun `register multiple plugins should maintain order`() {
        val plugin1 = TestPlugin("Plugin1")
        val plugin2 = TestPlugin("Plugin2")
        val plugin3 = TestPlugin("Plugin3")

        pluginManager
            .register(plugin1)
            .register(plugin2)
            .register(plugin3)

        assertEquals(3, pluginManager.getPlugins().size)
        assertEquals(plugin1, pluginManager.getPlugins()[0])
        assertEquals(plugin2, pluginManager.getPlugins()[1])
        assertEquals(plugin3, pluginManager.getPlugins()[2])
    }

    @Test
    fun `register should allow duplicate plugin instances`() {
        val plugin = TestPlugin("DuplicatePlugin")

        pluginManager
            .register(plugin)
            .register(plugin)

        assertEquals(2, pluginManager.getPlugins().size)
    }

    @Test
    fun `initialize should call initialize on all plugins`() {
        val plugin1 = TestPlugin("Plugin1")
        val plugin2 = TestPlugin("Plugin2")

        pluginManager.register(plugin1)
        pluginManager.register(plugin2)

        pluginManager.initialize(app)

        assertTrue(plugin1.initializeCalled)
        assertTrue(plugin2.initializeCalled)
    }

    @Test
    fun `initialize should pass app instance to plugins`() {
        val plugin = TestPlugin("TestPlugin")
        pluginManager.register(plugin)

        pluginManager.initialize(app)

        assertEquals(app, plugin.initializeApp)
    }

    @Test
    fun `initialize should handle plugin exception gracefully`() {
        val workingPlugin = TestPlugin("WorkingPlugin")
        val failingPlugin = FailingPlugin("FailingPlugin", "initialize")

        pluginManager.register(workingPlugin)
        pluginManager.register(failingPlugin)

        pluginManager.initialize(app)

        assertTrue(workingPlugin.initializeCalled)
    }

    @Test
    fun `initialize should be idempotent`() {
        val plugin = TestPlugin("TestPlugin")
        pluginManager.register(plugin)

        pluginManager.initialize(app)
        val callCount1 = plugin.initializeCallCount

        pluginManager.initialize(app)
        val callCount2 = plugin.initializeCallCount

        assertEquals(callCount1 + 1, callCount2)
    }

    @Test
    fun `beforeExecute should return true when all plugins allow execution`() {
        val plugin1 = TestPlugin("Plugin1")
        val plugin2 = TestPlugin("Plugin2")

        pluginManager.register(plugin1)
        pluginManager.register(plugin2)

        val result = pluginManager.beforeExecute(testCommand)

        assertTrue(result)
    }

    @Test
    fun `beforeExecute should return false when any plugin rejects execution`() {
        val allowingPlugin = TestPlugin("AllowingPlugin")
        val rejectingPlugin = RejectingPlugin("RejectingPlugin")

        pluginManager.register(allowingPlugin)
        pluginManager.register(rejectingPlugin)

        val result = pluginManager.beforeExecute(testCommand)

        assertFalse(result)
    }

    @Test
    fun `beforeExecute should call beforeExecute on all plugins in order`() {
        val plugin1 = TestPlugin("Plugin1")
        val plugin2 = TestPlugin("Plugin2")
        val plugin3 = TestPlugin("Plugin3")

        pluginManager.register(plugin1)
        pluginManager.register(plugin2)
        pluginManager.register(plugin3)

        pluginManager.beforeExecute(testCommand)

        assertTrue(plugin1.beforeExecuteCalled)
        assertTrue(plugin2.beforeExecuteCalled)
        assertTrue(plugin3.beforeExecuteCalled)
    }

    @Test
    fun `beforeExecute should pass command to plugins`() {
        val plugin = TestPlugin("TestPlugin")
        pluginManager.register(plugin)

        pluginManager.beforeExecute(testCommand)

        assertEquals(testCommand, plugin.beforeExecuteCommand)
    }

    @Test
    fun `beforeExecute should stop at first rejecting plugin`() {
        val plugin1 = TestPlugin("Plugin1")
        val rejectingPlugin = RejectingPlugin("RejectingPlugin")
        val plugin2 = TestPlugin("Plugin2")

        pluginManager.register(plugin1)
        pluginManager.register(rejectingPlugin)
        pluginManager.register(plugin2)

        pluginManager.beforeExecute(testCommand)

        assertTrue(plugin1.beforeExecuteCalled)
        assertTrue(rejectingPlugin.beforeExecuteCalled)
        assertFalse(plugin2.beforeExecuteCalled)
    }

    @Test
    fun `beforeExecute should handle plugin exception gracefully`() {
        val workingPlugin = TestPlugin("WorkingPlugin")
        val failingPlugin = FailingPlugin("FailingPlugin", "beforeExecute")

        pluginManager.register(workingPlugin)
        pluginManager.register(failingPlugin)

        assertDoesNotThrow {
            pluginManager.beforeExecute(testCommand)
        }

        assertTrue(workingPlugin.beforeExecuteCalled)
    }

    @Test
    fun `beforeExecute should return true with no plugins`() {
        val result = pluginManager.beforeExecute(testCommand)

        assertTrue(result)
    }

    @Test
    fun `afterExecute should call afterExecute on all plugins`() {
        val plugin1 = TestPlugin("Plugin1")
        val plugin2 = TestPlugin("Plugin2")

        pluginManager.register(plugin1)
        pluginManager.register(plugin2)

        pluginManager.afterExecute(testCommand)

        assertTrue(plugin1.afterExecuteCalled)
        assertTrue(plugin2.afterExecuteCalled)
    }

    @Test
    fun `afterExecute should pass command to plugins`() {
        val plugin = TestPlugin("TestPlugin")
        pluginManager.register(plugin)

        pluginManager.afterExecute(testCommand)

        assertEquals(testCommand, plugin.afterExecuteCommand)
    }

    @Test
    fun `afterExecute should handle plugin exception gracefully`() {
        val workingPlugin = TestPlugin("WorkingPlugin")
        val failingPlugin = FailingPlugin("FailingPlugin", "afterExecute")

        pluginManager.register(workingPlugin)
        pluginManager.register(failingPlugin)

        pluginManager.afterExecute(testCommand)

        assertTrue(workingPlugin.afterExecuteCalled)
    }

    @Test
    fun `afterExecute should call all plugins even if one fails`() {
        val plugin1 = TestPlugin("Plugin1")
        val failingPlugin = FailingPlugin("FailingPlugin", "afterExecute")
        val plugin2 = TestPlugin("Plugin2")

        pluginManager.register(plugin1)
        pluginManager.register(failingPlugin)
        pluginManager.register(plugin2)

        pluginManager.afterExecute(testCommand)

        assertTrue(plugin1.afterExecuteCalled)
        assertTrue(plugin2.afterExecuteCalled)
    }

    @Test
    fun `shutdown should call shutdown on all plugins`() {
        val plugin1 = TestPlugin("Plugin1")
        val plugin2 = TestPlugin("Plugin2")

        pluginManager.register(plugin1)
        pluginManager.register(plugin2)

        pluginManager.shutdown()

        assertTrue(plugin1.shutdownCalled)
        assertTrue(plugin2.shutdownCalled)
    }

    @Test
    fun `shutdown should handle plugin exception gracefully`() {
        val workingPlugin = TestPlugin("WorkingPlugin")
        val failingPlugin = FailingPlugin("FailingPlugin", "shutdown")

        pluginManager.register(workingPlugin)
        pluginManager.register(failingPlugin)

        pluginManager.shutdown()

        assertTrue(workingPlugin.shutdownCalled)
    }

    @Test
    fun `shutdown should call all plugins even if one fails`() {
        val plugin1 = TestPlugin("Plugin1")
        val failingPlugin = FailingPlugin("FailingPlugin", "shutdown")
        val plugin2 = TestPlugin("Plugin2")

        pluginManager.register(plugin1)
        pluginManager.register(failingPlugin)
        pluginManager.register(plugin2)

        pluginManager.shutdown()

        assertTrue(plugin1.shutdownCalled)
        assertTrue(plugin2.shutdownCalled)
    }

    @Test
    fun `getPlugins should return immutable copy of plugins list`() {
        val plugin1 = TestPlugin("Plugin1")
        val plugin2 = TestPlugin("Plugin2")

        pluginManager.register(plugin1)
        pluginManager.register(plugin2)

        val plugins1 = pluginManager.getPlugins()
        val plugins2 = pluginManager.getPlugins()

        assertEquals(plugins1, plugins2)
        assertEquals(2, plugins1.size)
    }

    @Test
    fun `getPlugins should return empty list when no plugins registered`() {
        val plugins = pluginManager.getPlugins()

        assertTrue(plugins.isEmpty())
    }

    @Test
    fun `full plugin lifecycle should work correctly`() {
        val plugin = TestPlugin("FullLifecyclePlugin")

        pluginManager.register(plugin)
        assertEquals(1, pluginManager.getPlugins().size)

        pluginManager.initialize(app)
        assertTrue(plugin.initializeCalled)

        val beforeResult = pluginManager.beforeExecute(testCommand)
        assertTrue(beforeResult)
        assertTrue(plugin.beforeExecuteCalled)

        pluginManager.afterExecute(testCommand)
        assertTrue(plugin.afterExecuteCalled)

        pluginManager.shutdown()
        assertTrue(plugin.shutdownCalled)
    }

    @Test
    fun `multiple plugins should work in sequence`() {
        val plugin1 = TestPlugin("Plugin1")
        val plugin2 = TestPlugin("Plugin2")
        val plugin3 = TestPlugin("Plugin3")

        pluginManager
            .register(plugin1)
            .register(plugin2)
            .register(plugin3)

        pluginManager.initialize(app)
        pluginManager.beforeExecute(testCommand)
        pluginManager.afterExecute(testCommand)
        pluginManager.shutdown()

        assertTrue(plugin1.initializeCalled && plugin1.beforeExecuteCalled && plugin1.afterExecuteCalled && plugin1.shutdownCalled)
        assertTrue(plugin2.initializeCalled && plugin2.beforeExecuteCalled && plugin2.afterExecuteCalled && plugin2.shutdownCalled)
        assertTrue(plugin3.initializeCalled && plugin3.beforeExecuteCalled && plugin3.afterExecuteCalled && plugin3.shutdownCalled)
    }
}

class TestPlugin(
    override val name: String,
) : LaretPlugin {
    override val version = "1.0.0"

    var initializeCalled = false
    var initializeCallCount = 0
    var initializeApp: CliApp? = null

    var beforeExecuteCalled = false
    var beforeExecuteCommand: Command? = null

    var afterExecuteCalled = false
    var afterExecuteCommand: Command? = null

    var shutdownCalled = false

    override fun initialize(app: CliApp) {
        initializeCalled = true
        initializeCallCount++
        initializeApp = app
    }

    override fun beforeExecute(command: Command): Boolean {
        beforeExecuteCalled = true
        beforeExecuteCommand = command
        return true
    }

    override fun afterExecute(command: Command) {
        afterExecuteCalled = true
        afterExecuteCommand = command
    }

    override fun shutdown() {
        shutdownCalled = true
    }
}

class RejectingPlugin(
    override val name: String,
) : LaretPlugin {
    override val version = "1.0.0"

    var beforeExecuteCalled = false

    override fun beforeExecute(command: Command): Boolean {
        beforeExecuteCalled = true
        return false
    }
}

class FailingPlugin(
    override val name: String,
    private val failAt: String,
) : LaretPlugin {
    override val version = "1.0.0"

    override fun initialize(app: CliApp) {
        if (failAt == "initialize") throw RuntimeException("Test exception in initialize")
    }

    override fun beforeExecute(command: Command): Boolean {
        if (failAt == "beforeExecute") throw RuntimeException("Test exception in beforeExecute")
        return true
    }

    override fun afterExecute(command: Command) {
        if (failAt == "afterExecute") throw RuntimeException("Test exception in afterExecute")
    }

    override fun shutdown() {
        if (failAt == "shutdown") throw RuntimeException("Test exception in shutdown")
    }
}
