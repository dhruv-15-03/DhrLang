# DhrLang Standard Library (Core Built-ins)

This document tracks the currently implemented built-in functions, their stability, and planned expansions. The core philosophy: keep the minimal surface necessary for pedagogy while enabling realistic exercises.

Stability Levels:
- Stable: Semantics unlikely to change; relied on by tests.
- Provisional: May change name or signature pending broader design (flagged in release notes).
- Experimental: Under active design discussion; subject to removal.

## Built-in Functions

| Name | Description | Args | Returns | Stability |
|------|-------------|------|---------|-----------|
| print | Prints a value without newline | any | void | Stable |
| printLine | Prints a value with newline | any | void | Stable |
| arrayLength | Returns length of 1D array | array | num | Stable |
| toNum | Convert string to integer | sab | num | Stable |
| toDuo | Convert string to float | sab | duo | Stable |
| toString | Convert any value to string | any | sab | Stable |
| isNum | Check if value is integer | any | kya | Stable |
| isDuo | Check if value is float | any | kya | Stable |
| isSab | Check if value is string | any | kya | Stable |
| isKya | Check if value is boolean | any | kya | Stable |
| typeOf | Returns type name as string | any | sab | Stable |
| charAt | Returns character at index (string) | sab, num | ek | Provisional |
| substring | Substring range | sab, num, num | sab | Provisional |
| replace | Replace substring | sab, sab, sab | sab | Provisional |

> **Note:** `toNum`, `toDuo`, and `toString` are also accessible via the `as` cast syntax:
> `expr as num`, `expr as duo`, `expr as sab`

## Types / Keywords (Core)
`num`, `duo`, `sab`, `kya`, `ek`, `kaam`, `class`, `extends`, `static`, access modifiers, control flow (`if`, `for`, `while`, `try/catch/finally`).

## Planned Additions
- Math utilities (min, max, pow) – pending generic numeric discussions.
- String trim / contains / indexOf.
- Collections (list/map) – deferred until module system concept.
- Time / system utilities – possibly via a sandboxed host interop layer.

## Non-Goals (Near Term)
- Networking, file IO (would bloat runtime & complicate determinism for tests).
- Threads / concurrency primitives (language semantics not yet defined).

## Backward Compatibility Policy
Stable entries will not have breaking signature changes within a minor release line (1.x). Provisional entries may evolve; changes enumerated in CHANGELOG under STDLIB section.

## Test Coverage Mapping
| Built-in | Representative Tests |
|----------|----------------------|
| print/printLine | Golden output tests, sample programs |
| arrayLength | array edge tests, multi-dim arrays |
| charAt | string edge tests (bounds, empty) |
| substring | string slicing tests |
| replace | string replace tests |

---

Contributions: open an issue proposing new built-ins including rationale, complexity, and interaction with generics/null semantics.
