# file-diff Specification

## Purpose

Defines `diff run`, the line-oriented file comparison command: its arguments and options, the unified, plain, and JSON renderings, hunk and line-number shapes, binary detection, identical and oversized inputs, and error reporting.

## Requirements

### Requirement: Invocation and required inputs

`diff run` MUST accept two required path arguments, the original file and the modified file, in that order. When either is missing the command MUST report which argument is missing, MUST print usage, MUST emit no diff, and MUST exit with status 1.

#### Scenario: Both paths supplied

- **WHEN** `diff run old.txt new.txt` is invoked with two readable files
- **THEN** the comparison is rendered in the selected format

#### Scenario: Missing argument

- **WHEN** `diff run old.txt` is invoked without the second path
- **THEN** an error naming `new-file` is reported together with usage, and the invocation exits with status 1

### Requirement: Comparison options

`--format`/`-f` MUST select `unified` (the default), `plain`, or `json`, matched case-insensitively. `--ignore-whitespace`/`-w` MUST compare lines after removing leading and trailing whitespace while still rendering the original text. `--context`/`-c` MUST set how many unchanged lines surround each change and defaults to 3.

#### Scenario: Whitespace-only change ignored

- **WHEN** two files differ only by trailing spaces and `-w` is passed
- **THEN** the files are reported as identical

#### Scenario: Whitespace change reported without the flag

- **WHEN** two files differ only by trailing spaces and `-w` is not passed
- **THEN** the whitespace difference is reported as a removal and an addition

#### Scenario: Zero context lines

- **WHEN** `-c 0` is passed
- **THEN** each hunk contains only the changed lines

### Requirement: Unified output

The default format MUST write `--- <oldPath>` and `+++ <newPath>` header lines, then one `@@ -<oldStart>[,<oldCount>] +<newStart>[,<newCount>] @@` header per hunk, then the hunk lines prefixed with a space (context), `-` (removed), or `+` (added). A count of 1 MUST omit the `,1` suffix. Identical files MUST produce no output.

#### Scenario: Single changed line

- **WHEN** one line in the middle of a file changes and `-c 0` is used
- **THEN** the header reads `@@ -3 +3 @@` followed by the removed and added lines

#### Scenario: Context included

- **WHEN** a three-line file with default context has its middle line changed
- **THEN** the header reads `@@ -1,3 +1,3 @@` and the hunk contains the context lines

#### Scenario: Identical files print nothing

- **WHEN** two identical files are compared in unified format
- **THEN** nothing is written to stdout

### Requirement: Plain output

Plain output MUST write the same `---`/`+++` headers, then lines prefixed with two spaces (context), `- ` (removed), or `+ ` (added), with no `@@` headers, and MUST separate non-adjacent hunks with a `...` line. Identical files MUST produce `Files are identical: <oldPath>`.

#### Scenario: Marker prefixes

- **WHEN** a line is replaced in plain format
- **THEN** the removed line appears as `- <old text>` and the added line as `+ <new text>`, with context as `  <text>`

#### Scenario: Hunks separated

- **WHEN** two distant regions change in plain format
- **THEN** a `...` line separates the two hunks

#### Scenario: Identical files in plain format

- **WHEN** identical files are compared in plain format
- **THEN** the line `Files are identical: <oldPath>` is written

### Requirement: JSON output

JSON output MUST be a pretty-printed object with the fields `oldPath`, `newPath`, `identical`, `oldBinary`, `newBinary`, and `hunks`. Each hunk MUST carry `oldStart`, `oldCount`, `newStart`, `newCount`, and `lines`; each line MUST carry `type` (`CONTEXT`, `ADD`, or `REMOVE`), `content`, `oldLineNo`, and `newLineNo`, where the number that does not apply to the line type is 0.

#### Scenario: Removed and added lines carry one side only

- **WHEN** a line is replaced in JSON format
- **THEN** the `REMOVE` entry has `newLineNo` 0 and the `ADD` entry has `oldLineNo` 0

#### Scenario: Identical JSON envelope

- **WHEN** identical files are compared in JSON format
- **THEN** `identical` is true and `hunks` is an empty array

#### Scenario: Binary JSON envelope

- **WHEN** either file is binary and JSON format is used
- **THEN** the binary flags are set, `identical` is false, and `hunks` is an empty array

