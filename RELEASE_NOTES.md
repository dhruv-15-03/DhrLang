# 🚀 DhrLang Release Notes

## v1.1.8 - Hotfix Release *(Jan 2026)*

### 🔧 Fixes
- **VS Code Extension**: Fixed PowerShell command execution issue where quoted paths caused "Unexpected token" errors.

## v1.1.6 - Hotfix Release *(Jan 2026)*

### 🔧 Fixes
- **Extension Bundle**: Fixed build pipeline to correctly bundle the compiler JAR in the VS Code extension.

## v1.1.5 - Hotfix Release *(Jan 2026)*

### 🔧 Fixes
- **Release Pipeline**: Resolved GitHub Packages conflict and credential issues.
- **Extension Bundle**: Ensured correct bundling of the compiler JAR in the VS Code extension.

## v1.1.3 - Current Release Line *(Nov 2025)*

### 🚀 Zero Config Experience
- **Bundled Compiler**: The VS Code extension now includes the compiler JAR. Users can just install the extension and run code immediately without manual setup.
- **Simplified Examples**: Cleaned up the repository to focus on high-quality, working examples in `input/`.

### ✅ Runtime & Backend Updates
- IR and bytecode backends are supported via `--backend=ir` / `--backend=bytecode`.
- Backend selection is authoritative (no AST fallback).
- Bytecode format is DHBC v2 (see design/bytecode-format.md).

### 🔒 Safety / Hardening
- Bytecode VM validates bytecode before execution (bounds/indices/control-flow constraints).
- Untrusted mode available via `-Ddhrlang.bytecode.untrusted=true` with conservative caps.
- Shared instruction step cap available via `-Ddhrlang.backend.maxSteps=<n>`.

## v1.0.1 - Documentation Fix Release *(September 28, 2025)*

### 🔧 **Critical Documentation Fixes**

#### ❌ **Issues Resolved:**
- **Syntax Mismatch**: Fixed critical mismatch between documentation examples and actual compiler syntax
- **Legacy Hindi Keywords**: Corrected non-existent Hindi keywords (`मुख्य`, `प्रिंट`) to actual DhrLang tokens (`class`, `static kaam main`, `printLine`)
- **Class Structure**: Added missing required class structure (`static kaam main()`)
- **Function Calls**: Fixed `printLine()` function calls to include required arguments
- **Type System**: Updated type examples to use correct keywords (`num`, `sab`, `duo`, `kya`)

#### ✅ **Improvements Added:**
- All code examples now **compile and run successfully**
- Professional user experience validation with working test programs
- Comprehensive demonstration programs included:
  - `hello.dhr` - Corrected first program example
  - `professional-demo.dhr` - Complete feature showcase
  - `banking-demo.dhr` - Real-world application example
- Updated VS Code extension status to reflect availability
- Added professional documentation fix report

#### 🎯 **Impact:**
New users can now follow the documentation from start to finish without encountering compilation errors. All examples have been tested and validated to work correctly with the DhrLang compiler.

---

## v1.0.0 - Official Release *(September 28, 2025)*

### 🎉 **Major Release - Production Ready**

#### ✅ **Core Language Features:**
- **Object-Oriented Programming**: Classes, inheritance, encapsulation
- **Type System**: `num` (integer), `sab` (string), `duo` (decimal), `kya` (boolean)
- **Access Control**: `private`, `protected`, `public` modifiers
- **Exception Handling**: `try`/`catch`/`finally` blocks
- **Control Flow**: `if`/`else`, `for`/`while` loops, conditionals
- **Functions**: Static and instance methods with `kaam` keyword

#### 🛠️ **Development Tools:**
- **VS Code Extension**: Syntax highlighting, IntelliSense, auto-completion
- **Command-Line Compiler**: `DhrLang-<version>.jar` with professional error messages
- **Run Commands**: Integrated VS Code shortcuts (Ctrl+F5, Ctrl+Shift+B)
- **Code Snippets**: Common programming patterns and templates

#### 📚 **Professional Documentation:**
- **GETTING_STARTED.md**: Complete beginner's guide
- **TUTORIALS.md**: 12 comprehensive tutorials from basic to advanced
- **EXAMPLES.md**: Real-world applications (banking system, calculator)
- **SPEC.md**: Complete language specification
- **API Documentation**: Comprehensive reference materials

#### 🚀 **Distribution & CI/CD:**
- **GitHub Actions**: Automated build, test, and release workflows
- **Multi-Platform Support**: Windows, macOS, Linux compatibility
- **Package Managers**: Ready for Homebrew, Chocolatey, Snap distribution
- **Docker Support**: Containerization ready for cloud deployment
- **VS Code Marketplace**: Extension ready for publication

#### 🔍 **Quality Assurance:**
- **Comprehensive Testing**: All features tested with automated CI/CD
- **Error Handling**: Professional error messages with helpful hints
- **Code Quality**: PMD, Checkstyle, SpotBugs integration
- **Performance**: Optimized compilation and runtime performance

---

## 🎯 **Getting Started**

### **Quick Installation:**
```bash
# Download the matching release asset from GitHub Releases
java -jar DhrLang-<version>.jar hello.dhr

# Install VS Code Extension
code --install-extension dhrlang-vscode-<version>.vsix
```

### **First Program:**
```dhrlang
class HelloWorld {
    static kaam main() {
        printLine("Hello, DhrLang!");
        return;
    }
}
```

---

## 🤝 **Community & Support**

- **📖 Documentation**: [GitHub Repository](https://github.com/dhruv-15-03/DhrLang)
- **🐛 Bug Reports**: [GitHub Issues](https://github.com/dhruv-15-03/DhrLang/issues)
- **💬 Discussions**: [GitHub Discussions](https://github.com/dhruv-15-03/DhrLang/discussions)
- **🔄 Contributing**: See [CONTRIBUTING.md](CONTRIBUTING.md)

---

**DhrLang is now officially ready for production use! 🎉**