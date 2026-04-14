package com.rkhamatyarov.laret.man

/**
 * Standard sections of a UNIX man page in display order.
 *
 * Each value corresponds to the Groff `.SH` macro argument that introduces
 * the section.  Section 1 is the conventional section number for user commands
 * in the man-page hierarchy.
 */
enum class ManSection(val heading: String) {
    NAME("NAME"),
    SYNOPSIS("SYNOPSIS"),
    DESCRIPTION("DESCRIPTION"),
    OPTIONS("OPTIONS"),
    EXAMPLES("EXAMPLES"),
    SEE_ALSO("SEE ALSO"),
}
