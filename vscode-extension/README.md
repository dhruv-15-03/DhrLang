# DhrLang VS Code Extension

This extension provides comprehensive support for the DhrLang programming language in Visual Studio Code.

## Features

### 🎨 Syntax Highlighting
- Full syntax highlighting for DhrLang (.dhr) files
- Support for Hindi keywords and English equivalents
- Proper highlighting for strings, comments, numbers, and operators

### 📝 Code Completion
- Intelligent auto-completion for Hindi keywords
- Snippet templates for common constructs
- Type suggestions for variables and functions

### 🚀 Run & Compile
- **Ctrl+F5**: Run DhrLang files directly
- **Ctrl+Shift+B**: Compile and check for errors
- Integrated terminal output
- Error highlighting and diagnostics

### 💡 IntelliSense Features
- Hover information for Hindi keywords
- Code snippets for faster development
- Context-aware suggestions

### 🛠️ Customization
Configure the extension through VS Code settings:
- `dhrlang.jarPath`: Path to DhrLang.jar file
- `dhrlang.javaPath`: Path to Java executable
- `dhrlang.enableAutoCompletion`: Enable/disable auto-completion
- `dhrlang.enableErrorSquiggles`: Enable/disable error highlighting

## Installation

### From VS Code Marketplace
1. Open VS Code
2. Go to Extensions (Ctrl+Shift+X)
3. Search for "DhrLang Support"
4. Click Install

### Manual Installation
1. Download the `.vsix` file from releases
2. Open VS Code
3. Run `code --install-extension dhrlang-vscode-1.0.0.vsix`

## Getting Started

1. **Create a new file** with `.dhr` extension
2. **Type `main`** and press Tab for main function template
3. **Write your code** using Hindi keywords:
   ```dhrlang
   मुख्य() {
       प्रिंट("नमस्ते, DhrLang!");
   }
   ```
4. **Press Ctrl+F5** to run your program

## Code Snippets

Type these prefixes and press Tab:

| Prefix | Snippet | Description |
|--------|---------|-------------|
| `main` | Main function | `मुख्य() { ... }` |
| `print` | Print statement | `प्रिंट("...");` |
| `if` | If condition | `अगर (...) { ... }` |
| `while` | While loop | `जबकि (...) { ... }` |
| `for` | For loop | `के लिए (...) { ... }` |
| `class` | Class definition | `क्लास ... { ... }` |
| `try` | Try-catch block | `कोशिश { ... } पकड़ना { ... }` |

## Hindi Keywords Reference

### Control Flow
- `अगर` - if
- `नहीं तो` - else
- `जबकि` - while
- `के लिए` - for
- `स्विच` - switch
- `केस` - case

### Data Types
- `संख्या` - integer
- `दशमलव` - decimal/float
- `स्ट्रिंग` - string
- `बूलियन` - boolean
- `चार` - character

### OOP Keywords
- `क्लास` - class
- `निजी` - private
- `संरक्षित` - protected
- `सार्वजनिक` - public
- `स्टैटिक` - static

### Exception Handling
- `कोशिश` - try
- `पकड़ना` - catch
- `अंततः` - finally

## Requirements

- **Java 17 or higher** installed on your system
- **DhrLang.jar** compiler (download from [releases](https://github.com/dhruv-15-03/DhrLang/releases))

## Configuration

Add these settings to your VS Code `settings.json`:

```json
{
  "dhrlang.jarPath": "/path/to/DhrLang.jar",
  "dhrlang.javaPath": "java",
  "dhrlang.enableAutoCompletion": true,
  "dhrlang.enableErrorSquiggles": true
}
```

## Commands

- **DhrLang: Run File** - Execute the current .dhr file
- **DhrLang: Compile File** - Check for compilation errors
- **DhrLang: Show Help** - Display language help

## Support

- 📖 [Documentation](https://github.com/dhruv-15-03/DhrLang/blob/main/README.md)
- 🎓 [Tutorials](https://github.com/dhruv-15-03/DhrLang/blob/main/TUTORIALS.md)
- 💾 [Examples](https://github.com/dhruv-15-03/DhrLang/blob/main/EXAMPLES.md)
- 🐛 [Report Issues](https://github.com/dhruv-15-03/DhrLang/issues)
- 💬 [Discussions](https://github.com/dhruv-15-03/DhrLang/discussions)

## Contributing

Contributions are welcome! Please see our [contributing guidelines](https://github.com/dhruv-15-03/DhrLang/blob/main/CONTRIBUTING.md).

## License

This extension is licensed under the [MIT License](https://github.com/dhruv-15-03/DhrLang/blob/main/LICENSE).

---

**Enjoy programming in Hindi with DhrLang! हिंदी में प्रोग्रामिंग का आनंद लें! 🇮🇳**