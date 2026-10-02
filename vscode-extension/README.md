# DhrLang VS Code Extension

Modern language tooling for the DhrLang language: syntax highlighting, snippets, run / compile commands, diagnostics, and quality-of-life helpers.

> This README now reflects the current Englishâ€‘core token set (num, duo, sab, kya, any, kaam, class, static, etc.). Legacy Hindi keyword forms were removed from the language docs and are no longer advertised here.

## âœ¨ Features

### ðŸš€ Zero Config Run
* **Bundled Compiler**: The extension includes the DhrLang compiler. Just install and run!
* **No Setup**: You don't need to download the JAR separately.
* **Requirement**: Ensure **Java 17+** is installed on your system.

### ðŸŽ¨ Syntax Highlighting
* Accurate grammar for `.dhr` files
* Highlights keywords, literals, strings, comments, classes, functions, numbers
* Updated for the simplified Englishâ€‘core tokens

### ðŸ§  Full Language Server (go-to-def, hover, rename, references, completion, diagnostics)
* The extension now launches the real DhrLang Language Server (`java -jar DhrLang.jar --lsp`) over stdio
  via `vscode-languageclient`, instead of the old static/in-process providers.
* **Go to Definition** and **Find All References** across the open file
* **Hover** with symbol/type information
* **Rename Symbol** (`F2`) with `prepareRename` support
* **Scope-aware Completion** — suggests symbols actually in scope at the cursor (locals, parameters,
  class members), not a flat keyword dump
* **Diagnostics** (`textDocument/publishDiagnostics`) with full-span ranges and `DHR-Exxx` error codes
* If Java or `DhrLang.jar` can't be found, the extension falls back to the older static
  keyword-completion/hover and shell-out diagnostics so it still degrades gracefully instead of
  crashing on activation (governed by `dhrlang.enableAutoCompletion` / `dhrlang.enableErrorSquiggles`,
  which now only apply to that fallback path).

### ðŸ§© Snippets
* Snippets for: main entry, class, method, if / if-else, while, for, foreach, arrays, printLine, try/catch skeleton

### ðŸš€ Run & Compile
* `Ctrl+F5` Run current DhrLang file
* `Ctrl+Shift+B` Compile / diagnostics
* Status bar indicator (auto jar detection state)
* Error squiggles (when enabled) sourced from compiler output

### âš™ï¸ Configuration (Settings)
| Setting | Description |
|---------|-------------|
| `dhrlang.jarPath` | Explicit path to `DhrLang.jar` (leave blank for auto detection) |
| `dhrlang.autoDetectJar` | Search workspace (root & `lib/`) for the JAR automatically |
| `dhrlang.javaPath` | Java runtime executable (default `java`) |
| `dhrlang.enableAutoCompletion` | Toggle fallback keyword/snippet completion (only used if the Language Server can't start) |
| `dhrlang.enableErrorSquiggles` | Toggle fallback inline diagnostics (only used if the Language Server can't start) |
| `dhrlang.outputEncoding` | Output encoding (`utf8` / `utf16`) |

## ðŸ›  Installation

### From Marketplace
1. Open VS Code
2. Extensions (Ctrl+Shift+X)
3. Search: `DhrLang Support` (publisher: `EnggWithDhruv`)
4. Install

### Manual (VSIX) - Recommended
1. Download `dhrlang-vscode-<version>.vsix` from the [GitHub Releases](https://github.com/dhruv-15-03/DhrLang/releases/latest) page.
2. Open VS Code.
3. Press `Ctrl+Shift+P` (Command Palette).
4. Type "Install from VSIX" and select **Extensions: Install from VSIX...**.
5. Select the downloaded `.vsix` file.

## ðŸš§ Packaging / Updating the VSIX

Requires JDK 17 and Node.js 22+. From the repository root, run
`.\gradlew.bat stageCompiler` (or `./gradlew stageCompiler` on Linux/macOS).
Then, in `vscode-extension`, run `npm ci`, `npm run test:packaging`, and
`npm run package`, stopping if any command fails.

The package script compiles the extension, embeds the exact staged compiler and
verifies its dependencies, version, publisher, entry point, README, license and icon.
It writes the VSIX, standalone JAR and SHA-256 manifest into `build/release/`.
Use `npm run verify:package` to verify those files again.

Marketplace publication consumes this same VSIX from the compiler's GitHub release;
it never runs `vsce publish patch` or rebuilds another package under the same version.
Follow [the release checklist](../RELEASE_CHECKLIST.md) for the tag order and checks.

## ðŸš€ Quick Start

Create `Main.dhr`:
```dhrlang
class Main {
  static kaam main() {
    printLine("Hello, DhrLang!");
  }
}
```
Press `Ctrl+F5` to run (jar must be resolvable or `dhrlang.jarPath` set).

## ðŸ”‘ Core Tokens (Current)

| Category | Tokens |
|----------|--------|
| Types | `num`, `duo`, `sab`, `kya`, `any` |
| Flow | `if`, `else`, `for`, `while`, `return` |
| OOP | `class`, `extends`, `static` |
| Functions | `kaam` (method/function indicator) |
| Builtâ€‘ins (examples) | `printLine`, `arrayLength` |

Language feature status is tracked in the main docs (README/SPEC).

## ðŸ“¦ Snippet Prefix Samples

| Prefix | Expands To |
|--------|------------|
| `mainc` | Main class + entry method skeleton |
| `main` | Only `main` method skeleton |
| `pl` | `printLine("...");` |
| `if` / `ife` | If / Ifâ€‘Else block |
| `for` / `foreach` | Loop templates |
| `while` | While loop |
| `class` | Class skeleton |
| `kaam` | Method skeleton |

## ðŸ§ª Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| Extension not found in search | Not yet published or caching delay | Install via VSIX manually |
| Run command says JAR not found | Auto-detect failed | Set `dhrlang.jarPath` explicitly |
| No completions | Auto completion disabled | Enable `dhrlang.enableAutoCompletion` |
| No diagnostics | Error squiggles off or compile failed | Enable setting / check terminal output |

## ðŸ“š Links
* Core README â€“ language overview
* Tutorials â€“ stepwise examples
* Examples â€“ curated sample programs
* Issues / Discussions â€“ feedback & support

## ðŸ¤ Contributing
See the project CONTRIBUTING guidelines. PRs to improve grammar, diagnostics, or snippets are welcome.

## ðŸ“„ License
MIT License â€“ see repository root `LICENSE`.

---

Happy hacking with DhrLang!