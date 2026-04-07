package com.rkhamatyarov.laret.model

data class Argument(
    val name: String,
    val description: String = "",
    val required: Boolean = true,
    val optional: Boolean = false,
    val default: String = "",
)
