package com.rkhamatyarov.laret.model

/**
 * A named command-line option (flag).
 *
 * @param short       Single-character short flag (e.g. `"f"` → `-f`).
 * @param long        Full flag name (e.g. `"force"` → `--force`).
 * @param description Help text shown in `--help` output.
 * @param default     Value used when the flag is absent from the command line
 *                    and no config-file override is present.
 * @param takesValue  True if the flag accepts a value; false for boolean toggles.
 * @param persistent  When true, a missing CLI value is looked up in
 *                    [com.rkhamatyarov.laret.config.model.AppConfig.flags]
 *                    before falling back to [default].  Precedence order:
 *                    CLI > config file > compile-time default.
 */
data class Option(
    val short: String,
    val long: String,
    val description: String = "",
    val default: String = "",
    val takesValue: Boolean = true,
    val persistent: Boolean = false,
)
