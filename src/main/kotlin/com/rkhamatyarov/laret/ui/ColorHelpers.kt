package com.rkhamatyarov.laret.ui

/** Helper functions for colored terminal output */
fun redBold(text: String): String = if (Colors.isColorSupported()) {
    "${Colors.RED_BOLD}$text${Colors.RESET}"
} else {
    text
}

fun greenBold(text: String): String = if (Colors.isColorSupported()) {
    "${Colors.GREEN_BOLD}$text${Colors.RESET}"
} else {
    text
}

fun yellowBold(text: String): String = if (Colors.isColorSupported()) {
    "${Colors.YELLOW_BOLD}$text${Colors.RESET}"
} else {
    text
}

fun blueBold(text: String): String = if (Colors.isColorSupported()) {
    "${Colors.BLUE_BOLD}$text${Colors.RESET}"
} else {
    text
}

fun cyanBold(text: String): String = if (Colors.isColorSupported()) {
    "${Colors.CYAN_BOLD}$text${Colors.RESET}"
} else {
    text
}

fun yellowItalic(text: String): String = if (Colors.isColorSupported()) {
    "${Colors.YELLOW_ITALIC}$text${Colors.RESET}"
} else {
    text
}

fun redItalic(text: String): String = if (Colors.isColorSupported()) {
    "${Colors.RED_ITALIC}$text${Colors.RESET}"
} else {
    text
}

fun bold(text: String): String = if (Colors.isColorSupported()) {
    "${Colors.BOLD}$text${Colors.RESET}"
} else {
    text
}

fun green(text: String): String = if (Colors.isColorSupported()) {
    "${Colors.GREEN}$text${Colors.RESET}"
} else {
    text
}

fun blue(text: String): String = if (Colors.isColorSupported()) {
    "${Colors.BLUE}$text${Colors.RESET}"
} else {
    text
}
