# mcp-and-schema Specification

## Purpose

Defines how a Laret application exposes its commands as Model Context Protocol tools over standard input/output and how it exports the same command tree as OpenAI, Anthropic, or Laret JSON schemas.

## Requirements

### Requirement: Stdio server session

`mcp serve --transport stdio` MUST read one JSON-RPC message per line from standard input and write exactly one JSON response line per non-blank input line to standard output, flushing after each response. It MUST NOT print an interactive prompt or any non-JSON text on standard output; startup and shutdown notices MUST go to standard error. Blank input lines MUST be ignored, and end of input MUST end the session.

#### Scenario: No prompt on startup

- **WHEN** the server starts and no request has been sent yet
- **THEN** standard output carries no banner or prompt and the process waits for input

#### Scenario: One response per request

- **WHEN** a single `tools/list` request line is piped into the server
- **THEN** exactly one JSON response line is written and the process exits at end of input

### Requirement: Transport selection

The `--transport` option MUST default to `stdio` and MUST accept only that value. Any other value MUST be reported on standard error and MUST NOT start the request loop. The `--port` option MUST be accepted and MUST have no effect.

#### Scenario: Unsupported transport

- **WHEN** the user runs `app mcp serve --transport http`
- **THEN** an error naming the unsupported transport is printed to standard error and no server loop starts

#### Scenario: Port is inert

- **WHEN** the user runs `app mcp serve --port 8080`
- **THEN** the server still serves over standard input/output as if the option were absent

### Requirement: Initialize handshake

An `initialize` request MUST return a result with protocol version `2024-11-05`, a tools capability declaring `listChanged` as `false`, and server info whose name is `<app>-mcp` and whose version is the application version. The request id MUST be echoed unchanged in the response.

#### Scenario: Client initialization

- **WHEN** a client sends `initialize` with id `1`
- **THEN** the response carries the same id and the advertised protocol version, capabilities, and server info

### Requirement: Tool listing and visibility

`tools/list` MUST return one tool per command of every visible group, in declaration order. Each tool MUST have the name `<app>.<group>.<command>`, a description equal to the declared command description or a generated sentence when that description is blank, an input schema, and an impact profile. Hidden groups MUST be absent from tool listings and from every schema export.

#### Scenario: Tool naming

- **WHEN** the application `laret` declares group `file` with command `create`
- **THEN** the listing contains a tool named `laret.file.create`

#### Scenario: Hidden group is not listed

- **WHEN** a group is marked hidden
- **THEN** its commands are absent from `tools/list` and from exported schemas

### Requirement: Tool call argument mapping

`tools/call` MUST resolve the requested tool by its full `<app>.<group>.<command>` name or by its `<group>.<command>` short name, then rebuild a command invocation: the group and command names, each supplied positional value in declared order, `--<long> <value>` for every supplied value-taking option, and `--<long>` for every boolean option whose value is true. An omitted `arguments` member MUST be treated as an empty object.

#### Scenario: Short tool name accepted

- **WHEN** a client calls `file.create` rather than `laret.file.create`
- **THEN** the same command runs

#### Scenario: Boolean option mapping

- **WHEN** a value-less option is sent as `true` and another declared option is omitted
- **THEN** the invocation contains the flag for the true option and nothing for the omitted option

### Requirement: Tool call results

A tool call that runs the command successfully MUST return `content` holding a single text item with the command's combined standard output and standard error, trimmed. Any non-zero command exit code, or a failure thrown while running the command, MUST become an error response carrying the captured output, or a message naming the exit code when that output is blank.

#### Scenario: Successful call

- **WHEN** a tool call targets a command that exits with code 0 and prints text
- **THEN** the result contains that text as a single `text` content item

#### Scenario: Failing command

- **WHEN** the underlying command exits with a non-zero code
- **THEN** the response is an error whose message carries the command output

### Requirement: JSON-RPC error handling

Requests MUST be answered with the established codes: `-32600` for a malformed or non-`2.0` request, `-32601` for an unknown method, `-32602` for invalid parameters, and `-32000` for a failed command. `-32602` MUST cover a missing or non-object `params`, a missing tool name, a non-object `arguments`, an unknown tool, and missing required arguments. Every error MUST use the standard error envelope with `code` and `message`.

#### Scenario: Missing required argument

