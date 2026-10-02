# Stage 3 compatibility decision

Status: accepted for the manifest-first increment. New import/module syntax and
numeric-default changes are explicitly out of scope for this increment.

## Public boundary

The existing single-file CLI, source syntax and `host` request/response v1 remain
available. A separate `project check/run` command resolves an explicit ordered
list of local files, independently parses them, and combines their declarations
in the existing global namespace. `PROJECTS.md` defines the manifest contract.

| Surface | Current contract | Evidence |
|---|---|---|
| Single-file JVM programs | Existing syntax and AST/IR/bytecode execution remain | `ThreeWayParityTest`, CLI and runtime tests |
| Bounded host v1 | One source string, explicit stdin, bytecode profile and resource limits | `HostExecutionTest`, request/response schemas |
| Project manifest v1 | Named local UTF-8 units, exact compiler version, ordered initialization | `ProjectRunnerTest`, `project.schema.json` |
| Source diagnostics | Per-file spans, distinct diagnostics, per-file suppression | Project diagnostic/token tests |
| EVM | Separate experimental target and integer semantics | Independent EthereumJS suite; `BLOCKCHAIN_TUTORIAL.md` limits |

These tests establish a bounded conformance corpus, not universal backend
equivalence or production certification.

## Numeric compatibility

Do not switch JVM `num` arithmetic to checked-by-default here. Existing JVM
signed-64-bit behavior, `duo` floating-point behavior, division and casts retain
their current implementation. The EVM target remains separate and must not be
used as the JVM numerical oracle.

A future numeric RFC must specify overflow, division/modulo, comparisons,
casts, minimum/maximum literals, constant folding and exceptions on every
backend. It needs a migration corpus and explicit approval for any breaking
default. No `@unchecked` host behavior is implied by the EVM annotation.

## Project compatibility

No automatic dependency resolution, module exports or cyclic import behavior is
claimed. The manifest is a deterministic file list, not a dependency DAG. If
scoped modules are introduced later, their resolution, initialization and
visibility rules require a distinct versioned design and editor support.

The bounded host is process-separated, not thread-safe in-process embedding and
not an OS security sandbox. Trusted local use and synthetic learner fixtures are
the supported purpose of the current experiment. External isolation review,
real learner outcomes and authorized SAP tenant testing remain qualification
gates rather than documentation-only checkboxes.
