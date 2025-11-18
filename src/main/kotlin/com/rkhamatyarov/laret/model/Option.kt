package com.rkhamatyarov.laret.model

data class Option(
    val short: String,
    val long: String,
    val description: String = "",
    val default: String = "",
    val takesValue: Boolean = true,
)
