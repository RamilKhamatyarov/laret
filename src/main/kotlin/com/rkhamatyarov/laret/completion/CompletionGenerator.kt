package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.core.CliApp

/**
 * Base interface for shell completion generators
 */
interface CompletionGenerator {
    fun generate(app: CliApp): String
}
