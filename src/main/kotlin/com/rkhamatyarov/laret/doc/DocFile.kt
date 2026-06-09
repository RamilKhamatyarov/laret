package com.rkhamatyarov.laret.doc

/**
 * An in-memory representation of a single generated documentation artifact.
 *
 * A [DocFile] is the *output* of a [DocGenerator] and carries **no I/O**:
 * generators are pure functions that build these objects, and the caller
 * (e.g. [DocGenerateCommand]) is solely responsible for writing them to disk.
 * Keeping generation and persistence separate (SRP) lets generators be tested
 * without touching the file system.
 *
 * @param relativePath Path of the artifact relative to the output directory,
 *                     e.g. `"en/file/create.md"` or `"man1/laret-file-create.1"`.
 * @param content      Fully rendered file content.
 */
data class DocFile(val relativePath: String, val content: String)
