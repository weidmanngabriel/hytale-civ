# Architecture Decision Records

Architecture Decision Records (ADRs) document the rationale for significant architectural decisions whose history would otherwise be lost from `docs/architecture.md`.

Use filenames in the form:

```text
NNNN-short-title.md
```

Allowed statuses are `Proposed`, `Accepted`, `Superseded`, `Deprecated` and `Rejected`.

Coding agents may create new ADRs only with status `Proposed`. `Accepted` and all later status changes require an explicit human decision.

Use an ADR when a decision has meaningful long-term architectural consequences, alternatives were genuinely considered, or future maintainers are likely to ask why the current structure exists. Routine implementation details do not need ADRs.
