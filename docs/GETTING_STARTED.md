# Getting Started with DhrLang

This guide shows how to build and run DhrLang programs on Windows (PowerShell), how to select execution backends (AST / IR / Bytecode), and how to use diagnostics.

## Prerequisites
- Java 17 or newer (21 also supported)
- Git (for building from source)

Verify Java:
```powershell
java -version
```

## Install or Build

### Option A: Download release JAR (Recommended)
1. Download the latest release JAR from:
   https://github.com/dhruv-15-03/DhrLang/releases/latest
2. Download `DhrLang-1.1.3.jar` (fat JAR with all dependencies)
3. Run directly:
```powershell
java -jar DhrLang-1.1.3.jar input\sample.dhr
```

### Option B: Build from source
```powershell
# Clone
git clone https://github.com/dhruv-15-03/DhrLang.git
Set-Location DhrLang

# Build fat JAR
./gradlew.bat shadowJar

# Run a program with the fat JAR
java -jar build\libs\DhrLang-1.1.3.jar input\sample.dhr

# Or run via Gradle
./gradlew.bat run --args="input\sample.dhr"

# Or run tests
./gradlew.bat test
```

## Running your first program
Create a file `hello.dhr`:
```dhrlang
class Main {
    static kaam main() {
        printLine("Hello, DhrLang!");
    }
}
```
Run it:
```powershell
# Using fat JAR
java -jar build\libs\DhrLang-1.1.3.jar hello.dhr

# Or using Gradle
./gradlew.bat run --args="hello.dhr"
```

## CLI flags you’ll use often
- `--help` / `-h` — show usage
- `--version` / `-v` — show version
- `--json` — emit diagnostics as JSON (see Diagnostics section)
- `--time` — print phase timings (also included in JSON when `--json`)
- `--no-color` — disable ANSI colors
- `--backend=ast|ir|bytecode` — choose execution backend
- `--emit-ir` — dump lowered IR
- `--emit-bc` — write compiled bytecode to `build/bytecode/Main.dbc`

Notes:
- If you don’t pass a file, the CLI defaults to `input/sample.dhr`.
- Exit codes: 0 success, 1 compile error, 2 runtime/system error.

## Switch execution backends
The default backend is the AST interpreter. You can select the IR or bytecode backend with flags:
```powershell
# IR backend
java -jar build\libs\DhrLang-1.1.3.jar --backend=ir input\test_arrays.dhr

# Bytecode backend
java -jar build\libs\DhrLang-1.1.3.jar --backend=bytecode input\test_arrays.dhr

# Using Gradle
./gradlew.bat run --args="--backend=ir input\test_arrays.dhr"
```

Notes:
- Backend selection is authoritative: when you choose `--backend=ir` or `--backend=bytecode`, the program executes on that backend (no AST fallback).
- The test suite enforces parity across AST/IR/bytecode for core semantics.

## Running untrusted programs (bytecode)
If you execute untrusted code, prefer the bytecode backend with the built-in verifier and conservative defaults:
```powershell
java -Ddhrlang.bytecode.untrusted=true -jar build\libs\DhrLang-1.1.3.jar --backend=bytecode input\sample.dhr
```
Key runtime flags (as JVM system properties):
- `dhrlang.bytecode.untrusted` (default: false) — enables conservative limits and strict entry validation.
- `dhrlang.backend.maxSteps` — instruction step limit (shared by IR and bytecode).
- `dhrlang.bytecode.strictEntry` — require an entrypoint (`Main.main` or any `*.main`).
- `dhrlang.bytecode.maxBytes`, `dhrlang.bytecode.maxConstPool`, `dhrlang.bytecode.maxFunctions`, `dhrlang.bytecode.maxInstructionsPerFunction` — size/shape caps for bytecode input.
- `dhrlang.bytecode.maxCallDepth`, `dhrlang.bytecode.maxHandlersPerFrame` — execution caps.
- `dhrlang.bytecode.verifyControlFlow` (default: true) — validates try/catch control-flow structure.

## Inspect IR and Bytecode
```powershell
# Emit IR for a program (printed to stdout)
./gradlew.bat run --args="--backend=ir --emit-ir input\calls_sample.dhr"

# Emit bytecode file (written to build/bytecode/Main.dbc)
./gradlew.bat run --args="--backend=bytecode --emit-bc input\test_arrays.dhr"
```

## Diagnostics as JSON (machine-readable)
```powershell
# Produce JSON diagnostics (e.g., for a syntax error) and include timings
./gradlew.bat run --args="--json --time input\parser_error_test.dhr"
```
The JSON schema is validated in tests; see `diagnostics.schema.json` and `DiagnosticsSchemaValidationTest` for details.

## Common issues
- PowerShell cannot find `gradlew.bat`:
  Use `./gradlew.bat` (note the `./` prefix). Example:
  ```powershell
  ./gradlew.bat test
  ```
- Java not found:
  Ensure Java 17+ is on your PATH, or set `JAVA_HOME` to a JDK install.
- Colored output looks odd:
  Add `--no-color` to disable ANSI sequences in terminals that don’t support them.

## What’s implemented in IR/Bytecode backends
- Literals, locals (load/store), arithmetic (+ - * /), comparisons (== != < <= > >=)
- Control-flow: if/else, while, short-circuit `&&` and `||`, `break`/`continue`
- Print/printLine, return (with/without value)
- Arrays: literal creation, `new`, load/store, `arrayLength`
- Static function calls with return values
- Static fields and instance fields
- Exceptions: `throw`, `try/catch/finally` (including typed catches)

The default backend remains AST for compatibility and debugging, but IR and bytecode are intended to be semantically equivalent.

## Next steps
- Explore examples in `input/`
- Read the Language Spec (`SPEC.md`)
- Check the README for roadmap and advanced topics
