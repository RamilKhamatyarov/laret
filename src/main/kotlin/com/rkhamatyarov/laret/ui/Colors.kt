package com.rkhamatyarov.laret.ui

object Colors {
    const val RESET = "\u001B[0m"

    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val BLUE = "\u001B[34m"
    const val CYAN = "\u001B[36m"

    const val RED_BOLD = "\u001B[1;31m"
    const val GREEN_BOLD = "\u001B[1;32m"
    const val YELLOW_BOLD = "\u001B[1;33m"
    const val BLUE_BOLD = "\u001B[1;34m"
    const val CYAN_BOLD = "\u001B[1;36m"

    const val RED_ITALIC = "\u001B[3;31m"
    const val YELLOW_ITALIC = "\u001B[3;33m"

    fun isColorSupported(): Boolean {
        val term = System.getenv("TERM")
        val os = System.getProperty("os.name").lowercase()

        if (os.contains("windows")) {
            val version = System.getProperty("os.version")
            return version >= "10.0"
        }

        return term != null && term != "dumb"
    }
}
