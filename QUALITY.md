# DhrLang Quality & Readiness Guide

This document tracks production readiness dimensions and enforced gates.

## Readiness Dimensions (Current Snapshot)
- Correctness: Golden + negative test suite with parity coverage across AST/IR/bytecode for core semantics.
- Safety & Errors: Structured runtime error categories; access modifiers enforced; runtime execution caps available (e.g., `dhrlang.backend.maxSteps`).
- Maintainability: Central `NativeSignatures` registry partially integrated (existence + arity). Interpreter still wires natives manually.
- Test Coverage: Enforced Jacoco minimums to prevent regressions; run `./gradlew.bat test jacocoTestReport` to see current numbers.
- Observability: Stack traces for thrown exceptions; need future logging hooks.
- Performance: AST interpreter is the baseline; IR and bytecode backends exist to enable optimizations without changing semantics.

## Bytecode Safety (Runtime)
The bytecode VM validates bytecode before executing and supports an untrusted mode:
- `-Ddhrlang.bytecode.untrusted=true` enables conservative defaults and tighter caps.
- Additional caps exist for bytecode size/shape, call depth, handler depth, and control-flow verification.

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

## Static Initialization Policy
Enforced by TypeChecker at compile time to avoid fragile order-dependent bugs:
- No same-class static forward reads in field initializers (diagnostic: STATIC_FORWARD_REFERENCE).
- No static initializer dependency cycles within a class (diagnostic: STATIC_INIT_CYCLE).
Rationale: keeps runtime initialization simple (source order) while ensuring determinism and debuggability.

## Error Messaging Philosophy
Every user-facing error includes actionable hint (TypeChecker) or category (Runtime).

---
This file maintained alongside readiness score discussions; update upon each refactor wave.
