# Getting Started with DhrLang

DhrLang is a modern programming language with Hindi keywords, designed to make programming more accessible to Hindi speakers while maintaining powerful features like generics, access control, and comprehensive error handling.

## Quick Start

### Option 1: Download Release (Recommended)
1. Go to [Releases](https://github.com/dhruv-15-03/DhrLang/releases)
2. Download the latest `DhrLang-x.x.x-distribution.zip`
3. Extract and run: `java -jar lib/DhrLang-1.0.0.jar examples/sample.dhr`

### Option 2: Build from Source
```bash
git clone https://github.com/dhruv-15-03/DhrLang.git
cd DhrLang
./gradlew build
java -jar build/libs/DhrLang-1.0.0.jar input/sample.dhr
```

## Your First DhrLang Program

Create a file `hello.dhr`:
```dhrlang
// नमस्ते दुनिया!
class HelloWorld {
    static kaam main() {
        printLine("नमस्ते, DhrLang!");
        
        // Variables with DhrLang keywords
        num age = 25;
        sab name = "राहुल";
        
        printLine("नाम: " + name);
        printLine("उम्र: " + age);
        
        return;
    }
}
```

Run it:
```bash
java -jar DhrLang-1.0.0.jar hello.dhr
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
try {
    num result = 10 / 0;
    printLine("Result: " + result);
} catch (RuntimeException e) {
    printLine("Error caught: Division by zero");
} finally {
    printLine("Cleanup complete");
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