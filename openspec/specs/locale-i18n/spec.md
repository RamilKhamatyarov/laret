# Locale I18n Specification

## Purpose

Defines how Laret selects and persists an interface locale, validates locale tags, exposes the `locale` commands, and resolves and formats localized messages with English fallback.

## Requirements

### Requirement: Locale resolution order

At startup the active locale MUST be resolved from the first available source: a non-blank `LARET_LOCALE` environment variable, then a non-empty tag persisted in `~/.laret/locale`, then the system default locale. Unreadable or empty persisted content MUST be treated as absent.

#### Scenario: Environment wins

- **WHEN** `LARET_LOCALE=es` is set and a different locale is persisted
- **THEN** Spanish is the active locale

#### Scenario: Persisted value used

- **WHEN** `LARET_LOCALE` is unset and `~/.laret/locale` contains `es`
- **THEN** Spanish is the active locale

#### Scenario: No preference

- **WHEN** no environment value or persisted file exists
- **THEN** the system default locale is active

### Requirement: Locale tag parsing

Tags MUST be split on `_` or `-`. A single part MUST be treated as a language; two parts as language and region; three or more parts as language, region, and a variant formed by joining the remaining parts with underscores. The active tag MUST be reported using underscore separators.

#### Scenario: Language and region

- **WHEN** the tag `en-US` is applied
- **THEN** the active locale reports as `en_US`

#### Scenario: Language only

- **WHEN** the tag `es` is applied
- **THEN** the active locale reports as `es`

### Requirement: Locale tag validation

`locale set` MUST reject a blank tag. The language part MUST be two or three letters. If a region part is present it MUST be empty or consist of two or three letters or digits. Invalid tags MUST produce an error, MUST NOT change the active locale, and MUST NOT write the preference file.

#### Scenario: Valid tag

- **WHEN** the user runs `locale set fr_FR`
- **THEN** the tag is accepted and applied

#### Scenario: Invalid language length

- **WHEN** the user runs `locale set e`
- **THEN** an error states that the tag is invalid and the locale is unchanged

### Requirement: Showing the active locale

`locale show` MUST print the active locale tag. With `--verbose` (`-v`) it MUST also print the source as `LARET_LOCALE (env)`, `~/.laret/locale (persisted)`, or `system default`. The `i18n locale` command MUST print only the active tag.

#### Scenario: Verbose environment source

- **WHEN** `LARET_LOCALE` is set and `locale show --verbose` runs
- **THEN** the output includes `Source: LARET_LOCALE (env)`

### Requirement: Persisting a locale

`locale set <tag>` MUST apply the locale to the current session and persist the tag as plain text in `~/.laret/locale`, creating `~/.laret` if needed. Success MUST print a localized confirmation containing the tag. A failure to write the file MUST NOT fail the command; the locale still applies for the session.

#### Scenario: Persisted across sessions

- **WHEN** `locale set es` succeeds and Laret starts again without `LARET_LOCALE`
- **THEN** Spanish is active from the persisted file

#### Scenario: Preference directory missing

- **WHEN** `~/.laret` does not exist
- **THEN** it is created before the locale file is written

### Requirement: Environment override warning

When `LARET_LOCALE` is set, `locale set` MUST still apply and persist the tag but MUST warn on standard error that the environment variable overrides the persisted locale and must be unset for the change to apply across sessions.

#### Scenario: Set while environment overrides

- **WHEN** `LARET_LOCALE=en` is set and the user runs `locale set es`
- **THEN** the file is written, the active locale remains controlled by the environment, and the warning names `LARET_LOCALE`

### Requirement: Resetting the locale

`locale reset` MUST remove `~/.laret/locale`, then apply the locale that remains after removal: `LARET_LOCALE` when set, otherwise the system default. It MUST print a localized confirmation containing the resulting locale. A missing preference file MUST NOT be an error; any other deletion failure MUST print a warning and still recompute the active locale.

#### Scenario: Reset with environment override

- **WHEN** `LARET_LOCALE=es` is set and `locale reset` runs
- **THEN** the preference file is removed and Spanish remains active from the environment

#### Scenario: Reset without preference

- **WHEN** no preference file exists and `locale reset` runs
- **THEN** the command succeeds without an error and reports the system default

### Requirement: Message lookup and English fallback

Message lookup MUST consult the bundle for the active locale, then the English bundle. A key missing from both MUST be returned as the key text itself rather than throwing. A key present only in English MUST render the English message.

#### Scenario: Missing translation falls back

- **WHEN** a key exists in English but not in the active locale's bundle
- **THEN** the English message is printed

#### Scenario: Unknown key

- **WHEN** a message is requested for a key defined in no bundle
- **THEN** the key itself is printed

### Requirement: Message argument formatting

When arguments are supplied, the message pattern MUST be formatted with the active locale, substituting positional placeholders such as `{0}` and `{1}`. With no arguments the pattern MUST be returned unchanged. If the pattern cannot be formatted, the raw pattern MUST be returned instead of failing.

#### Scenario: Positional arguments

- **WHEN** a message `File created: {0}` is rendered with path `out.txt`
- **THEN** the output is `File created: out.txt`

#### Scenario: Invalid pattern

- **WHEN** a message pattern contains malformed formatting syntax
- **THEN** the unformatted pattern is printed and no exception escapes

### Requirement: Localized user-visible surfaces

Built-in validation failures for arguments, options, rules, and cross-field constraints MUST be localized, as MUST the `Constraints:` section of command help and the `locale` command confirmations, errors, and warnings. Application messages rendered through message keys, such as the localized greeting, MUST use the active locale. Framework text without a message key MUST be emitted unchanged rather than translated.

#### Scenario: Localized validation error

- **WHEN** `LARET_LOCALE=es` is set and a numeric validation fails
- **THEN** the failure text is Spanish while the offending argument name is preserved

#### Scenario: Localized greeting

- **WHEN** Spanish is active and the `i18n hello` command runs
- **THEN** the Spanish greeting is printed

#### Scenario: Untranslated framework text

- **WHEN** a framework message has no bundle key
- **THEN** it is printed in its original form
