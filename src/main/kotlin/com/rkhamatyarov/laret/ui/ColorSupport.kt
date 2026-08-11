package com.rkhamatyarov.laret.ui

/**
 * Pure, testable decision for whether ANSI color should be emitted.
 *
 * Kept free of `System` calls so unit tests can drive every branch by passing
 * explicit inputs. [Colors.isColorSupported] supplies the live values.
 */
object ColorSupport {
    /**
     * @param env       environment-variable lookup (e.g. `System::getenv`)
     * @param osName    `os.name` system property
     * @param osVersion `os.version` system property
     * @param isTty     whether stdout is an interactive terminal
     */
    fun detect(env: (String) -> String?, osName: String, osVersion: String, isTty: Boolean): Boolean {
        if (!env("NO_COLOR").isNullOrEmpty()) return false

        val force = env("CLICOLOR_FORCE")
        if (!force.isNullOrEmpty() && force != "0") return true

        if (!isTty) return false

        return if (osName.lowercase().contains("win")) {
            windowsMajorVersion(osVersion)?.let { it >= 10 } ?: false
        } else {
            val term = env("TERM")
            term != null && term != "dumb"
        }
    }

    private fun windowsMajorVersion(osVersion: String): Int? = osVersion.substringBefore('.').trim().toIntOrNull()
}
