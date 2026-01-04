# DhrLang Programming Language

[![Build Status](https://github.com/dhruv-15-03/DhrLang/actions/workflows/ci.yml/badge.svg)](https://github.com/dhruv-15-03/DhrLang/actions)
[![Coverage](docs/badges/coverage.svg)](#test-coverage) [![Mutation](docs/badges/mutation.svg)](#mutation-testing)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

DhrLang is a modern, statically typed, object-oriented programming language with a concise English‑core token set (`num`, `duo`, `sab`, `kya`, `ek`, `kaam`) inspired by earlier Hindi-localized experimentation. Focus: clarity, pedagogy, and strong static analysis while retaining culturally inspired naming roots.

## Quick Links
- Getting Started: docs/GETTING_STARTED.md
- Installation: see below
- Run a program: Running DhrLang Programs
- Language Spec: SPEC.md
- Standard Library: STDLIB.md
- Examples: input/
- Changelog: CHANGELOG.md
- Release Checklist: RELEASE_CHECKLIST.md
- Bytecode / IR Plan: design/bytecode-roadmap.md
    - Bytecode Format: design/bytecode-format.md
- Editor Integration: VS Code Extension section

## Features

- **Concise Core Tokens**: Minimal memorable keywords: `num` (int), `duo` (float), `sab` (string), `kya` (boolean), `ek` (char), `kaam` (function/method)
- **Object-Oriented Programming**: Classes, inheritance (`extends`), access control, static members
- **Static Typing**: Strong compile-time type checking with generic substitution
- **Arrays**: Multi-dimensional arrays with bounds & type safety
- **Generics**: Type parameters on classes/methods with full substitution + diagnostics
- **Implicit Field Access**: Instance method identifier resolution falls back to fields safely
- **String & Array Utilities**: Core built-ins (`printLine`, `arrayLength`, substring helpers, etc.)
- **Structured Error Handling**: `try/catch/finally` (including typed catches)
- **Evaluator Architecture**: Central semantic executor for maintainability & clear runtime rules
- **Static Initialization Safety**: Detects forward references & dependency cycles early

### Currently Unsupported / Experimental
Some constructs (advanced exception types, modules, concurrency) are either experimental or not implemented yet. See SPEC.md for authoritative status markers; see design/bytecode-roadmap.md for backend evolution.

## Language Syntax

### Data Types
- `num` - Integer numbers
- `duo` - Floating-point numbers  
- `sab` - Strings
- `kya` - Boolean values
- `ek` - Characters

### Keywords
- `class` - Class declaration
- `kaam` - Function/method declaration
- `if`/`else` - Conditional statements
- `while`/`for` - Loop statements
- `try`/`catch`/`finally` - Exception handling
- `return` - Return statement
- `new` - Object instantiation
- `this`/`super` - Object references
- `extends`/`implements` - Inheritance
- `static` - Static members
- `private`/`protected`/`public` - Access modifiers

## Installation

### Prerequisites
- Java 17 or higher
- Gradle 8.0 or higher
 - Tested on Java 17 and 21 in CI (Ubuntu); local development verified on Windows.

### Install (Download Release)
- Download the latest release ZIP from: https://github.com/dhruv-15-03/DhrLang/releases/latest
- Extract the ZIP; inside `lib/` you’ll find a runnable fat JAR (shadow JAR) with `Main-Class` set.

Linux/macOS:
```bash
cd path/to/DhrLang-<version>/lib
java -jar DhrLang-<version>.jar input/sample.dhr
```

Windows (PowerShell):
```powershell
Set-Location path\to\DhrLang-<version>\lib
java -jar DhrLang-<version>.jar input\sample.dhr
```

Alternatively, download just the JAR directly from Releases and run:
```powershell
# Windows
java -jar DhrLang-1.1.3.jar path\to\program.dhr

# Linux/macOS
java -jar DhrLang-1.1.3.jar path/to/program.dhr
```

### CLI Options
```bash
--help           Show usage and options
--version        Print version (e.g., "DhrLang version 1.1.3")
--json           Output diagnostics as JSON (see JSON Diagnostics below)
--time           Show phase timings (lex/parse/type/exec)
--no-color       Disable ANSI colors in diagnostics
--backend=ast|ir|bytecode  (select execution backend)
```

Runtime safety flags (JVM system properties):
- `dhrlang.backend.maxSteps` — instruction step limit (IR + bytecode)
- `dhrlang.bytecode.untrusted=true` — enables conservative validation + limits for bytecode execution
- `dhrlang.bytecode.strictEntry` — require an entrypoint (`Main.main` or any `*.main`)

### JSON Diagnostics
DhrLang can emit machine-readable diagnostics in JSON format for tooling integration:

```powershell
java -jar DhrLang-1.1.3.jar --json --time program.dhr
```

Output conforms to `diagnostics.schema.json` (JSON Schema v7) with:
- `schemaVersion`: Currently `1` (stable contract)
- `timings`: Phase timings in milliseconds (lex, parse, type, exec, total)
- `errors`: Array of error objects with file, line, column, type, message, hint, sourceLine
- `warnings`: Array of warning objects with the same structure

Exit codes with `--json`:
- 0: success (or warnings only)
- 1 (or 65): compile-time errors
- 2: runtime/system errors

### Building from Source
Linux/macOS:
```bash
git clone https://github.com/dhruv-15-03/DhrLang.git
cd DhrLang
./gradlew build
```

Windows (PowerShell):
```powershell
git clone https://github.com/dhruv-15-03/DhrLang.git
Set-Location DhrLang
./gradlew.bat build
```

To build the production fat JAR:
```powershell
./gradlew.bat shadowJar
# Output: build/libs/DhrLang-1.1.3.jar
```

### Running DhrLang Programs
Linux/macOS:
```bash
# Using Gradle
./gradlew run --args="path/to/your/file.dhr"

# Using Java directly (after build)
java -cp build/classes/java/main dhrlang.Main path/to/your/file.dhr

# Using the fat JAR
java -jar build/libs/DhrLang-1.1.3.jar path/to/your/file.dhr
```

Windows (PowerShell):
```powershell
# Using Gradle
./gradlew.bat run --args="path/to/your/file.dhr"

# Using Java directly (after build)
java -cp build/classes/java/main dhrlang.Main path/to/your/file.dhr

# Using the fat JAR
java -jar build\libs\DhrLang-1.1.3.jar path\to\your\file.dhr
```

### CLI exit codes
Exit codes:
- 0: success
- 1: compile-time error(s)
- 2: runtime/system error

## Quick Start

### Hello World
```dhrlang
class Main {
    static kaam main() {
        printLine("Hello, DhrLang!");
    }
}
```

### Variables and Basic Operations
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

### Object-Oriented Programming
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

### Multi-dimensional Arrays
```dhrlang
class NDArrayDemo {
    static kaam main(){
        // Allocate a 2D array 3x4 of num
        num[][] m = new num[3][4];
        // Set a few elements
        m[0][0] = 1; m[2][3] = 7;
        // Nested iteration
        for(num i=0; i<3; i++){
            for(num j=0; j<4; j++){
                print(" "+m[i][j]);
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
- Indexing is bounds-checked: negative or >= length raises an index error.
- Allocation requires non-negative sizes; very large sizes are rejected.
- Element defaults follow type defaults (numbers→0, duo→0.0, kya→false, references→null).

### Exception Handling (Basic)
```dhrlang
class ErrorExample {
    static kaam main() {
        try {
            // Risky operation
            throw "boom";
        } catch(err) {
            printLine("Caught: " + err);
        } finally {
            printLine("Cleanup");
        }
    }
}
```

## Language Features

### Comprehensive Type System
DhrLang features a strong static type system that catches errors at compile time:
- Explicit static types for variable declarations (no local type inference yet)
- Generics with type parameter substitution and clear diagnostics
- Array type safety, including multi-dimensional arrays
- Simple method model (no overloading; duplicate names are rejected)

### Core Built-ins
- String utilities (selected: `charAt`, `replace`, `substring` – scope documented in SPEC)
- Array operations (`arrayLength`, indexing, iteration patterns)
- Output (`print`, `printLine`)
- Basic math / operator semantics via core evaluator

### Diagnostics
- Source-mapped error messages with codes & hints
- Colorized terminal output (ANSI) for clarity
- Categories: parser, typechecker, runtime (documented in `ERROR_CODES.md`)

## Development

### Project Structure
```
src/
├── main/java/dhrlang/
│   ├── Main.java              # Main entry point
│   ├── lexer/                 # Lexical analysis
│   ├── parser/                # Syntax analysis
│   ├── ast/                   # Abstract Syntax Tree
│   ├── typechecker/           # Type checking
│   ├── interpreter/           # Code execution
│   ├── error/                 # Error reporting
│   └── stdlib/                # Standard library
└── test/java/dhrlang/         # Test suite
```

### Running Tests
```bash
# Run all tests
./gradlew test

# Run with detailed output
./gradlew test --info

# Run specific test class
./gradlew test --tests "dhrlang.DhrLangCompilerTest"
```

### Test Coverage & Mutation Testing
Jacoco + PIT run in CI. Badges reflect instruction coverage & mutation kill ratio. Thresholds will ratchet upwards over time.

### Performance Baselines
Micro-benchmarks in `bench/` execute with `--time` capturing phase timings. `bench/baseline.json` stores calibrated totals; `benchCompare` fails the build if any benchmark regresses by >50% (tolerance may tighten). Bench runs are advisory (micro scale, not full perf suite).

The project includes coverage for:
- Lexical analysis (tokenization)
- Parsing (syntax analysis)
- Type checking (semantic analysis)
- Runtime execution (interpretation)
- Error handling and reporting

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Setup
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes and add tests
4. Ensure all tests pass (`./gradlew test`)
5. Commit your changes (`git commit -am 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

## Benchmarks
Run micro benchmarks:
```bash
./gradlew bench
```
Outputs `build/bench/bench-results.json` (JSON array). Each entry:
```jsonc
{
    "file": "fib.dhr",
    "timings": { "lexMs": <n>, "parseMs": <n>, "typeMs": <n>, "execMs": <n>, "totalMs": <n> },
    "errorCount": 0,
    "warningCount": 0
}
```
Compare with baseline:
```bash
./gradlew benchCompare
```
Add / update baseline after validating stability:
```bash
cp build/bench/bench-results.json bench/baseline.json
```
Bench harness is deterministic and multi-line JSON robust; future: add variance & dual-backend (AST vs IR) measurements.

## Examples

Check out the `input/` directory for comprehensive examples:
- `test_basic_syntax.dhr` - Basic language features
- `test_oop_features.dhr` - Object-oriented programming
- `test_arrays.dhr` - Array operations
- `test_exceptions.dhr` - Exception handling
- `test_strings.dhr` - String manipulation
- `test_static_methods.dhr` - Static methods and utilities
 - `advanced_features_test.dhr` - Demonstrates generics syntax and substitution (fully supported)
 - `complete_feature_demo.dhr` - Comprehensive showcase including generics, multi-dimensional arrays, and implicit field access
 - `advanced_edge_cases.dhr` - Stress tests for multi-dimensional arrays and generics
 - `duplicate_error_test.dhr`, `parser_error_test.dhr` - Intentional negative tests to verify diagnostics

## Roadmap & Backend
High-level roadmap lives in design/bytecode-roadmap.md (IR + bytecode path).

Backends:
- `--backend=ast` is the default and is useful for debugging.
- `--backend=ir` and `--backend=bytecode` are intended to be semantically equivalent to AST for the implemented language feature set.
- Backend selection is authoritative: IR/bytecode runs do not fall back to AST.

Implemented in IR/Bytecode:
- Literals, locals (load/store), arithmetic (+ - * /), comparisons (== != < <= > >=)
- Control-flow: if/else, while, short-circuit `&&`/`||`, `break`/`continue`
- print/printLine, return (with/without value), unary minus/! and postfix ++/-- on locals
- Arrays (literal/new/load/store/`arrayLength`), static function calls
- Static fields + instance fields
- Exceptions: `throw`, `try/catch/finally` (including typed catches)

Security note for running untrusted code:
- Prefer `--backend=bytecode` and run with `-Ddhrlang.bytecode.untrusted=true` to enable strict verification and conservative resource limits.

## Feature Status
- Generics: Fully implemented, including type parameter substitution and diagnostics
- Multi-dimensional arrays: Fully supported in parser, typechecker, and evaluator
- Implicit field access: Unqualified variable/assignment inside instance methods resolves to fields, with generic substitution and access checks
- Comprehensive diagnostics: Structured error codes, JSON output, and actionable hints
- CI/Quality: Jacoco, PIT, CodeQL, Dependabot integrated

## Diagnostics Quick Guide
- Cannot access private/protected member → ACCESS_MODIFIER
- Wrong number of generic type arguments → GENERIC_ARITY
- Type doesn’t match expected (incl. after generic substitution) → TYPE_MISMATCH
- Name not found (and static method context doesn’t resolve instance fields) → UNDECLARED_IDENTIFIER
- Array index/size invalid or allocation too large → BOUNDS_VIOLATION
- Static field reads a later-declared static field (same class) → STATIC_FORWARD_REFERENCE
- Static field initializers form a dependency cycle → STATIC_INIT_CYCLE

## CI Notes
- CodeQL: If GitHub Code Scanning is not enabled for your repository, the CodeQL job will run but its upload step is marked non-blocking and won’t fail CI. To enable full CodeQL results (including alerts in the Security tab), turn on Code Scanning in your repository settings.

## Planned Features
- Package management system
- Standard library expansion
- IDE support and language server
- Debugging capabilities
- Module system
- Concurrency support
- Foreign function interface
- JIT compilation
- WebAssembly target
- Advanced optimization
- Cross-platform GUI framework

## VS Code Extension (Editor Integration)

The official VS Code extension (version aligned with core releases) provides:
* Syntax highlighting for current English-core tokens
* Snippets (main class, loops, methods, printLine, try/catch skeleton)
* Run / Compile commands with status bar JAR detection
* Optional inline diagnostics (enable via settings)

Manual install (until marketplace listing active):
1. Download the latest `dhrlang-vscode-<version>.vsix` from the release assets
2. In VS Code: Command Palette → "Extensions: Install from VSIX..."
3. Select the file; open a `.dhr` file to activate

Settings preview (add to `settings.json` as needed):
```jsonc
{
    "dhrlang.autoDetectJar": true,
    "dhrlang.jarPath": "",
    "dhrlang.enableAutoCompletion": true,
    "dhrlang.enableErrorSquiggles": true
}
```

If auto detection fails, set `dhrlang.jarPath` directly to your built `DhrLang.jar`.

## Command Line Interface

After building, run programs via:
```bash
java -jar DhrLang-<version>.jar path/to/file.dhr
```

Flags:
| Flag | Description |
|------|-------------|
| `--help`, `-h` | Print usage and exit |
| `--version`, `-v` | Print version (manifest Implementation-Version) |
| `--json` | Emit diagnostics JSON (always includes `schemaVersion` and `timings` when `--time`) |
| `--time` | Show phase timings and embed timings in JSON |
| `--no-color` | Disable ANSI color output |
| `--backend=ast|ir|bytecode` | Select execution backend |
| `--emit-ir` | Dump lowered IR (JSON) for debugging |
| `--emit-bc` | Write compiled bytecode to build/bytecode/Main.dbc |

Behavior:
* If no file is specified, defaults to `input/sample.dhr`.
* Exit codes: `0` success or warnings only, `1` compile error, `2` runtime/system error, `65` JSON diagnostics emission with errors.
* Future flags under design: additional optimization/debug flags.
* Diagnostics schema: see `diagnostics.schema.json` (always includes `schemaVersion` and timings object; timings may be zero if error short-circuits).


DhrLang is designed for:
- **Fast compilation**: Efficient lexing, parsing, and type checking
- **Clear error reporting**: Detailed diagnostics with helpful hints
- **Educational use**: Clean syntax suitable for teaching programming concepts
- **Extensibility**: Modular architecture for easy feature addition

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by modern language design principles
- Built with Java for cross-platform compatibility
- Inspired by earlier efforts to make programming approachable with culturally resonant naming
- Community-driven development approach

## Contact

- GitHub: [@dhruv-15-03](https://github.com/dhruv-15-03)
- Project Link: [https://github.com/dhruv-15-03/DhrLang](https://github.com/dhruv-15-03/DhrLang)

---

*DhrLang - A compact, statically typed educational & exploratory language* 🚀
