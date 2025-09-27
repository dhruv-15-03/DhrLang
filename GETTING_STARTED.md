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
मुख्य() {
    प्रिंट("नमस्ते, DhrLang!");
    
    // Variables with Hindi keywords
    संख्या age = 25;
    स्ट्रिंग name = "राहुल";
    
    प्रिंट("नाम: " + name);
    प्रिंट("उम्र: " + age);
}
```

Run it:
```bash
java -jar DhrLang-1.0.0.jar hello.dhr
```

## Language Features

### 🌍 **Hindi Keywords & English Support**
```dhrlang
// Hindi style
क्लास Person {
    निजी स्ट्रिंग name;
    सार्वजनिक Person(स्ट्रिंग n) { this.name = n; }
}

// English style also supported
class Student {
    private string course;
    public Student(string c) { this.course = c; }
}
```

### 🔧 **Generics & Type Safety**
```dhrlang
क्लास Container<T> {
    निजी T value;
    सार्वजनिक void set(T val) { this.value = val; }
    सार्वजनिक T get() { return this.value; }
}

Container<संख्या> numbers = new Container<संख्या>();
numbers.set(42);
```

### 🛡️ **Access Control**
```dhrlang
क्लास BankAccount {
    निजी दशमलव balance = 0.0;     // Private - only class access
    संरक्षित स्ट्रिंग accountType;    // Protected - subclass access  
    सार्वजनिक स्ट्रिंग accountNumber; // Public - everywhere access
}
```

### 🎯 **Exception Handling**
```dhrlang
कोशिश {
    संख्या result = 10 / 0;
} पकड़ना (RuntimeException e) {
    प्रिंट("Error: " + e.getMessage());
} अंततः {
    प्रिंट("Cleanup complete");
}
```

### 🔄 **Control Flow**
```dhrlang
// Loops with Hindi keywords
के लिए (संख्या i = 0; i < 5; i++) {
    प्रिंट("Iteration: " + i);
}

जबकि (condition) {
    // do something
}

अगर (age >= 18) {
    प्रिंट("Adult");
} नहीं तो {
    प्रिंट("Minor");
}
```

## IDE Setup

### VS Code (Recommended)
1. Install the DhrLang extension (coming soon)
2. Open any `.dhr` file
3. Get syntax highlighting, error detection, and auto-completion

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