- **WHEN** a tool call omits a required positional argument
- **THEN** the error code is `-32602` and the message names the missing arguments

#### Scenario: Unknown tool

- **WHEN** a tool call names a tool that does not exist
- **THEN** the error code is `-32602` and the message names the unknown tool

#### Scenario: Malformed JSON

- **WHEN** an input line is not parseable JSON
- **THEN** the response is a `-32600` error with a null id

### Requirement: Input schema from declared types

A tool input schema MUST be a JSON Schema object whose properties are keyed by option long name and argument name. A property type MUST be the declared scalar type (`string`, `integer`, `number`, or `boolean`) for value-taking options and arguments, and `boolean` for value-less options. Blank descriptions MUST be replaced by generated text, a non-blank default MUST be emitted as a JSON value typed like its property, and `required` MUST list required non-optional argument names.

#### Scenario: Typed argument

- **WHEN** an integer argument is declared
- **THEN** its property in the input schema has type `integer`

#### Scenario: Boolean flag property

- **WHEN** a value-less option is declared
- **THEN** its property has type `boolean`

#### Scenario: Required list

- **WHEN** a command declares two required arguments and one optional argument
- **THEN** `required` contains exactly the two required argument names

### Requirement: Impact profile metadata

Every tool and every neutral contract entry MUST carry an impact profile with a category and a capability-needs list. The category MUST be one of `no_effect`, `read_only`, `local_mutation`, `external_interaction`, or `undeclared` when the author declared none. The capability-needs list MUST always be empty, and both fields MUST be descriptive only and MUST NOT change execution. See [SI-1](../../adr/contract-first-command-model.md).

#### Scenario: Undeclared category

- **WHEN** a command declares no impact category
- **THEN** its exported profile reports `undeclared`

#### Scenario: Metadata does not gate execution

- **WHEN** a command declaring the read-only category is called over MCP
- **THEN** it runs without any additional check or refusal, and its capability-needs list is empty

### Requirement: Schema export command

`schema export` MUST accept `--format` with the values `openai`, `anthropic`, or `laret`, defaulting to `openai`. With `--output <file>` the JSON MUST be written to that file and nothing printed; without it the JSON MUST be printed to standard output. An unrecognised format MUST be reported on standard error and MUST NOT write a file.

#### Scenario: Default dialect

- **WHEN** the user runs `app schema export` without `--format`
- **THEN** the OpenAI dialect JSON is printed to standard output

#### Scenario: File output is silent

- **WHEN** the user runs `app schema export --format laret --output schema.json`
- **THEN** the JSON is written to `schema.json` and nothing is printed to standard output

#### Scenario: Unknown dialect

- **WHEN** the user passes `--format gemini`
- **THEN** an error naming the unsupported format is printed and no file is written

### Requirement: Schema dialect shapes

Each dialect MUST be a single JSON object. The `openai` dialect MUST expose a `functions` array of function objects with a name, description, and parameters schema. The `anthropic` dialect MUST expose a `tools` array with a name, description, and input schema. The `laret` dialect MUST expose a contract version of `1`, the application name and version, and a `commands` array whose entries carry the dotted tool name, description, typed input schema, and impact profile.

#### Scenario: OpenAI envelope

- **WHEN** the OpenAI dialect is exported
- **THEN** each entry under `functions` declares the function type and nests name, description, and parameters

#### Scenario: Anthropic envelope

- **WHEN** the Anthropic dialect is exported
- **THEN** each entry under `tools` carries `input_schema` in place of OpenAI's `parameters`

#### Scenario: Exported names

- **WHEN** any dialect is exported for group `file` and command `create` in application `laret`
- **THEN** the provider dialects use the name `laret_file_create` and the Laret contract uses `laret.file.create`

### Requirement: Security scope

The server MUST run with the invoking user's permissions and MUST NOT authenticate, authorize, sandbox, or refuse a call because of its impact category: every command reachable from the CLI is invocable by any connected agent. The transport MUST remain standard input/output only, with no network listener opened.

#### Scenario: Full command reachability

- **WHEN** a client lists and calls tools
- **THEN** every command of every visible group can be invoked, subject only to its own argument checks

#### Scenario: No network surface

- **WHEN** the server is running over stdio
- **THEN** it binds no port and accepts requests only from its standard input
