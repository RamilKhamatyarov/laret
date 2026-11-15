# documentation-generation Specification

## Purpose

Defines how Laret renders Groff man pages and Markdown or man documentation for an application's commands, how it scaffolds prose for authors, and how it validates links and anchors before generated documentation ships.

## Requirements

### Requirement: Man page command output

`completion man` MUST print Groff man(7) source for the application. Without targeting options it MUST render one page per command of every group, separated by a blank line. `--group <name>` with `--command <name>` MUST render that single command's page; a group or command that does not exist MUST be reported with no page produced. `--output <file>` MUST write the source to that file instead of printing it, creating any missing parent directory.

#### Scenario: Overview to standard output

- **WHEN** the user runs `app completion man` with no targeting options
- **THEN** one man page per command is printed, pages separated by a blank line, and no file is created

#### Scenario: Single command targeted

- **WHEN** the user runs `app completion man --group file --command create`
- **THEN** only the `create` page of group `file` is printed

#### Scenario: Unknown group

- **WHEN** the user passes a group name that does not exist
- **THEN** an error naming that group is printed and no page is produced

### Requirement: Man page structure and escaping

Every page MUST start with a `.TH` line giving the upper-cased `<app>-<group>-<command>` name (or `<app>-<command>` without a group), section `1`, an empty date, the source `<app> <version>`, and the manual `User Commands`, then the sections NAME, SYNOPSIS, DESCRIPTION, OPTIONS, EXAMPLES, and SEE ALSO in order. OPTIONS MUST be omitted when no options are declared, and literal text MUST be Groff-escaped.

#### Scenario: Section order

- **WHEN** a page is generated for a command with options
- **THEN** the six section headings appear in the documented order

#### Scenario: Options omitted

- **WHEN** a command declares no options
- **THEN** the page contains no OPTIONS section

#### Scenario: Special characters escaped

- **WHEN** a description contains a hyphen, an apostrophe, or begins with a period
- **THEN** the hyphen renders as `\-` and the other characters are protected so the line is not read as a macro

### Requirement: Synopsis and examples sections

The SYNOPSIS line MUST be the application, optional group, and command names, then `<name>` for each required argument and `[<name>]` for each optional argument, then `[--<long> <value>]` for each value-taking option and `[--<long>]` otherwise. EXAMPLES MUST contain a basic usage line and, when the command declares arguments, a second example line listing every argument in angle brackets.

#### Scenario: Required and optional arguments

- **WHEN** a command declares one required and one optional argument
- **THEN** the synopsis shows the first as `<name>` and the second as `[<name>]`

#### Scenario: Value-taking option

- **WHEN** a command declares an option that takes a value
- **THEN** the synopsis shows `[--<long> <value>]` for it

### Requirement: Markdown documentation layout

`doc generate --format md` MUST write one page per documented command at `{lang}/{group}/{command}.md`, a group index at `{lang}/{group}/index.md`, and a root index at `{lang}/index.md`, plus a single `mkdocs.yml` whose navigation mirrors the tree. `--lang all` MUST emit every supported language. The command MUST report each written path and the total number of files.

#### Scenario: Page tree

- **WHEN** documentation is generated for group `file` with command `create` in language `en`
- **THEN** `en/file/create.md`, `en/file/index.md`, `en/index.md`, and `mkdocs.yml` are written

#### Scenario: All languages

- **WHEN** the user passes `--lang all`
- **THEN** Markdown is generated for each supported language

### Requirement: Markdown page contents

Each command page MUST begin with a level-1 title heading, then the summary, an alias list when aliases exist, and the sections Synopsis, Description, Arguments, Options, Examples, and See Also as applicable. Synopsis, examples, and descriptions MUST use the resolved prose when present and generated defaults otherwise. A hidden command documented on request MUST carry an `[INTERNAL]` marker and MUST stay out of index pages and navigation.

#### Scenario: Hidden command badge

- **WHEN** hidden commands are included with `--include-hidden`
- **THEN** their pages carry the internal marker and the indexes do not link to them

#### Scenario: Empty prose falls back

- **WHEN** no prose file exists for a command
- **THEN** the page still has a title, summary, synopsis, and description derived from the declaration

### Requirement: Man documentation from doc generate

`doc generate --format man` MUST write one flat page per documented command to `man1/<app>-<group>-<command>.1`. Man output MUST be language-neutral and produced once regardless of `--lang`; `--lang all` MUST NOT duplicate man pages. `--include-hidden` MUST include hidden commands in either format.

#### Scenario: Flat man tree

