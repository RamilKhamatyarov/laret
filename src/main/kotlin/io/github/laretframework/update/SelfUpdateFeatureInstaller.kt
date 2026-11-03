package io.github.laretframework.update

import io.github.laretframework.core.CliApp
import io.github.laretframework.core.FeatureInstaller

internal class SelfUpdateFeatureInstaller(private val cleanup: () -> Unit = OldBinaryCleaner::cleanupSilently) :
    FeatureInstaller {
    override fun beforeRun(app: CliApp) {
        cleanup()
    }
}
