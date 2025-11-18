package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.core.CliApp

class PowerShellCompletionGenerator : CompletionGenerator {
    override fun generate(app: CliApp): String {
        val appName = app.name

        return """
            |# PowerShell completion for $appName
            |
            |${'$'}scriptblock = {
            |    param(${'$'}wordToComplete, ${'$'}commandAst, ${'$'}cursorPosition)
            |    
            |    ${'$'}command = ${'$'}commandAst.ToString()
            |    
            |    # Разбейте команду на токены
            |    ${'$'}tokens = ${'$'}command -split '\s+' | Where-Object { ${'$'}_ }
            |    
            |    # Найдите индекс "$appName" или "$appName.exe"
            |    ${'$'}startIdx = 0
            |    for (${'$'}i = 0; ${'$'}i -lt ${'$'}tokens.Count; ${'$'}i++) {
            |        if (${'$'}tokens[${'$'}i] -eq '$appName' -or ${'$'}tokens[${'$'}i] -eq '$appName.exe') {
            |            ${'$'}startIdx = ${'$'}i + 1
            |            break
            |        }
            |    }
            |    
            |    # Получите аргументы после "$appName"
            |    ${'$'}args = ${'$'}tokens[${'$'}startIdx..(${'$'}tokens.Count-1)] | Where-Object { ${'$'}_ }
            |    
            |    ${'$'}completions = @()
            |    
            |    # Определите что completировать
            |    if (${'$'}args.Count -eq 0) {
            |        # Нет аргументов - показать группы
            |        ${'$'}groups = @(
            |            ${app.groups.joinToString(",\n") { "                        '${it.name}'" }}
            |        )
            |        ${'$'}completions = ${'$'}groups | Where-Object { ${'$'}_ -like "${'$'}wordToComplete*" }
            |    }
            |    elseif (${'$'}args.Count -eq 1) {
            |        # Один аргумент - это группа или неполная группа
            |        ${'$'}validGroups = @(
            |            ${app.groups.joinToString(",\n") { "                        '${it.name}'" }}
            |        )
            |        ${'$'}currentGroup = ${'$'}args[0]
            |        
            |        if (${'$'}validGroups -contains ${'$'}currentGroup) {
            |            # Это валидная группа - показать команды
            |            ${'$'}commands = switch (${'$'}currentGroup) {
            |                ${
            app.groups.joinToString("\n") { group ->
                """
                |'${group.name}' {
                |    @(
                |        ${group.commands.joinToString(",\n") { "                        '${it.name}'" }}
                |    )
                |}
                """.trimMargin()
            }
        }
                           |             default { @() }
                           |         }
                           |         ${'$'}completions = ${'$'}commands | Where-Object { ${'$'}_ -like "${'$'}wordToComplete*" }
                           |     }
                           |     else {
                           |         # Неполная группа - показать подходящие группы
                           |         ${'$'}groups = @(
                           |             ${app.groups.joinToString(",\n") { "                        '${it.name}'" }}
                           |         )
                           |         ${'$'}completions = ${'$'}groups | Where-Object { ${'$'}_ -like "${'$'}currentGroup*" }
                           |     }
                           | }
                           | else {
                           |     # Два и более аргументов - показать флаги
                           |     ${'$'}options = @(
                           |         '--help',
                           |         '-h',
                           |         '--version',
                           |         '-v'
                           |         ${
            app.groups.flatMap { group ->
                group.commands.flatMap { cmd ->
                    cmd.options.flatMap { opt ->
                        listOf(
                            ",\n                        '--${opt.long}'",
                            ",\n                        '-${opt.short}'",
                        )
                    }
                }
            }.joinToString("")
        }
                        |        )
                        |        ${'$'}completions = ${'$'}options | Where-Object { ${'$'}_ -like "${'$'}wordToComplete*" }
                        |    }
                        |    
                        |    # Вернуть результаты
                        |    ${'$'}completions | ForEach-Object {
                        |        [System.Management.Automation.CompletionResult]::new(${'$'}_, ${'$'}_, 'ParameterValue', ${'$'}_)
                        |    }
                        |}
                        |
                        |Register-ArgumentCompleter -CommandName $appName -ScriptBlock ${'$'}scriptblock
                        |Register-ArgumentCompleter -CommandName $appName.exe -ScriptBlock ${'$'}scriptblock
            """.trimMargin()
    }
}
