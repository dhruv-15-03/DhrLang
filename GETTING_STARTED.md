# Getting Started with DhrLang

DhrLang currently ships with an **English-core token set** (`class`, `static`, `kaam`, `num`, `sab`, `duo`, `kya`, `ek`, `any`, etc.). Earlier experimental drafts referenced direct Hindi keywords (e.g. `मुख्य`, `प्रिंट`, `अगर`) but those are **not accepted by the present compiler**. Future bilingual support may re‑introduce them behind a compatibility flag. This guide reflects the syntax that actually parses today.

## Quick Start

### Option 1: Download Release (Recommended)
1. Go to [Releases](https://github.com/dhruv-15-03/DhrLang/releases/latest)
2. Download `DhrLang-1.1.3.jar` (fat JAR with all dependencies)
3. Run: `java -jar DhrLang-1.1.3.jar examples/sample.dhr`

**Requirements**: Java 17 or higher

### Option 2: Build from Source
```bash
git clone https://github.com/dhruv-15-03/DhrLang.git
cd DhrLang
./gradlew shadowJar
java -jar build/libs/DhrLang-1.1.3.jar input/sample.dhr
```

### CLI Options
```bash
--help           Show usage and options
--version        Print version (e.g., "DhrLang version 1.1.3")
--json           Output diagnostics as JSON (machine-readable)
--time           Show phase timings (lex/parse/type/exec)
--no-color       Disable ANSI colors in diagnostics
--backend=ast|ir|bytecode  (select execution backend)
```

## Your First DhrLang Program

Create a file `hello.dhr`:
```dhrlang
// Hello World
class Main {
    static kaam main() {
        printLine("Hello, DhrLang!");
        num age = 25;
        sab name = "Rahul";
        printLine("Name: " + name);
        printLine("Age: " + age);
    }
}
```

Run it:
```bash
java -jar DhrLang-1.1.3.jar hello.dhr
```

Output:
```
Hello, DhrLang!
Name: Rahul
Age: 25
```

## Language Features

### 🌍 **DhrLang Keywords & Type System**
```dhrlang
// DhrLang syntax with special keywords
class Person {
    private sab name;        // sab = string
    public kaam init(sab n) { this.name = n; }  // kaam = function
}

// Full type system
class Student {
    private sab course;      // sab = string
    private num grade;       // num = integer 
    private duo gpa;         // duo = decimal
    private kya graduated;   // kya = boolean
}
```

### 🔧 **Object-Oriented Features**
```dhrlang
class Container {
    private sab value;
    
    public kaam init(sab val) { 
        this.value = val; 
    }
    
    public sab getValue() { 
        return this.value; 
    }
}

class NumberContainer {
    private num value;
    public kaam setValue(num val) { this.value = val; }
    public num getValue() { return this.value; }
}
```

### 🛡️ **Access Control**
```dhrlang
class BankAccount {
    private duo balance = 0.0;        // Private - only class access
    protected sab accountType;        // Protected - subclass access  
    public sab accountNumber;         // Public - everywhere access
    
    public kaam deposit(duo amount) {
        this.balance = this.balance + amount;
    }
}
```

### 🎯 **Exception Handling**
```dhrlang
class ErrorDemo {
    static kaam main() {
        try {
            throw "Something went wrong";
        } catch(err) {
            printLine("Caught: " + err);
        } finally {
            printLine("Cleanup");
        }
    }
}
```

Typed catches:
```dhrlang
class TypedCatchDemo {
    static kaam main() {
        try {
            num x = 10 / 0;  // Will throw division by zero
        } catch(Error e) {
            printLine("Error caught: " + e);
        } catch(any e) {
            printLine("Fallback: " + e);
        }
    }
}
```

### 🔄 **Control Flow**
```dhrlang
// Loops and conditionals
for (num i = 0; i < 5; i++) {
    printLine("Iteration: " + i);
}

num counter = 0;
while (counter < 3) {
    printLine("Counter: " + counter);
    counter++;
}

num age = 20;
if (age >= 18) {
    printLine("Adult");
} else {
    printLine("Minor");
}
```

## IDE Setup

### VS Code (Recommended) ✅
1. Install the DhrLang extension: `code --install-extension dhrlang-vscode-1.0.0.vsix`
2. Open any `.dhr` file to get:
   - Syntax highlighting for DhrLang keywords
   - IntelliSense auto-completion
   - Run commands (Ctrl+F5 to run, Ctrl+Shift+B to compile)
   - Code snippets for common patterns

### IntelliJ IDEA
1. Configure file association: `.dhr` → Text files
2. Use Java syntax highlighting as fallback
3. Set external tool: `java -jar path/to/DhrLang.jar $FilePath$`

## Examples Gallery

Explore `input/` directory for comprehensive examples:
- **Basic Syntax**: `test_basic_syntax.dhr`
- **OOP Features**: `test_oop_features.dhr` 
- **Arrays & Collections**: `test_arrays.dhr`
- **String Manipulation**: `test_strings.dhr`
- **Exception Handling**: `test_exceptions.dhr`
- **Algorithms**: `test_algorithms.dhr`

## Community & Support

- 📖 **Documentation**: [Language Specification](SPEC.md)
- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/dhruv-15-03/DhrLang/issues)
- 💬 **Discussions**: [GitHub Discussions](https://github.com/dhruv-15-03/DhrLang/discussions)
- 📧 **Contact**: dhruv.rastogi@example.com

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for:
- Code style guidelines
- Adding new language features
- Improving error messages
- Documentation improvements
- Language bindings

## License

DhrLang is open source under the [MIT License](LICENSE).

---

**Ready to write your first program?** Try the [interactive tutorial](examples/) or join our community!