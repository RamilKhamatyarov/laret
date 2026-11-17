# Middleware Priority, Scoping, and Chain Inspection

- Status: Accepted
- Date: 2026-07-20

## Context

Laret already ships a `Middleware` interface, a `MiddlewareScope` enum, and an internal `MiddlewareChain` in the `core` package. Three defects made scoping unusable in practice.

`CliBuilder.build()` keyed group-scoped middleware by implementation class name (`groupBy { it::class.simpleName }`) while `CommandRunner` resolved it by command-group name (`groupMiddlewares[groupName]`). The two keys agreed only when a middleware class happened to be named after a group, so `MiddlewareScope.GROUP` silently never fired. `CommandRunner.commandMiddlewares` was declared and read but never populated by the DSL, making `MiddlewareScope.COMMAND` dead code. `MiddlewareScope` also carried no target, so a middleware could declare that it was group-scoped but never state which group it belonged to.

Chain ordering, by contrast, was already correct: `sortedBy { it.priority }` feeding an index-advancing chain means a lower priority value wraps more of the execution. The interface documentation described this as "lower values execute first (0 = highest priority)", which conflated invocation order with nesting depth and left `StatsMiddleware`'s `-1000` unexplained.

Registration state lived in three `internal var` maps on the `CommandRunner` singleton, mutated as a side effect of building an application. Test isolation depended on resetting those maps by hand, and two applications in one JVM would overwrite each other.

## Decision

### Priority

A lower priority value wraps more of the chain. Priority sorts flatly across every scope: a command-scoped middleware with a lower value than a global one becomes the outer wrapper, and that is a supported override rather than an accident. Equal priorities preserve registration order, which the existing stable sort already guarantees. `StatsMiddleware` keeps `-1000` to remain the outermost timer.

### Scoping

Scope is derived from the registration site, not declared by the middleware class:

- `CliBuilder.use(...)` registers at `GLOBAL` scope.
- `GroupBuilder.use(...)` registers at `GROUP` scope, targeting the enclosing group.
- `CommandBuilder.use(...)` registers at `COMMAND` scope, targeting the enclosing group and command.

Binding by position keeps a middleware class reusable at any scope and removes the possibility of a target name drifting out of sync with the group it names. Command registrations are keyed by the qualified `group:command` pair, because bare command names such as `list` and `show` repeat across the `plugin`, `stats`, and `locale` groups and a bare key would leak middleware between groups.

`Middleware.scope` remains on the interface as deprecated, read-only metadata so existing implementations continue to compile. The runtime ignores it. `middleware list` reports effective scope derived from registration, never the declared value.

### Registry

`MiddlewareRegistry` is an immutable value object holding global, group, and command registrations. `CliBuilder` builds it and `CliApp` carries it as a field, so resolution is per-application rather than process-wide. `CommandRunner.executeCommand` already receives the application and resolves through `app.middlewares`. The three `CommandRunner` maps survive as deprecated fallbacks that are merged during resolution, preserving existing tests and any code that set them directly.

### Naming

`Middleware` gains `val name: String` defaulting to `this::class.simpleName ?: "<anonymous>"`. Named implementations are labelled correctly without changes, anonymous `object : Middleware { }` declarations used throughout the test suite degrade to a readable placeholder instead of failing, and implementations that need a stable label under GraalVM native image can override the property.

### Inspection

A `middleware` group is registered automatically by `CliBuilder.build()` for every Laret application, alongside the existing built-in completion handling. `middleware list` prints all registrations with name, priority, effective scope, and target. `middleware list --command "<group> <command>"` resolves and prints the chain that would actually execute for that command, outermost first, using the same resolution path as the runner rather than a reimplementation. The group name `middleware` joins the reserved set that sidecar plugin discovery already honours.

## Package Boundaries

`core` holds `MiddlewareRegistry`, `MiddlewareRegistration`, and `MiddlewareListCommand` next to the existing `Middleware` and `MiddlewareChain` types. `dsl` gains only registration entry points on the three builders. No new package is introduced and no new dependency is added.

## Consequences

Group and command scoping work for the first time, and the effective chain for any command is inspectable without reading source. Moving the registry onto `CliApp` removes global mutable state from application construction and makes tests independent without manual resets. Flat priority sorting keeps a single ordering rule but means a mis-set command-scoped priority can wrap a global middleware, so `middleware list` exists partly to make that visible.

The cost is a slightly larger `Middleware` surface (`name`, plus a deprecated `scope`) and one reserved group name. Because scope is positional, moving a `use(...)` call between builders changes behaviour silently; the chain preview is the intended mitigation. Backward compatibility is preserved: no existing implementation, DSL call, or test requires modification.
