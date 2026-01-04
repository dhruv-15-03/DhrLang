# Examples

This document lists **runnable** DhrLang example programs that match the current token set and compiler/runtime behavior.

If you want maximum coverage (including edge cases and negative tests), use the `input/` suite.

## Quick Start (Recommended)

- [input/sample.dhr](input/sample.dhr) — minimal program covering basics
- [input/test_basic_syntax.dhr](input/test_basic_syntax.dhr) — syntax coverage

## Runnable Demos (examples/)

- [examples/hello_world.dhr](examples/hello_world.dhr) — hello world
- [examples/banking_system.dhr](examples/banking_system.dhr) — OOP + methods + fields
- [examples/professional_demo.dhr](examples/professional_demo.dhr) — larger demo program

## How to Run

Using a release JAR:

```powershell
java -jar DhrLang-<version>.jar examples/hello_world.dhr
```

Selecting a backend:

```powershell
java -jar DhrLang-<version>.jar --backend=ast input\sample.dhr
java -jar DhrLang-<version>.jar --backend=ir input\sample.dhr
java -jar DhrLang-<version>.jar --backend=bytecode input\sample.dhr
```

For untrusted code, prefer bytecode + conservative caps:

```powershell
java -Ddhrlang.bytecode.untrusted=true -jar DhrLang-<version>.jar --backend=bytecode input\sample.dhr
```

## Notes on Legacy Content

This repository may contain older `.dhr` programs (especially under `examples/`) that use legacy syntax forms and are **not guaranteed to run** on the current compiler. The authoritative, maintained set of runnable programs is `input/` plus the top-level demos listed above.