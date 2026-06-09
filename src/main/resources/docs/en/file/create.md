---
title: Create a file
summary: Create a new file, optionally overwriting an existing one.
synopsis: laret file create <path> [--content <text>] [--force]
examples:
  - laret file create notes.txt --content "hello"
  - echo "piped" | laret file create notes.txt
  - laret file create notes.txt --force
see_also:
  - laret-file-delete
  - laret-file-read
---
Creates a file at the given `<path>`. The contents come from `--content`, or
from standard input when no content flag is supplied.

If the target already exists the command fails unless `--force` is given. The
operation is undoable via `laret undo`.
