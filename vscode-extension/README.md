# DhrLang VS Code Extension

Modern language tooling for the DhrLang language: syntax highlighting, snippets, run / compile commands, diagnostics, and quality-of-life helpers.

> This README now reflects the current English‑core token set (num, duo, sab, kya, any, kaam, class, static, etc.). Legacy Hindi keyword forms were removed from the language docs and are no longer advertised here.

## ✨ Features

### 🎨 Syntax Highlighting
* Accurate grammar for `.dhr` files
* Highlights keywords, literals, strings, comments, classes, functions, numbers
* Updated for the simplified English‑core tokens

### 🧠 Completions & Snippets
* Contextual keyword & snippet suggestions (main class, methods, loops, conditionals)
* Snippets for: main entry, class, method, if / if-else, while, for, foreach, arrays, printLine, experimental try/catch skeleton

### 🚀 Run & Compile
* `Ctrl+F5` Run current DhrLang file
* `Ctrl+Shift+B` Compile / diagnostics
* Status bar indicator (auto jar detection state)
* Error squiggles (when enabled) sourced from compiler output

### ⚙️ Configuration (Settings)
| Setting | Description |
|---------|-------------|
| `dhrlang.jarPath` | Explicit path to `DhrLang.jar` (leave blank for auto detection) |
| `dhrlang.autoDetectJar` | Search workspace (root & `lib/`) for the JAR automatically |
| `dhrlang.javaPath` | Java runtime executable (default `java`) |
| `dhrlang.enableAutoCompletion` | Toggle keyword/snippet completion |
| `dhrlang.enableErrorSquiggles` | Toggle inline diagnostics |
| `dhrlang.outputEncoding` | Output encoding (`utf8` / `utf16`) |

## 🛠 Installation

### From Marketplace (Recommended)
1. Open VS Code
2. Extensions (Ctrl+Shift+X)
3. Search: `DhrLang Support` (publisher: `dhruv-15-03`)
4. Install

### Manual (VSIX)
If Marketplace listing isn’t live yet or you are testing a local build:
1. Build the extension (see Packaging below) to produce `dhrlang-vscode-1.1.2.vsix`
2. VS Code Command Palette: “Extensions: Install from VSIX...” and select the file
   - OR from shell: `code --install-extension dhrlang-vscode-1.1.2.vsix`

## 🚧 Packaging / Updating the VSIX

The repository currently contains an older `dhrlang-vscode-1.0.0.vsix`. Rebuild to match new version:

1. In `vscode-extension/` run (PowerShell):
   - Install dependencies: `npm ci`
   - (If not installed) `npm install -g @vscode/vsce`
2. Compile: `npm run compile`
3. Package: `vsce package` (produces `dhrlang-vscode-1.1.2.vsix`)
4. (Optional) Publish: `vsce publish patch` (requires a Personal Access Token and verified publisher)

## 🚀 Quick Start

Create `Main.dhr`:
```dhrlang
class Main {
  static kaam main() {
    printLine("Hello, DhrLang!");
  }
}
```
Press `Ctrl+F5` to run (jar must be resolvable or `dhrlang.jarPath` set).

## 🔑 Core Tokens (Current)

| Category | Tokens |
|----------|--------|
| Types | `num`, `duo`, `sab`, `kya`, `any` |
| Flow | `if`, `else`, `for`, `while`, `return` |
| OOP | `class`, `extends`, `static` |
| Functions | `kaam` (method/function indicator) |
| Built‑ins (examples) | `printLine`, `arrayLength` |

Experimental / evolving constructs (like advanced exceptions) are marked in main docs.

## 📦 Snippet Prefix Samples

| Prefix | Expands To |
|--------|------------|
| `mainc` | Main class + entry method skeleton |
| `main` | Only `main` method skeleton |
| `pl` | `printLine("...");` |
| `if` / `ife` | If / If‑Else block |
| `for` / `foreach` | Loop templates |
| `while` | While loop |
| `class` | Class skeleton |
| `kaam` | Method skeleton |

## 🧪 Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| Extension not found in search | Not yet published or caching delay | Install via VSIX manually |
| Run command says JAR not found | Auto-detect failed | Set `dhrlang.jarPath` explicitly |
| No completions | Auto completion disabled | Enable `dhrlang.enableAutoCompletion` |
| No diagnostics | Error squiggles off or compile failed | Enable setting / check terminal output |

## 📚 Links
* Core README – language overview
* Tutorials – stepwise examples
* Examples – curated sample programs
* Issues / Discussions – feedback & support

## 🤝 Contributing
See the project CONTRIBUTING guidelines. PRs to improve grammar, diagnostics, or snippets are welcome.

## 📄 License
MIT License – see repository root `LICENSE`.

---

Happy hacking with DhrLang!