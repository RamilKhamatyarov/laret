# output-formats Specification

## Purpose

Defines the built-in output strategies Laret offers for rendering structured data, how each renders maps, lists, and scalars, and how custom strategies are named, selected, and invoked.

## Requirements

### Requirement: Built-in output formats

Laret MUST provide five built-in output strategies with the exact names `plain`, `json`, `yaml`, `toml`, and `table`. Those names MUST be the values used to select a strategy, and each strategy MUST expose its name together with a rendering operation that accepts arbitrary data and returns text.

#### Scenario: Every built-in name resolves

- **WHEN** a caller requests `plain`, `json`, `yaml`, `toml`, or `table`
- **THEN** the matching built-in strategy is returned

#### Scenario: Names are the selection keys

- **WHEN** a format value is documented or passed as `yaml`
- **THEN** it selects the YAML strategy rather than a display label

### Requirement: Strategy selection and fallback

Selection MUST be by exact, case-sensitive name. An unrecognized name MUST fall back to `plain` rather than failing or producing an error, so a user-supplied format value can never break rendering.

#### Scenario: Unknown format falls back to plain

- **WHEN** the requested format is `bogus`
- **THEN** the data is rendered by the `plain` strategy and the command succeeds

#### Scenario: Case matters

- **WHEN** the requested format is `JSON`
- **THEN** it does not match the built-in `json` name and falls back to `plain`

### Requirement: Plain rendering

`plain` MUST render data by the data's own string form and MUST NOT add quoting, indentation, headers, or structural punctuation. A string renders as itself, and a list or map renders as its string form.

#### Scenario: String data

- **WHEN** the string `hello` is rendered with `plain`
- **THEN** the output is exactly `hello`

#### Scenario: Structured data

- **WHEN** a list of maps is rendered with `plain`
- **THEN** the output is the list's string form on a single line with no added formatting

### Requirement: JSON rendering

`json` MUST render data as a single-line JSON document without pretty-printing whitespace. Map keys MUST keep insertion order, list elements MUST keep order, strings MUST be JSON-escaped, and numbers, booleans, and null MUST retain their JSON types.

#### Scenario: List of maps

- **WHEN** the list `[{"name":"a.txt","size":12}]` is rendered with `json`
- **THEN** the output is `[{"name":"a.txt","size":12}]` with no spaces between tokens

#### Scenario: Scalar string

- **WHEN** the string `hi` is rendered with `json`
- **THEN** the output is the quoted JSON string `"hi"`

#### Scenario: Key order is preserved

- **WHEN** a map is rendered whose keys were inserted as `name` then `size`
- **THEN** the JSON object lists `name` before `size`

### Requirement: YAML rendering

`yaml` MUST render data as a block-style YAML document beginning with the `---` document marker. Maps MUST render as `key: value` entries, lists as `- item` sequences, and nesting MUST be expressed by indentation rather than braces.

#### Scenario: List of maps

- **WHEN** a list of maps is rendered with `yaml`
- **THEN** the output starts with `---` and continues with `- name: ...` entries

#### Scenario: Nested map

- **WHEN** a map containing a nested map is rendered with `yaml`
- **THEN** the nested keys appear indented under the parent key

### Requirement: TOML rendering

`toml` MUST render data as a TOML document. Top-level keys MUST become `key = value` entries, nested maps MUST become TOML tables, and lists MUST become TOML arrays. Data that TOML cannot express at the document root MUST be serialized by the same serializer rather than rejected.

#### Scenario: Map of scalars

- **WHEN** a map of scalar values is rendered with `toml`
- **THEN** the output contains one `key = value` entry per key

#### Scenario: Nested map

- **WHEN** a map containing a nested map is rendered with `toml`
- **THEN** the nested map is emitted as a TOML table rather than a string

### Requirement: Table rendering

`table` MUST render a list of maps as a grid: a header row from the first element's keys in insertion order, a full border, and one row per element, with missing cells rendered empty. An empty list MUST render `No data`; a list whose first element is not a map MUST render one item per line; a map MUST render as a header row plus one value row; any other value MUST render as its string form. Table output MUST NOT contain ANSI color codes.

#### Scenario: Empty list

- **WHEN** an empty list is rendered with `table`
- **THEN** the output is exactly `No data`

#### Scenario: Missing cell

- **WHEN** one element of a list of maps lacks a key present in the first element
- **THEN** that cell is empty and the remaining columns still align

#### Scenario: Map input

- **WHEN** a single map is rendered with `table`
- **THEN** its keys form the header row and its values form the single body row

#### Scenario: No color

- **WHEN** a table is rendered while colors are supported
- **THEN** the returned text contains no escape sequences

### Requirement: Custom output strategies

A custom strategy MUST declare an exact name and a generic rendering operation that turns arbitrary data into a string, and MUST be usable with the same data shapes as a built-in. Applications select a custom strategy by matching its name to the requested format.

#### Scenario: CSV strategy

- **WHEN** an application defines a strategy named `csv` that renders a list of maps as comma-separated rows
- **THEN** asking it to render that list returns one comma-separated line per element

#### Scenario: Custom scalar rendering

- **WHEN** a custom strategy's rendering operation receives a value rather than a list
- **THEN** it decides the output for that value instead of delegating to a built-in

### Requirement: Rendering through the execution context

A command action MUST be able to render data by format name through the execution context. The context MUST resolve the name to the matching built-in strategy and MUST fall back to `plain` when no built-in matches.

#### Scenario: Context render selects JSON

- **WHEN** an action asks the context to render data as `json`
- **THEN** the result is the JSON strategy's rendering of that data

#### Scenario: Context render falls back

- **WHEN** an action asks the context to render data with an unknown name
- **THEN** the result is the plain strategy's rendering

### Requirement: The format option convention

A command that selects among the built-in strategies MUST accept the format name as the value of a `format` option, conventionally with short name `-f` and default `plain`, and MUST emit the selected strategy's rendering to the command's output.

#### Scenario: Default format

- **WHEN** a command with a `format` option runs without `--format`
- **THEN** the value `plain` is used and plain rendering is emitted

#### Scenario: Selected format

- **WHEN** the same command runs with `--format yaml`
- **THEN** the emitted output is the YAML rendering of the command's data