### Requirement: Line numbering and hunk grouping

Line numbers MUST be 1-based per side and MUST refer to positions in the original and modified files. Each hunk MUST contain the requested context lines around its change, and change regions whose context windows touch or overlap MUST be merged into one hunk. A side with no lines MUST report start 0 and count 0 for that side.

#### Scenario: Pure insertion hunk

- **WHEN** a line is inserted into `a`/`c` with `-c 0`
- **THEN** the hunk header reads `@@ -0,0 +2 @@` and the hunk contains the added line

#### Scenario: Appended line

- **WHEN** a line is appended to a one-line file with default context
- **THEN** the hunk header reads `@@ -1 +1,2 @@`

#### Scenario: Overlapping context merges

- **WHEN** two changes are close enough that their context windows touch
- **THEN** both changes appear in a single hunk

### Requirement: Binary detection and reporting

A file MUST be classified as binary when a NUL byte occurs within its first 8 KiB. When either side is binary no line hunks MUST be produced, and unified output MUST state `Binary files <old> and <new> differ`, `Binary file <old> and text file <new> differ`, or `Text file <old> and binary file <new> differ`. Plain output MUST state `Binary files differ: <old> vs <new>`.

#### Scenario: Both files binary

- **WHEN** two files containing NUL bytes are compared in unified format
- **THEN** the single line `Binary files <old> and <new> differ` is written

#### Scenario: One side binary

- **WHEN** the original contains a NUL byte and the modified file does not
- **THEN** the line `Binary file <old> and text file <new> differ` is written

#### Scenario: Plain binary message

- **WHEN** a binary file is compared in plain format
- **THEN** the line `Binary files differ: <old> vs <new>` is written

### Requirement: Identical files

When the compared line sequences are equal, including after whitespace trimming, the result MUST report identical files with no hunks. Unified output MUST be empty, plain output MUST name the file as identical, and JSON MUST report `identical` true.

#### Scenario: Equal content

- **WHEN** both files contain the same lines
- **THEN** the result is identical with zero hunks

#### Scenario: Equal after whitespace trimming

- **WHEN** lines differ only in leading or trailing whitespace and whitespace is ignored
- **THEN** the result is identical with zero hunks

### Requirement: Comparison algorithm and large-input fallback

Differences MUST be computed as a line-level longest-common-subsequence so unchanged lines become context rather than a full replacement. When the product of the old and new line counts exceeds 4,000,000 cells, the comparison MUST fall back to treating every old line as removed and every new line as added instead of attempting the full comparison.

#### Scenario: Minimal diff within the threshold

- **WHEN** one line of a five-line file changes
- **THEN** only that line is reported as removed and added, with the other lines as context

#### Scenario: Oversized input falls back

- **WHEN** the two files are large enough that old line count times new line count exceeds 4,000,000
- **THEN** all old lines are reported as removed and all new lines as added in one hunk

### Requirement: Malformed and unreadable input

A file containing invalid UTF-8 MUST be treated as binary rather than causing a failure, with the corresponding binary flag set and a binary message reported. A path that does not exist MUST be reported as `Error: file not found: <path>` and MUST NOT produce a render.

#### Scenario: Invalid UTF-8 handled as binary

- **WHEN** a file contains bytes that cannot be decoded as UTF-8 and no NUL byte
- **THEN** it is treated as binary and a binary message is reported instead of an error trace

#### Scenario: Missing file

- **WHEN** the original path does not exist
- **THEN** the message `Error: file not found: <path>` is reported

### Requirement: Exit status

`diff run` MUST exit with status 0 whenever a result is rendered, including when the files differ, are identical, or are binary. Reported input and format errors MUST NOT change the exit status; only missing required arguments produce a non-zero status.

#### Scenario: Differences exit zero

- **WHEN** the files differ
- **THEN** the diff is rendered and the invocation exits with status 0

#### Scenario: Unknown format exits zero

- **WHEN** an unsupported `--format` value is passed
- **THEN** the message `Error: unknown diff format: <value> (expected unified, plain, json)` is reported and the invocation exits with status 0

#### Scenario: Missing argument exits non-zero

- **WHEN** a required path argument is omitted
- **THEN** the invocation exits with status 1
