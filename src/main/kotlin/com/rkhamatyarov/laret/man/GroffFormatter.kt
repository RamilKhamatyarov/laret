package com.rkhamatyarov.laret.man

/**
 * Low-level Groff/troff formatting helpers.
 *
 * All public methods return strings that are safe to embed directly inside
 * a Groff man(7) source file.  No external Groff library is used — formatting
 * is pure string manipulation, keeping GraalVM native-image compilation simple
 * (no reflection config needed for this module).
 */
object GroffFormatter {

    /**
     * Escape plain-text prose for safe inclusion in Groff man(7) output.
     *
     * Rules applied (in order, so that `\` is escaped first):
     * - `\`  → `\\`   (backslash itself — must be first)
     * - `-`  → `\-`   (hyphen rendered as a real minus in terminals)
     * - `'`  → `\'`   (apostrophe, avoids Groff macro confusion)
     * - `.`  at line start → `\&.`  (avoids being interpreted as a macro)
     */
    fun escape(text: String): String {
        var result = text
            .replace("\\", "\\\\")
            .replace("-", "\\-")
            .replace("'", "\\'")

        // Escape dot at the very beginning of the string
        if (result.startsWith(".")) {
            result = "\\&." + result.substring(1)
        }
        // Escape dot that appears right after a newline
        result = result.replace("\n.", "\n\\&.")

        return result
    }

    /**
     * Wrap [text] in bold Groff font escapes: `\fB...\fR`.
     */
    fun bold(text: String): String = "\\fB${escape(text)}\\fR"

    /**
     * Wrap [text] in italic (underline on terminals) Groff font escapes: `\fI...\fR`.
     */
    fun italic(text: String): String = "\\fI${escape(text)}\\fR"

    // ── Macro helpers ──────────────────────────────────────────────────────

    /**
     * Emit a `.TH` (title heading) macro line.
     *
     * @param title    Command name in UPPERCASE.
     * @param section  Man-page section number (1 = user commands).
     * @param date     Build/release date string (e.g. `"2025-01-01"`).
     * @param source   Package name and version (e.g. `"laret 1.0.0"`).
     * @param manual   Manual name displayed in the page header (e.g. `"User Commands"`).
     */
    fun titleHeading(
        title: String,
        section: Int = 1,
        date: String = "",
        source: String = "",
        manual: String = "User Commands",
    ): String = ".TH ${title.uppercase()} $section \"$date\" \"$source\" \"$manual\""

    /**
     * Emit a `.SH` section-heading macro line.
     */
    fun sectionHeading(section: ManSection): String = ".SH ${section.heading}"

    /**
     * Emit a `.TP` tagged-paragraph macro followed by the bold [tag] and [body].
     *
     * Used for listing options and arguments:
     * ```
     * .TP
     * \fB-f\fR, \fB--force\fR
     * Overwrite if exists.
     * ```
     */
    fun taggedParagraph(tag: String, body: String): String = """
        .TP
        $tag
        ${escape(body)}
    """.trimIndent()

    /**
     * Emit a `.PP` paragraph-break macro followed by [text].
     */
    fun paragraph(text: String): String = """
        .PP
        ${escape(text)}
    """.trimIndent()

    /**
     * Emit a `.B` bold-line macro with [text].
     */
    fun boldLine(text: String): String = ".B ${escape(text)}"

    /**
     * Emit a `.br` explicit line-break macro.
     */
    fun lineBreak(): String = ".br"
}
