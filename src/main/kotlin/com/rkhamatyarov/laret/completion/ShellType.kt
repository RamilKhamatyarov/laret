package com.rkhamatyarov.laret.completion

enum class ShellType(val extension: String, val templatePath: String) {
    POWERSHELL("ps1", "templates/powershell.tpl"),
    BASH("sh", "templates/bash.tpl"),
    ZSH("zsh", "templates/zsh.tpl"),
}
