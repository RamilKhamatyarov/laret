package ${packageName}.commands

import com.rkhamatyarov.laret.dsl.GroupBuilder

/**
 * Hello demo command. Demonstrates the 12-factor config precedence:
 *
 *   1. Default value (compiled-in)
 *   2. Config file (loaded by Laret's ConfigRegistry)
 *   3. Environment variable
 *   4. CLI flag (highest priority)
 *
 * Try:
 *   ./${appName} hello run --name Alice
 *   ${appNameUpper}_GREETING_NAME=Bob ./${appName} hello run
 */
fun GroupBuilder.helloCommand() {
    command(name = "run", description = "Print a greeting using resolved config") {
        option("n", "name", "Name to greet", "", optional = true)
        option("p", "print-config", "Show resolved config sources", "", optional = false)

        action { ctx ->
            val flagName = ctx.option("name")
            val envName = System.getenv("${appNameUpper}_GREETING_NAME") ?: ""
            val fileName = ctx.config.get("greeting.name")?.toString() ?: ""
            val defaultName = "World"

            val resolved = listOf(flagName, envName, fileName)
                .firstOrNull { it.isNotBlank() } ?: defaultName

            println("Hello, $resolved!")

            if (ctx.optionBool("print-config")) {
                println("--- precedence trace ---")
                println("cli      = ${flagName.ifBlank { "<unset>" }}")
                println("env      = ${envName.ifBlank { "<unset>" }}")
                println("file     = ${fileName.ifBlank { "<unset>" }}")
                println("default  = $defaultName")
                println("resolved = $resolved")
            }
        }
    }
}
