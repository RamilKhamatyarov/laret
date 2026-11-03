package io.github.laretframework.update

import io.github.laretframework.dsl.cli
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SelfUpdateFeatureInstallerTest {
    @Test
    fun fullBuilderInstallsSelfUpdateCleanupHookWhenEnabled() {
        var cleaned = false
        val installer = SelfUpdateFeatureInstaller { cleaned = true }
        val app = cli(name = "updater", version = "0") {
            selfUpdate = true
        }

        installer.beforeRun(app)

        assertThat(cleaned).isTrue()
        assertThat(app.cleansUpAfterSelfUpdate).isTrue()
    }

    @Test
    fun installerDoesNotCleanUpUntilItsRunHookIsInvoked() {
        val cleaned = false
        val app = cli(name = "plain", version = "0") {}

        assertThat(cleaned).isFalse()
        assertThat(app.cleansUpAfterSelfUpdate).isFalse()
    }
}
