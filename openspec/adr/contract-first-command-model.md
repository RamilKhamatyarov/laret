# Contract-First Command Model

## Status

Accepted

## Decision

Commands expose a declarative, machine-readable execution contract. The v1 contract records the command name and description, input schema, scalar input types, and a coarse impact profile. Input types are `string`, `integer`, `number`, or `boolean`; declarations omitted by existing callers default to `string`. Commands without an explicit impact declaration report `undeclared` and are not inferred to have no effect.

The supported impact categories are `no_effect`, `read_only`, `local_mutation`, and `external_interaction`. Command owners declare the category explicitly. It is descriptive metadata and does not change command execution, validation, or authorization.

A provider-neutral Laret contract export is added alongside the existing OpenAI and Anthropic formats. Those existing provider formats retain their current output shape and scalar serialization. Public contract and MCP listings follow the existing visibility rules for hidden groups and commands.

MCP tool listings additionally include an `impactProfile` with `category` and `capabilityNeeds`. The capability-needs list is always empty in v1. It is a reserved additive wire field only; v1 consumers and runtime behavior must not interpret it.

## Compatibility

Existing command construction remains source-compatible through defaulted metadata. Existing OpenAI and Anthropic exports remain unchanged. The MCP field is additive. Existing clients can continue using the established tool fields and ignore `impactProfile`; future clients may read the reserved list without requiring a breaking v1 schema change.

## Non-Goals

This decision establishes contract and visibility only. It does not implement capability enforcement, capability inference, proof checking, policy evaluation, sandboxing, energy/resource accounting, or any other behavior. It does not add runtime type coercion, infer command impact, change authorization, or revise existing provider schemas.

## Acceptance

- The neutral contract can be exported in a versioned JSON representation.
- MCP tool listings expose a profile for every visible tool, including `undeclared` profiles.
- Every profile includes an empty `capabilityNeeds` list.
- Declared scalar types appear in the neutral contract and MCP input schema.
- Existing OpenAI and Anthropic schema output remains unchanged.
- Hidden groups and commands remain absent from public exports and tool listings.
- CI checks these deterministic contract and MCP properties; runtime and size are not used as gates.

## Risks

Impact declarations may be inaccurate if command authors do not maintain them. `undeclared` avoids silently claiming a command has no effect. The reserved field could be mistaken for active policy; keeping it empty and explicitly inert avoids prematurely assigning capability semantics.
