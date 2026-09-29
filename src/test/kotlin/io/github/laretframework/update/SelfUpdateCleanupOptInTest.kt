package io.github.laretframework.update

import io.github.laretframework.dsl.cli
import io.github.laretframework.example.buildLaretApp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SelfUpdateCleanupOptInTest {

    @Test
    fun anAppDoesNotCleanUpUnlessItOptsIn() {
        val app = cli(name = "plain", version = "0", description = "no self-update") {}

        assertThat(app.cleansUpAfterSelfUpdate).isFalse()
    }

    @Test
    fun anAppThatUpdatesItselfCleansUp() {
        val app = cli(name = "updater", version = "0", description = "self-updating") {
            selfUpdate = true
        }

        assertThat(app.cleansUpAfterSelfUpdate).isTrue()
    }

    @Test
    fun theDemoAppOptsInBecauseItShipsUpdateCommands() {
        assertThat(buildLaretApp().cleansUpAfterSelfUpdate).isTrue()
    }
}
