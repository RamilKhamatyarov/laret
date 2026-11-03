package io.github.laretframework.core

import io.github.laretframework.config.registry.ConfigLayer
import io.github.laretframework.dsl.cli
import io.github.laretframework.model.CommandGroup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeatureRegistryTest {
    @Test
    fun emptyRegistryHasNoHooksOrContributions() {
        val app = cli(name = "empty", version = "0") {}
        val registry = FeatureRegistry()

        registry.beforeRun(app)

        assertThat(registry.interceptDispatch(app, emptyArray())).isNull()
        assertThat(registry.configLayers(app)).isEmpty()
        assertThat(registry.commandGroups(app)).isEmpty()
        registry.shutdown(app)
    }

    @Test
    fun installDelegatesEveryDeclaredHook() {
        val app = cli(name = "feature", version = "0") {}
        val calls = mutableListOf<String>()
        val layer = object : ConfigLayer {
            override fun get(key: String): Any? = null
        }
        val group = CommandGroup("extra", "")
        val registry = FeatureRegistry()
        registry.install(
            object : FeatureInstaller {
                override fun beforeRun(app: CliApp) {
                    calls += "beforeRun"
                }

                override fun interceptDispatch(app: CliApp, args: Array<String>): Int {
                    calls += "dispatch"
                    return 17
                }

                override fun configLayers(app: CliApp): List<ConfigLayer> = listOf(layer)

                override fun commandGroups(app: CliApp): List<CommandGroup> = listOf(group)

                override fun shutdown(app: CliApp) {
                    calls += "shutdown"
                }
            },
        )

        registry.beforeRun(app)

        assertThat(registry.interceptDispatch(app, arrayOf("hidden"))).isEqualTo(17)
        assertThat(registry.configLayers(app)).containsExactly(layer)
        assertThat(registry.commandGroups(app)).containsExactly(group)
        registry.shutdown(app)
        assertThat(calls).containsExactly("beforeRun", "dispatch", "shutdown")
    }

    @Test
    fun cliAppBytecodeDoesNotReferenceOldBinaryCleaner() {
        val bytecode = CliApp::class.java.getResourceAsStream("CliApp.class")!!.use { it.readBytes() }
        val constantPool = bytecode.toString(Charsets.ISO_8859_1)

        assertThat(constantPool).doesNotContain("update/OldBinaryCleaner")
    }
}
