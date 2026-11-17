# middleware Specification

## Purpose

Defines the contract a Laret middleware obeys, how priority and registration scope determine the chain applied to a command, and how that chain is inspected at runtime.

## Requirements

### Requirement: Middleware contract

A middleware MUST receive the execution context and a continuation and MUST be able to perform work before, after, or around the remainder of the chain. Invoking the continuation MUST proceed to the next middleware or to the command action; not invoking it MUST skip the action and every later middleware.

#### Scenario: Work around the action

- **WHEN** a middleware prints before and after invoking its continuation
- **THEN** the action runs between the two messages

#### Scenario: Continuation declined

- **WHEN** a middleware returns without invoking its continuation
- **THEN** the command action and every later middleware do not run

### Requirement: Priority ordering

Each middleware MUST expose an integer priority, defaulting to 0. A lower value MUST wrap more of the chain: it enters before and exits after every middleware with a higher value. Priority MUST sort flatly across scopes, and equal priorities MUST preserve registration order. See [the priority and scoping ADR](../../adr/middleware-priority-and-scoping.md).

#### Scenario: Lower priority wraps

- **WHEN** middleware with priority `-1000` and middleware with priority `0` apply to the same command
- **THEN** the `-1000` middleware enters first and exits last

#### Scenario: Equal priorities keep declaration order

- **WHEN** two applicable middleware share the same priority
- **THEN** the one registered first enters first

### Requirement: Scope from registration site

Effective scope MUST be derived from where a middleware is registered: at application level it applies to every command, inside a group it applies to that group's commands, and inside a command it applies to that command only. A scope value declared by the middleware itself MUST be ignored at runtime.

#### Scenario: Group scope is bounded

- **WHEN** a middleware is registered inside one group's block
- **THEN** it applies to that group's commands and not to commands in other groups

#### Scenario: Command scope is bounded

- **WHEN** a middleware is registered inside one command's block
- **THEN** it applies to that command only

### Requirement: Chain resolution per command

For a selected command, the chain MUST consist of every applicable application-level, group-level, and command-level registration, ordered by ascending priority with ties in registration order. Command-level registrations MUST be keyed by both the group and the command name, so identically named commands in different groups do not share middleware. See [the middleware ADR](../../adr/middleware-priority-and-scoping.md).

#### Scenario: All three scopes combine

- **WHEN** an application-level, a group-level, and a command-level middleware all apply
- **THEN** the chain contains all three and their relative order follows priority

#### Scenario: Repeated command names stay separate

- **WHEN** two groups each declare a command named `list` and only one is given command-scoped middleware
- **THEN** the middleware applies only to that group's `list` command

### Requirement: Middleware failure

If a middleware fails, the command MUST NOT complete normally: the failure MUST reach the command's error hook and the run MUST exit 1. If a middleware declines the continuation, the action MUST NOT run and the run MUST use the context's exit code unchanged.

#### Scenario: Middleware exception reaches the error hook

- **WHEN** an applicable middleware fails before invoking its continuation
- **THEN** the command's error hook receives the failure and the process exits 1

#### Scenario: Short-circuit preserves the exit code

- **WHEN** a middleware returns without invoking its continuation and the context exit code has not been changed
- **THEN** the action does not run and the process exits 0

### Requirement: Automatic inspection group

Every application MUST automatically register a `middleware` group with a `list` command, and the name `middleware` MUST be reserved so a sidecar plugin cannot occupy it.

#### Scenario: Inspection group is always present

- **WHEN** an application prints its help without declaring any middleware
- **THEN** the `middleware` group and its `list` command appear in the listing

### Requirement: Registry listing output

`middleware list` MUST print a table with `NAME`, `PRIORITY`, `SCOPE`, and `TARGET` columns, one row per registration in declaration order across all scopes. `SCOPE` MUST report the effective scope, and `TARGET` MUST be `*` for application scope, the group name for group scope, or `<group> <command>` for command scope. When no middleware is registered, it MUST print `No middleware registered`.

#### Scenario: Listing every registration

- **WHEN** the user runs `middleware list` on an application with global, group, and command registrations
- **THEN** each registration is shown once with its name, priority, effective scope, and target

#### Scenario: Empty registry

- **WHEN** no middleware is registered
- **THEN** the output is `No middleware registered`

### Requirement: Chain preview

`middleware list --command "<group> <command>"` MUST resolve the same chain execution would use and print it outermost first, one numbered entry per middleware with its name, priority, and scope, ending with `→ action`. It MUST accept both the space-separated and `group:command` forms. When no middleware applies, it MUST print `(no middleware applies)` before `→ action`.

#### Scenario: Preview matches execution order

- **WHEN** the user previews a command with three applicable middleware
- **THEN** the entries are numbered from 1 outermost inward and end with `→ action`

#### Scenario: Preview with an empty chain

- **WHEN** the user previews a command to which no middleware applies
- **THEN** the output states that no middleware applies and still ends with `→ action`

### Requirement: Invalid preview target

A `--command` value that does not split into exactly two non-blank parts MUST be rejected with `Invalid --command value: '<value>' (expected "<group> <command>")`, MUST NOT print a chain, and MUST leave the run's exit code 0.

#### Scenario: Malformed target

- **WHEN** the user runs `middleware list --command "not-a-target"`
- **THEN** the invalid-value message is printed, no chain is shown, and the process exits 0
