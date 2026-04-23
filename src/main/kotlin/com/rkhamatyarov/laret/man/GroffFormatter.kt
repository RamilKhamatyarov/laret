package com.rkhamatyarov.laret.man

/**
 * Low-level Groff/troff formatting helpers.
 */
object GroffFormatter {

    fun escape(text: String): String {
        var result = text
            .replace("\\", "\\\\")
            .replace("-", "\\-")
            .replace("'", "\\'")

        if (result.startsWith(".")) {
            result = "\\&." + result.substring(1)
        }

        result = result.replace("\n.", "\n\\&.")

        return result
    }

    fun bold(text: String): String = "\\fB${escape(text)}\\fR"

    fun italic(text: String): String = "\\fI${escape(text)}\\fR"

    fun titleHeading(
        title: String,
        section: Int = 1,
        date: String = "",
        source: String = "",
        manual: String = "User Commands",
    ): String = ".TH ${title.uppercase()} $section \"$date\" \"$source\" \"$manual\""

    fun sectionHeading(section: ManSection): String = ".SH ${section.heading}"

    fun taggedParagraph(tag: String, body: String): String = """
        .TP
        $tag
        ${escape(body)}
    """.trimIndent()

    fun paragraph(text: String): String = """
        .PP
        ${escape(text)}
    """.trimIndent()

    fun boldLine(text: String): String = ".B ${escape(text)}"

    fun lineBreak(): String = ".br"
}
