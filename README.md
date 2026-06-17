# DhrLang Programming Language

[![Build Status](https://github.com/dhruv-15-03/DhrLang/actions/workflows/ci.yml/badge.svg)](https://github.com/dhruv-15-03/DhrLang/actions)
[![Coverage](docs/badges/coverage.svg)](#test-coverage--mutation-testing)
[![Mutation](docs/badges/mutation.svg)](#test-coverage--mutation-testing)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Release](https://img.shields.io/github/v/release/dhruv-15-03/DhrLang)](https://github.com/dhruv-15-03/DhrLang/releases/latest)

DhrLang is a modern, statically typed, object-oriented programming language with a
concise English-core token set (`num`, `duo`, `sab`, `kya`, `ek`, `kaam`) inspired by
earlier Hindi-localized experimentation. Focus: clarity, pedagogy, and strong static
analysis while retaining culturally inspired naming roots.

It runs on the JVM, ships three execution backends (AST, IR, bytecode), an LSP server,
and an experimental EVM (smart-contract) compiler target.

> **Current release: v3.2.0** - see [What's New in v3.2.0](#whats-new-in-v320) and
> [CHANGELOG.md](CHANGELOG.md).

## Quick Links

- Getting Started: [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md)
- Installation: [see below](#installation)
- Language Spec: [SPEC.md](SPEC.md)
- Standard Library: [STDLIB.md](STDLIB.md)
- Error Codes: [ERROR_CODES.md](ERROR_CODES.md)
- Examples: [`input/`](input/)
- Changelog: [CHANGELOG.md](CHANGELOG.md)
- Release Checklist: [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md)
- Bytecode / IR Plan: [design/bytecode-roadmap.md](design/bytecode-roadmap.md)
  ([format](design/bytecode-format.md))
- Editor Integration: [VS Code Extension](#vs-code-extension-editor-integration)

## Features

- **Concise core tokens**: minimal memorable keywords - `num` (int), `duo` (float),
  `sab` (string), `kya` (boolean), `ek` (char), `kaam` (function/method)
- **Object-oriented programming**: classes, inheritance (`extends`), access control,
  static members
- **Static typing**: strong compile-time type checking with generic substitution
- **Arrays**: multi-dimensional and jagged arrays with bounds and type safety
- **Generics**: type parameters on classes/methods with full substitution + diagnostics
- **Implicit field access**: instance-method identifier resolution falls back to fields
  safely
- **String and array utilities**: core built-ins (`printLine`, `arrayLength`,
  substring helpers, etc.)
- **Structured error handling**: `try`/`catch`/`finally` (including typed catches)
- **Three backends**: AST (default, debug-friendly), IR, and bytecode - semantically
  equivalent for the implemented feature set
- **Static initialization safety**: detects forward references and dependency cycles
  early
- **Tooling**: LSP server (`--lsp`), JSON diagnostics, VS Code extension, EVM target

### Currently unsupported / experimental

Some constructs (modules, concurrency, lambdas/closures) are either experimental or not
implemented yet. See [SPEC.md](SPEC.md) for authoritative status markers and
[design/bytecode-roadmap.md](design/bytecode-roadmap.md) for backend evolution.

## What's New in v3.2.0

Smart-contract security tooling:
- **SARIF code scanning is first-class.** `--audit --sarif` now emits real source line numbers
  (`region.startLine`) and a stable `partialFingerprints` per finding, so results land precisely
  in GitHub's **Security → Code scanning** tab and dedupe across runs. Rule links resolve to the
  new [SECURITY_RULES.md](SECURITY_RULES.md) (every `ARITH-*`, `SEC-*`, `INV-*`, `AUD-*`,
  `DHR-E5xx` rule with severities + SWC mappings).
- **`--audit` now runs on contracts that have errors.** It is an analysis mode: it no longer
  aborts when a contract fails type/validation checks — those issues are reported as findings and
  it always exits `0`. Previously the most security-relevant contracts produced no report at all.

Full details in [CHANGELOG.md](CHANGELOG.md).

## What's New in v3.1.0

EVM (smart-contract) backend:
- **Checked / wrapping arithmetic modes**: opt into overflow-safe math per function with
  `@checked` (reverts with `"arithmetic overflow"` / `"arithmetic underflow"`) or `@unchecked`
  (wraps mod 2²⁵⁶). Default stays wrapping in this release.
- **Custom errors + `revert`**: declare gas-efficient typed errors
  (`@error kaam InsufficientBalance(num available, num required) {}`) and raise them with
  `revert(...)` / `require(cond, ...)`; emitted as `"type":"error"` in the ABI.
- **Explicit `indexed` event parameters**: event topics are driven by the `indexed` keyword
  (e.g. `Transfer(indexed Address from, indexed Address to, num amount)`).

Fixed:
- EVM backend now emits correct **unsigned** opcodes and operand order for `-`, `/`, `%` and
  comparisons (`num` is `uint256`); checked multiply uses the SafeMath identity.
- Peephole optimizer no longer misreads `PUSH` immediate data as opcodes (it could corrupt
  bytecode — e.g. embedded revert strings).
- Numeric `as` casts: `expr as num` / `expr as duo` (and `toNum` / `toDuo`) now accept numeric
  operands; `(7 / 2) as num` → `3` is the supported integer-division idiom.

Full details in [CHANGELOG.md](CHANGELOG.md).

## What's New in v3.0.0

Language:
- **Labeled `break`/`continue`** with labeled loops: `outer: for(...) { ... break outer; }`
- **`as` type cast**: `expr as num`, `expr as duo`, `expr as sab`
- **Hex literals**: `0xFF`, `0xABCD` (all backends)
- **String interpolation**: `"Hello ${name}!"`
- **Bitwise operators**: `&`, `|`, `^`, `~`, `<<`, `>>` (all backends + type checker)

Tooling:
- **LSP server**: stdio-based diagnostics, completion, and hover via `--lsp`
- **VS Code extension v3.0.0**: updated grammar and keyword highlighting

EVM (smart-contract) backend:
- SafeMath overflow/underflow protection, auto-generated access control (`onlyOwner`),
  collision-safe reentrancy lock, peephole optimizer, gas/stack/memory tracking

Quality: **1,287 tests, 0 failures**. Full details in [CHANGELOG.md](CHANGELOG.md).

## Installation

### Prerequisites

- **Java**: JDK 17 or higher
- **OS**: Windows, macOS, or Linux

### Option 1: VS Code Extension (easiest)

1. Download `dhrlang-vscode-3.0.0.vsix` from the
   [latest release](https://github.com/dhruv-15-03/DhrLang/releases/latest).
2. In VS Code: Command Palette -> "Extensions: Install from VSIX..." and select the file.
3. Ensure **Java 17+** is installed.
4. Open any `.dhr` file and run it directly (Ctrl+F5).
   - **Zero config**: the extension bundles the compiler, so no manual JAR download is
     needed.

### Option 2: Download a Release (manual CLI)

1. Go to the [Releases page](https://github.com/dhruv-15-03/DhrLang/releases/latest).
2. Download the runnable fat JAR **`DhrLang.jar`** (or a platform bundle:
   `DhrLang-3.0.0-windows.zip` / `DhrLang-3.0.0-linux.tar.gz`). Optionally verify against
   `checksums.txt`.
3. Run it:
   ```bash
   java -jar DhrLang.jar input/sample.dhr
   ```

### Option 3: Build from Source

```bash
git clone https://github.com/dhruv-15-03/DhrLang.git
cd DhrLang
./gradlew shadowJar          # use ./gradlew.bat on Windows
java -jar build/libs/DhrLang-3.0.0.jar input/sample.dhr
```

A plain `./gradlew build` (or `./gradlew.bat build` on Windows) compiles and runs the
full test suite.

## Command-Line Interface

```bash
java -jar DhrLang.jar [options] path/to/file.dhr
```

If no file is given, DhrLang defaults to `input/sample.dhr`.

| Flag | Description |
|------|-------------|
| `--help`, `-h` | Print usage and exit |
| `--version`, `-v` | Print version (e.g. "DhrLang version 3.0.0") |
| `--json` | Emit diagnostics as JSON (see [JSON Diagnostics](#json-diagnostics)) |
| `--time` | Show phase timings (lex/parse/type/exec) and embed them in JSON |
| `--no-color` | Disable ANSI colors in diagnostics |
| `--backend=ast\|ir\|bytecode` | Select execution backend (default: `ast`) |
| `--emit-ir` | Dump lowered IR (JSON) for debugging |
| `--emit-bc` | Write compiled bytecode to `build/bytecode/Main.dbc` |
| `--lsp` | Start the Language Server Protocol server (stdio) |

### Exit codes

- `0`: success (or warnings only)
- `1`: compile-time error(s)
- `2`: runtime/system error
- `65`: JSON diagnostics emission that includes errors

### Runtime safety flags (JVM system properties)

- `dhrlang.backend.maxSteps` - instruction step limit (IR + bytecode)
- `dhrlang.bytecode.untrusted=true` - conservative validation + limits for bytecode
- `dhrlang.bytecode.strictEntry` - require an entrypoint (`Main.main` or any `*.main`)

When running untrusted code, prefer `--backend=bytecode` with
`-Ddhrlang.bytecode.untrusted=true` to enable strict verification and resource limits.

### JSON Diagnostics

DhrLang can emit machine-readable diagnostics for tooling integration:

```bash
java -jar DhrLang.jar --json --time program.dhr
```

Output conforms to [`diagnostics.schema.json`](diagnostics.schema.json) (JSON Schema v7):

- `schemaVersion`: currently `1` (stable contract)
- `timings`: phase timings in ms (lex, parse, type, exec, total)
- `errors` / `warnings`: arrays of objects with file, line, column, type, message, hint,
  sourceLine

## Language Syntax

### Data types

- `num` - integer numbers
- `duo` - floating-point numbers
- `sab` - strings
- `kya` - boolean values
- `ek` - characters

### Keywords

- `class` - class declaration
- `kaam` - function/method declaration
- `if` / `else` - conditional statements
- `while` / `for` / `do` - loop statements (with labeled `break`/`continue`)
- `try` / `catch` / `finally` - exception handling
- `return` - return statement
- `new` - object instantiation
- `this` / `super` - object references
- `extends` / `implements` - inheritance
- `static` - static members
- `private` / `protected` / `public` - access modifiers
- `as` - type cast

## Quick Start

### Hello World

```dhrlang
class Main {
    static kaam main() {
        printLine("Hello, DhrLang!");
    }
}
```

### Variables and basic operations

```dhrlang
class Example {
    static kaam main() {
        num x = 42;
        sab message = "Hello World";
        kya flag = true;

        printLine("Number: " + x);
        printLine("Message: " + message);
        printLine("Flag: " + flag);
    }
}
```

### String interpolation, hex literals, and `as` casts (v3.0.0)

```dhrlang
class Modern {
    static kaam main() {
        sab name = "DhrLang";
        num mask = 0xFF;                 // hex literal
        num packed = 0xF0 & 0x0F;        // bitwise AND
        printLine("Hello ${name}! mask=${mask}");
        sab text = packed as sab;        // type cast
        printLine("packed as text: " + text);
    }
}
```

### Labeled break (v3.0.0)

```dhrlang
class LabeledBreak {
    static kaam main() {
        outer: for (num i = 0; i < 3; i++) {
            for (num j = 0; j < 3; j++) {
                if (i + j == 3) { break outer; }
                printLine(i + "," + j);
            }
        }
    }
}
```

### Object-oriented programming

```dhrlang
class Animal {
    protected sab name;

    kaam init(sab name) {
        this.name = name;
    }

    kaam makeSound() {
        printLine(this.name + " makes a sound");
    }
}

class Dog extends Animal {
    kaam init(sab name) {
        super.init(name);
    }

    kaam makeSound() {
        printLine(this.name + " barks!");
    }
}
```

### Multi-dimensional arrays

```dhrlang
class NDArrayDemo {
    static kaam main() {
        // Allocate a 2D array 3x4 of num
        num[][] m = new num[3][4];
        m[0][0] = 1; m[2][3] = 7;
        for (num i = 0; i < 3; i++) {
            for (num j = 0; j < 4; j++) {
                print(" " + m[i][j]);
            }
            printLine("");
        }
        // Jagged arrays are supported (rows can differ in length)
        num[][] jag = new num[2][];
        jag[0] = new num[1];
        jag[1] = new num[3];
        printLine(arrayLength(jag)); // prints 2
    }
}
```

Notes:
- Indexing is bounds-checked: negative or `>= length` raises an index error.
- Allocation requires non-negative sizes; very large sizes are rejected.
- Element defaults follow type defaults (num -> 0, duo -> 0.0, kya -> false,
  references -> null).

### Exception handling

```dhrlang
class ErrorExample {
    static kaam main() {
        try {
            throw "boom";
        } catch (err) {
            printLine("Caught: " + err);
        } finally {
            printLine("Cleanup");
        }
    }
}
```

## Language Features

### Type system

- Explicit static types for variable declarations (no local type inference yet)
- Generics with type-parameter substitution and clear diagnostics
- Array type safety, including multi-dimensional arrays
- Simple method model (no overloading; duplicate names are rejected)

### Core built-ins

- String utilities (e.g. `charAt`, `replace`, `substring` - scope documented in SPEC)
- Array operations (`arrayLength`, indexing, iteration patterns)
- Output (`print`, `printLine`)
- Basic math / operator semantics via the core evaluator

### Diagnostics

- Source-mapped error messages with codes and hints
- Colorized terminal output (ANSI) for clarity
- Categories: parser, type checker, runtime (documented in
  [ERROR_CODES.md](ERROR_CODES.md))

## Backends and Roadmap

The high-level roadmap lives in
[design/bytecode-roadmap.md](design/bytecode-roadmap.md) (IR + bytecode path).

- `--backend=ast` is the default and is useful for debugging.
- `--backend=ir` and `--backend=bytecode` are intended to be semantically equivalent to
  AST for the implemented language feature set.
- Backend selection is authoritative: IR/bytecode runs do not fall back to AST.

Implemented in IR/bytecode:
- Literals, locals (load/store), arithmetic (`+ - * /`), comparisons
  (`== != < <= > >=`)
- Control flow: `if`/`else`, `while`, short-circuit `&&`/`||`, `break`/`continue`
- `print`/`printLine`, `return` (with/without value), unary `-`/`!`, postfix `++`/`--`
- Arrays (literal/new/load/store/`arrayLength`), static function calls
- Static fields + instance fields
- Exceptions: `throw`, `try`/`catch`/`finally` (including typed catches)
- Bitwise operators, hex literals, string interpolation, `as` casts

## Development

### Project structure

```
src/
  main/java/dhrlang/
    Main.java        # CLI entry point
    lexer/           # Lexical analysis
    parser/          # Syntax analysis
    ast/             # Abstract syntax tree
    typechecker/     # Type checking
    types/           # Type model
    eval/            # Core semantic evaluator
    interpreter/     # AST interpretation
    ir/              # Intermediate representation
    bytecode/        # Bytecode compiler/VM
    evm/             # EVM (smart-contract) compiler target
    lsp/             # Language Server Protocol server
    agent/           # Agent orchestration
    pipeline/        # Compilation pipeline
    debug/           # Debugger support
    deploy/          # Deployment tooling
    production/      # Production utilities
    runtime/         # Runtime support
    stdlib/          # Standard library
    error/           # Error reporting
    testing/         # Testing helpers
    tools/           # Developer tools
    util/            # Utilities
    validation/      # Static validation/checks
  test/java/dhrlang/ # Test suite
```

### Running tests

```bash
./gradlew test                                      # all tests
./gradlew test --info                               # detailed output
./gradlew test --tests "dhrlang.DhrLangCompilerTest" # a specific class
```

### Test coverage & mutation testing

Jacoco + PIT run in CI. The badges reflect instruction coverage and mutation kill ratio.
Thresholds ratchet upwards over time. Coverage spans lexical analysis, parsing, type
checking, runtime execution, and error handling.

### Benchmarks

Run micro benchmarks:

```bash
./gradlew bench
```

Outputs `build/bench/bench-results.json` (JSON array). Each entry:

```jsonc
{
    "file": "fib.dhr",
    "timings": { "lexMs": 0, "parseMs": 0, "typeMs": 0, "execMs": 0, "totalMs": 0 },
    "errorCount": 0,
    "warningCount": 0
}
```

Compare with the baseline (fails the build on >50% regression):

```bash
./gradlew benchCompare
```

Update the baseline after validating stability:

```bash
cp build/bench/bench-results.json bench/baseline.json
```

Bench runs are advisory (micro scale, not a full perf suite).

## Examples

See the [`input/`](input/) directory for runnable examples:

- `test_basic_syntax.dhr` - basic language features
- `test_oop_features.dhr` - object-oriented programming
- `test_arrays.dhr` - array operations
- `test_exceptions.dhr` - exception handling
- `test_strings.dhr` - string manipulation
- `test_static_methods.dhr` - static methods and utilities
- `advanced_features_test.dhr` - generics syntax and substitution
- `complete_feature_demo.dhr` - generics, multi-dimensional arrays, implicit field access
- `advanced_edge_cases.dhr` - stress tests for arrays and generics
- `duplicate_error_test.dhr`, `parser_error_test.dhr` - intentional negative tests

## Feature Status

- **Generics**: implemented, including type-parameter substitution and diagnostics
- **Multi-dimensional arrays**: supported in parser, type checker, and evaluator
- **Implicit field access**: unqualified variable/assignment inside instance methods
  resolves to fields, with generic substitution and access checks
- **Diagnostics**: structured error codes, JSON output, actionable hints
- **CI/Quality**: Jacoco, PIT, CodeQL, Dependabot integrated

### Diagnostics quick guide

| Situation | Code |
|-----------|------|
| Cannot access private/protected member | `ACCESS_MODIFIER` |
| Wrong number of generic type arguments | `GENERIC_ARITY` |
| Type does not match expected (incl. after substitution) | `TYPE_MISMATCH` |
| Name not found (static context cannot resolve instance fields) | `UNDECLARED_IDENTIFIER` |
| Array index/size invalid or allocation too large | `BOUNDS_VIOLATION` |
| Static field reads a later-declared static field (same class) | `STATIC_FORWARD_REFERENCE` |
| Static field initializers form a dependency cycle | `STATIC_INIT_CYCLE` |

## Known Limitations

DhrLang is an educational/exploratory language under active development. Current gaps:

- **No module/import system**: all code lives in a single file
- **No lambdas/closures**: functional patterns not yet supported
- **No enum types**: language-level enums not yet available
- **Single-threaded**: no concurrency primitives (async/await, coroutines)
- **No REPL**: interactive mode not yet available (a debug REPL exists for EVM contracts)
- **Generics**: syntactic and substitution support exist; runtime enforcement is limited
- **EVM compiler**: production-ready for basic contracts; complex ABI types (nested
  arrays, tuples) are partial

See [NEXT_STEPS.md](NEXT_STEPS.md) for the detailed roadmap.

## VS Code Extension (Editor Integration)

The official VS Code extension (version-aligned with core releases) provides:

- Syntax highlighting for the current English-core tokens
- Snippets (main class, loops, methods, `printLine`, `try`/`catch` skeleton)
- Run / Compile commands with status-bar JAR detection
- Optional inline diagnostics (enable via settings)

Manual install (until a marketplace listing is active):

1. Download `dhrlang-vscode-3.0.0.vsix` from the release assets.
2. In VS Code: Command Palette -> "Extensions: Install from VSIX...".
3. Select the file and open a `.dhr` file to activate.

Settings preview (add to `settings.json` as needed):

```jsonc
{
    "dhrlang.autoDetectJar": true,
    "dhrlang.jarPath": "",
    "dhrlang.enableAutoCompletion": true,
    "dhrlang.enableErrorSquiggles": true
}
```

If auto-detection fails, set `dhrlang.jarPath` directly to your built `DhrLang.jar`.

## CI Notes

- **CodeQL**: if GitHub Code Scanning is not enabled for your repository, the CodeQL job
  still runs but its upload step is non-blocking. Enable Code Scanning in repository
  settings for full results (alerts in the Security tab).

## Planned Features

Package management, standard-library expansion, a module system, concurrency support,
a foreign function interface, JIT compilation, a WebAssembly target, advanced
optimization, and broader IDE/language-server support.

## Contributing

Contributions are welcome. See the [Contributing Guidelines](CONTRIBUTING.md) for details.

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/amazing-feature`).
3. Make your changes and add tests.
4. Ensure all tests pass (`./gradlew test`).
5. Commit your changes.
6. Push to the branch and open a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for
details.

## Acknowledgments

- Inspired by modern language-design principles
- Built with Java for cross-platform compatibility
- Inspired by earlier efforts to make programming approachable with culturally resonant
  naming
- Community-driven development approach

## Contact

- GitHub: [@dhruv-15-03](https://github.com/dhruv-15-03)
- Project: [https://github.com/dhruv-15-03/DhrLang](https://github.com/dhruv-15-03/DhrLang)

---

*DhrLang - a compact, statically typed educational and exploratory language.*
