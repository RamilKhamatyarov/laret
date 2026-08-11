package com.rkhamatyarov.laret.ui

object Colors {
    const val RESET = "[0m"

    const val BOLD = "[1m"

    const val RED = "[31m"
    const val GREEN = "[32m"
    const val YELLOW = "[33m"
    const val BLUE = "[34m"
    const val CYAN = "[36m"

    const val RED_BOLD = "[1;31m"
    const val GREEN_BOLD = "[1;32m"
    const val YELLOW_BOLD = "[1;33m"
    const val BLUE_BOLD = "[1;34m"
    const val CYAN_BOLD = "[1;36m"

    const val RED_ITALIC = "[3;31m"
    const val YELLOW_ITALIC = "[3;33m"

    /**
     * Whether ANSI color should be emitted for the current process.
     *
     * Delegates to the pure [ColorSupport.detect]; stdout is treated as a
     * terminal when `System.console()` is non-null, matching [UnicodeSupport].
     */
    fun isColorSupported(): Boolean = ColorSupport.detect(
        env = System::getenv,
        osName = System.getProperty("os.name", ""),
        osVersion = System.getProperty("os.version", ""),
        isTty = System.console() != null,
    )
}