- **WHEN** man documentation is generated for `app` group `file` command `create`
- **THEN** the file is written at `man1/app-file-create.1`

#### Scenario: All languages does not multiply man pages

- **WHEN** `--format man --lang all` is requested
- **THEN** exactly one set of man pages is written

### Requirement: Prose provider contract

Prose MUST be resolved from `docs/{lang}/{group}/{command}.md` with optional YAML frontmatter keys `title`, `summary`, `synopsis`, `examples`, and `see_also`, followed by a Markdown body. Resolution MUST fall back whole-file from the requested language to English to the declared command description. Missing fields MUST default (title to the command name, summary to the declared description), and malformed frontmatter MUST be treated as a missing file rather than an error.

#### Scenario: Language fallback

- **WHEN** no Spanish prose exists for a command but English prose does
- **THEN** the English prose is used for the Spanish page

#### Scenario: Malformed frontmatter

- **WHEN** a prose file's frontmatter cannot be parsed
- **THEN** the file is skipped and the next fallback supplies the prose

### Requirement: Prose scaffolding

`doc scaffold` MUST create a skeleton at `{lang}/{group}/{command}.md` under the output directory for every documented command that has no file yet, creating directories as needed. It MUST never overwrite an existing file and MUST report only the files it created. The skeleton MUST contain frontmatter with a title, a summary, a generated synopsis usage line, empty examples and see-also lists, and a TODO body comment.

#### Scenario: New command gets a skeleton

- **WHEN** a command has no prose file and scaffolding runs
- **THEN** a skeleton file is created and reported

#### Scenario: Existing prose preserved

- **WHEN** a prose file already exists for a command
- **THEN** scaffolding leaves it byte-identical and does not report it

### Requirement: Standalone guide scaffolding

`doc guide <name>` MUST normalise the name to a kebab-case slug and write `{lang}/guides/<slug>.md`, or `guides/<slug>.md` when `--lang` is blank. The page MUST contain a frontmatter title derived from the slug, a TODO summary, and a TODO body. An existing guide MUST be left untouched and reported as existing, and a blank name MUST be rejected with usage text.

#### Scenario: Slug normalisation

- **WHEN** the user runs `doc guide "Quick Start"`
- **THEN** the file is written as `guides/quick-start.md`

#### Scenario: Existing guide is preserved

- **WHEN** the target guide file already exists
- **THEN** its contents are unchanged and the output says the guide already exists

### Requirement: Landing page from README

`doc index` MUST read the source README (default `README.md`), drop a leading level-1 heading together with one following blank line, and write `index.md` under the output directory, or `{lang}/index.md` when `--lang` is set, prefixed with a frontmatter title. The source README MUST NOT be modified, and a missing README MUST be reported with no file written.

#### Scenario: README heading dropped

- **WHEN** the README starts with a level-1 heading followed by a blank line
- **THEN** the generated index page omits that heading and keeps the rest

#### Scenario: README untouched

- **WHEN** `doc index` runs
- **THEN** the source README file is unchanged

#### Scenario: Missing README

- **WHEN** the README path does not exist
- **THEN** an error naming that path is printed and no index is written

### Requirement: Link and anchor validation

Rendered Markdown MUST be validated: an internal `.md` link MUST resolve, relative to the linking page, to a page in the generated set and MUST NOT escape the output root; a `#anchor` MUST match a same-page heading slug (lower-cased, punctuation stripped, whitespace collapsed to `-`). External links, cross-file anchors, and links inside code spans MUST be left unvalidated.

#### Scenario: Broken internal link

- **WHEN** a page links to `missing.md`, or a relative link climbs above the output root
- **THEN** validation reports a broken link naming that page

#### Scenario: Broken local anchor

- **WHEN** a page links to `#nowhere` and no heading produces that slug
- **THEN** validation reports a broken anchor naming the anchor

#### Scenario: External link ignored

- **WHEN** a page links to an `https://` or `mailto:` address
- **THEN** validation reports nothing for that link

### Requirement: Validation reporting and exit codes

Validation MUST run on every generation. In the default mode every problem MUST be reported on standard error as a warning and generation MUST continue, exiting with code 0. With `--strict` the run MUST additionally report missing prose files, broken see-also targets, and orphaned prose files, and MUST fail with exit code 1 without writing any file.

#### Scenario: Non-strict warnings

- **WHEN** a generated page contains a broken link and `--strict` is absent
- **THEN** the warning is printed, the files are still written, and the exit code is 0

#### Scenario: Strict failure

- **WHEN** `--strict` is set and a command has no prose file
- **THEN** the missing prose is reported and the process exits with code 1 without writing documentation
