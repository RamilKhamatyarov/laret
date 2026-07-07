package com.rkhamatyarov.laret.completion.completers

import com.rkhamatyarov.laret.completion.Completer
import com.rkhamatyarov.laret.completion.CompletionCandidate
import com.rkhamatyarov.laret.completion.CompletionContext
import com.rkhamatyarov.laret.completion.CompletionResult
import com.rkhamatyarov.laret.model.fs.LaretFileSystem
import com.rkhamatyarov.laret.model.fs.RealFileSystem
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Completes file and directory paths by listing the parent directory of the
 * typed prefix through [fs].  The binary does the walk (not the shell), so
 * behavior is identical in bash, zsh, and PowerShell, and the completer is
 * unit-testable with a mocked [LaretFileSystem].
 *
 * Directories get a trailing `/` so a second TAB descends into them; when the
 * sole candidate is a directory the [CompletionResult.NO_SPACE] directive is
 * set so the shell does not append a space after it.
 */
class FileCompleter(private val fs: LaretFileSystem = RealFileSystem()) : Completer {

    override fun complete(context: CompletionContext): CompletionResult {
        val word = context.wordToComplete.replace('\\', '/')
        val slash = word.lastIndexOf('/')
        val dirPrefix = if (slash >= 0) word.substring(0, slash + 1) else ""
        val namePrefix = if (slash >= 0) word.substring(slash + 1) else word
        val dir: Path = if (dirPrefix.isEmpty()) Paths.get(".") else Paths.get(dirPrefix)

        if (!fs.exists(dir)) return CompletionResult(emptyList(), CompletionResult.NO_FILE_COMP)

        val candidates = fs.listFiles(dir)
            .filter { it.fileName.toString().startsWith(namePrefix) }
            .map { entry ->
                val name = entry.fileName.toString()
                if (fs.isDirectory(entry)) {
                    CompletionCandidate("$dirPrefix$name/")
                } else {
                    CompletionCandidate("$dirPrefix$name")
                }
            }

        val directive = if (candidates.size == 1 && candidates.single().value.endsWith("/")) {
            CompletionResult.NO_SPACE or CompletionResult.NO_FILE_COMP
        } else {
            CompletionResult.NO_FILE_COMP
        }
        return CompletionResult(candidates, directive)
    }
}
