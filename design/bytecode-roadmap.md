# DhrLang Bytecode / IR Roadmap

This document outlines the staged plan for introducing a portable Intermediate Representation (IR) and optional bytecode execution backend for DhrLang. The initial goal is *correctness parity* with the current AST interpreter while enabling future optimizations (constant folding, dead code elimination, inlining) without perturbing source semantics.

## Guiding Principles
1. Deterministic & Auditable: Each lowering step should be testable with golden IR snapshots.
2. Parity First: Optimizations are deferred until we pass full semantic + golden-output test suite using the IR backend.
3. Minimal Instruction Set: Start with ~20 instructions covering control flow, stack / locals, object & array ops, method dispatch, and basic arithmetic.
4. Pluggable Backend: CLI flag `--backend=ast|ir` (default ast until GA). Later: `--emit-ir` to dump serialized IR JSON for debugging.
5. Incremental Evolution: Keep IR schema versioned (irSchemaVersion) and validated in tests similar to diagnostics schema.

## Phase Breakdown

### Phase 0 (This PR Batch)
- Add this roadmap.
- Introduce placeholders in code: `IrProgram`, `IrFunction`, `IrBuilder` (empty shells) under `dhrlang.ir` package.
- Add CLI parsing acceptance of `--backend=ir` (feature gated; if selected, print experimental warning & fallback to AST until implemented).

### Phase 1: Core IR Data Structures
- Model value kinds: INT, FLOAT, STRING, BOOL, CHAR, NULL, OBJECT_REF, ARRAY_REF, GENERIC_PLACEHOLDER.
- Instruction subset (draft):
  - `CONST <slot> <value>`
  - `LOAD_LOCAL <slot>` / `STORE_LOCAL <slot>`
  - `FIELD_GET <slot> <fieldIndex>` / `FIELD_SET <slot> <fieldIndex>`
  - `ARRAY_LOAD` / `ARRAY_STORE` / `ARRAY_LEN`
  - `JUMP <label>` / `JUMP_IF_FALSE <slot> <label>`
  - `CALL_STATIC <methodId>` / `CALL_VIRTUAL <methodId>`
  - `RETURN` (implicit top of stack value or void)
  - Arithmetic: `ADD`, `SUB`, `MUL`, `DIV`, `NEG`
  - Comparison: `EQ`, `NEQ`, `LT`, `LE`, `GT`, `GE`
  - `NEW_OBJECT <classId>` / `NEW_ARRAY <elemTypeId> <dims>`
  - `NOP`
- Label resolution pass & validation (structural sanity test).

### Phase 2: Lowering Pipeline
- AST -> IR builder for: literals, variables, blocks, if, while, for, arithmetic, comparisons.
- Initial method invocation (static + simple virtual dispatch).
- Golden IR tests for small programs (store IR as JSON snapshots in `src/test/resources/ir/`).

### Phase 3: Baseline IR Interpreter
- Stack machine executor with local array for slots + heap abstraction.
- Execution parity tests (reuse existing golden runtime tests under `--backend=ir`).
- Add benchmark dimension running both backends; output IR timings when requested.

### Phase 4: Objects, Arrays & Exceptions
- Proper field layout mapping.
- Array bounds & null checks (mirroring existing error categories with same codes).
- Try/catch lowering (structured finally blocks) or temporary bailout (raise NYI) until complete.

### Phase 5: Optimization Hooks (Deferred)
- Peephole pass staging: collapse CONST+STORE patterns, remove dead NOP chains.
- Simple constant folding for binary numeric ops.
- (Later) Inlining, dead branch pruning after constant propagation.

### Phase 6: Emission & Tooling
- `--emit-ir[=phase]` to print IR after lowering (JSON). Optional phases: raw, validated, optimized.
- IR schema version guard + test similar to diagnostics.
- Visualization script stub (future): generate control flow graphs (DOT).

## Testing Strategy
| Layer | Approach |
|-------|----------|
| Instruction Validity | Unit tests per instruction semantics (edge cases, nulls) |
| Lowering Parity | Golden IR snapshots + AST vs IR execution output diff harness |
| Error Parity | Inject failing programs, assert identical error codes & counts |
| Performance | Compare bench tasks with `--backend=ast` vs `--backend=ir` (non-gating initially) |

## Risks & Mitigations
- Divergent Semantics: Mitigate via unified error category enums + shared validation utilities.
- Instruction Bloat: Start minimal; add only when needed by lowering.
- Benchmark Noise: IR path initially slower; regressions ignored until Phase 5.

## Success Criteria (Phase 3 Completion)
1. All existing green tests also pass with `--backend=ir` (excluding any explicitly unsupported constructs yet).
2. Diagnostic error codes identical between backends for negative tests.
3. Bench harness includes both backends (adds `backend` field in JSON) without flakiness.

---

Future extension could include emitting a binary packed IR or targeting a future JIT / WASM backend; these are intentionally out of current scope.
