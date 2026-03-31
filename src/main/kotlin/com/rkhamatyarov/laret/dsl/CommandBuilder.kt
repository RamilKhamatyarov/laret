package com.rkhamatyarov.laret.dsl

import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.Option

/**
 * DSL builder for a single [Command].
 *
 * ```kotlin
 * command(name = "create", description = "Create a file") {
 *     aliases("c", "new")
 *     argument("path", "File path")
 *     option("f", "force", "Overwrite if exists", "", false, persistent = true)
 *     action { ctx -> ... }
 * }
 * ```
 */
class CommandBuilder(val name: String, val description: String = "") {
    private val arguments = mutableListOf<Argument>()
    private val options = mutableListOf<Option>()
    private val aliases = mutableListOf<String>()
    private var actionBlock: (CommandContext) -> Unit = {}

    /**
     * Register one or more alternative names for this command.
     * All aliases are matched case-sensitively at runtime.
     */
    fun aliases(vararg names: String) {
        aliases.addAll(names)
    }

    /** Define a positional argument. */
    fun argument(
        name: String,
        description: String = "",
        required: Boolean = true,
        optional: Boolean = false,
        default: String = ""
    ) {
        arguments.add(Argument(name, description, required, optional, default))
    }

    /**
     * Define a command-line option / flag.
     *
     * @param short       Single-letter short form (`"f"` → `-f`).
     * @param long        Long form (`"force"` → `--force`).
     * @param description Help text.
     * @param default     Compile-time default value.
     * @param takesValue  True if the flag accepts a value; false for boolean toggles.
     * @param persistent  When **true**, a missing CLI value is looked up in
     *                    [com.rkhamatyarov.laret.config.model.AppConfig.flags]
     *                    using the key hierarchy `<group>.<cmd>.<flag>`,
     *                    `<cmd>.<flag>`, or `global.<flag>` before falling back
     *                    to [default].  Declare the override in `.laret.yml`:
     *                    ```yaml
     *                    flags:
     *                      file:
     *                        create:
     *                          force: true
     *                    ```
     */
    fun option(
        short: String,
        long: String,
        description: String = "",
        default: String = "",
        takesValue: Boolean = true,
        persistent: Boolean = false
    ) {
        options.add(Option(short, long, description, default, takesValue, persistent))
    }

    /** Define the action executed when this command is invoked. */
    fun action(block: (CommandContext) -> Unit) {
        actionBlock = block
    }

    fun build(): Command = Command(name, description, arguments, options, aliases.toList(), actionBlock)
}
