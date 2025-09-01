# DhrLang Quality & Readiness Guide

This document tracks production readiness dimensions and enforced gates.

## Readiness Dimensions (Current Snapshot)
- Correctness: Growing golden + negative test suite (51 tests). Core parser/lexer well covered; interpreter/typechecker medium coverage.
- Safety & Errors: Structured runtime error categories; access modifiers enforced; stack depth guard (MAX_CALL_DEPTH=1000) in functions.
- Maintainability: Central `NativeSignatures` registry partially integrated (existence + arity). Interpreter still wires natives manually.
- Test Coverage: Instruction ~43%, branch ~33% (Jacoco). Gate set to 40% / 30% to prevent regression; will ratchet upward.
- Observability: Stack traces for thrown exceptions; need future logging hooks.
- Performance: Tree-walk implementation (baseline). No profiling yet.

## Gates
Jacoco verification (build.gradle):
- INSTRUCTION >= 40%
- BRANCH >= 30%

## Native Function Strategy
`NativeSignatures` centralizes name, parameter type hints, and return category. Planned stages:
1. (Done) Registry established for existence/arity; TypeChecker uses for presence.
2. (Next) Interpreter initGlobals refactor: map registry entries to concrete implementations; add assertion that every registry name is installed.
3. (Next) Replace large switch in `TypeChecker.checkNativeFunction` with metadata-driven validation helpers.

## Future Ratchet Plan
Target increments once stable at each level:
- 50% / 38%
- 60% / 45%
- 70% / 55% (introduce mutation tests or fuzz harness)

## Near-Term TODO
- Refactor native wiring (Interpreter + TypeChecker).
- Add deterministic recursion test at depth limit boundary.
- Expand stdlib tests (arrays, string transforms, edge cases: empty substring, out-of-range charAt error, arraySlice bounds, range negative steps if supported).
- Document language spec basics (separate SPEC.md).

## Recursion Depth Policy
Current MAX_CALL_DEPTH = 1000 enforced per function call entry (`Function.call`). Provide clear error message guiding users to add base cases.

## Access Control
TypeChecker enforces private/protected for fields & methods on instance and static access.

## Error Messaging Philosophy
Every user-facing error includes actionable hint (TypeChecker) or category (Runtime).

---
This file maintained alongside readiness score discussions; update upon each refactor wave.
