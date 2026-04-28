package com.rkhamatyarov.laret.ui

/**
 * Picks between Unicode and ASCII glyphs for terminal output.
 *
 * Default: ASCII when running on Windows with an attached console, because
 * legacy consoles (cmd.exe, older PowerShell on LTSC) may still render UTF-8
 * output through the system code page and produce mojibake even after Jansi's
 * `SetConsoleOutputCP` call. Override explicitly via:
 *   - `-Dlaret.ui.ascii=true|false`
 *   - `LARET_UI_ASCII=true|false`
 */
object UnicodeSupport {
    val asciiMode: Boolean
        get() {
            System.getProperty("laret.ui.ascii")?.let { return it.toBoolean() }
            System.getenv("LARET_UI_ASCII")?.let { return it.toBoolean() }
            return System.getProperty("os.name", "").lowercase().contains("windows") &&
                System.console() != null
        }

    fun pick(unicode: String, ascii: String): String = if (asciiMode) ascii else unicode
}
