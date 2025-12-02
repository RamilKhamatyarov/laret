package com.rkhamatyarov.laret.output

/*
* Interface for pluggable output formatting strategies
*/
interface OutputStrategy {
    fun <T> render(data: T): String

    val name: String
}
