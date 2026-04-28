package com.rkhamatyarov.laret.core

import org.jline.terminal.TerminalBuilder

fun CommandContext.isInteractive(): Boolean = System.console() != null &&
    runCatching {
        TerminalBuilder.builder().system(true).computeSystemOutput() != null
    }.getOrDefault(false)
