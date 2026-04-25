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

### ðŸ§  Completions & Snippets
* Contextual keyword & snippet suggestions (main class, methods, loops, conditionals)
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
| `dhrlang.enableAutoCompletion` | Toggle keyword/snippet completion |
| `dhrlang.enableErrorSquiggles` | Toggle inline diagnostics |
| `dhrlang.outputEncoding` | Output encoding (`utf8` / `utf16`) |

## ðŸ›  Installation

### From Marketplace (Coming Soon)
*Once published to the VS Code Marketplace:*
1. Open VS Code
2. Extensions (Ctrl+Shift+X)
3. Search: `DhrLang Support` (publisher: `dhruv-15-03`)
4. Install

### Manual (VSIX) - Recommended
1. Download `dhrlang-vscode-1.1.8.vsix` from the [GitHub Releases](https://github.com/dhruv-15-03/DhrLang/releases/latest) page.
2. Open VS Code.
3. Press `Ctrl+Shift+P` (Command Palette).
4. Type "Install from VSIX" and select **Extensions: Install from VSIX...**.
5. Select the downloaded `.vsix` file.

## ðŸš§ Packaging / Updating the VSIX

The repository currently contains an older `dhrlang-vscode-3.0.0.vsix`. Rebuild to match new version:

1. In `vscode-extension/` run (PowerShell):
   - Install dependencies: `npm ci`
   - (If not installed) `npm install -g @vscode/vsce`
2. Compile: `npm run compile`
3. Package: `vsce package` (produces `dhrlang-vscode-1.1.4.vsix`)
4. (Optional) Publish: `vsce publish patch` (requires a Personal Access Token and verified publisher)

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