# DhrLang Programming Language

[![Build Status](https://github.com/dhruv-15-03/DhrLang/actions/workflows/ci.yml/badge.svg)](https://github.com/dhruv-15-03/DhrLang/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

DhrLang is a modern, object-oriented programming language that combines familiar syntax with Hindi keywords, making programming more accessible to Hindi speakers while maintaining the power and flexibility of traditional programming languages.

## Features

- **Hindi Keywords**: Uses Hindi terms like `kaam`, `num`, `sab`, `kya` for a more localized programming experience
- **Object-Oriented Programming**: Full support for classes, inheritance, and polymorphism
- **Static Typing**: Strong type system with compile-time type checking
- **Exception Handling**: Comprehensive try-catch-finally exception handling
- **Array Support**: Built-in array operations with type safety
- **String Manipulation**: Rich string operations and methods
- **Static Methods**: Support for utility functions and class-level operations
 - **Centralized Evaluator Architecture**: All expression/statement semantics consolidated in a dedicated Evaluator (Interpreter is now a thin orchestration layer)

### Currently Unsupported / Partial Features
These appear in some demonstration files for forward-looking purposes and may intentionally fail compilation until implemented:
- True generic type checking & generic arrays
- Multi-dimensional arrays (e.g. `num[][]`) 
- Full access modifier enforcement semantics
- Abstract method return type validation across interfaces (legacy mismatch examples remain in samples)

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

### Building from Source
```bash
git clone https://github.com/dhruv-15-03/DhrLang.git
cd DhrLang
./gradlew build
```

### Running DhrLang Programs
```bash
# Using Gradle
./gradlew run --args="path/to/your/file.dhr"

# Using Java directly
java -cp build/classes/java/main dhrlang.Main path/to/your/file.dhr
```

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

### Exception Handling
```dhrlang
class ErrorExample {
    static kaam main() {
        try {
            // Risky operation
            throw "Something went wrong";
        }
        catch(error) {
            printLine("Caught: " + error);
        }
        finally {
            printLine("Cleanup completed");
        }
    }
}
```

## Language Features

### Comprehensive Type System
DhrLang features a strong static type system that catches errors at compile time:
- Type inference for variable declarations
- Generic type support (planned)
- Array type safety
- Method overloading resolution

### Rich Standard Library
- String manipulation functions (`length()`, `charAt()`, `indexOf()`, `contains()`, `replace()`)
- Array operations (`arrayLength()`, indexing, iteration)
- Built-in I/O operations (`print()`, `printLine()`)
- Mathematical operations with proper type handling

### Advanced Error Handling
- Detailed error messages with source location
- Helpful hints for common mistakes
- Colored terminal output for better readability
- Comprehensive error reporting system

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

### Test Coverage
The project includes comprehensive test coverage for:
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

## Examples

Check out the `input/` directory for comprehensive examples:
- `test_basic_syntax.dhr` - Basic language features
- `test_oop_features.dhr` - Object-oriented programming
- `test_arrays.dhr` - Array operations
- `test_exceptions.dhr` - Exception handling
- `test_strings.dhr` - String manipulation
- `test_static_methods.dhr` - Static methods and utilities
 - `advanced_features_test.dhr` - Shows generic syntax (may partially fail until generics implemented)
 - `complete_feature_demo.dhr` - Comprehensive showcase including forward-looking generics examples
 - `advanced_edge_cases.dhr` - Stress tests (multi-dimensional arrays commented out if unsupported)
 - `duplicate_error_test.dhr`, `parser_error_test.dhr` - Intentional negative tests to verify diagnostics

## Roadmap

### Short Term (v1.1)
- [ ] Package management system
- [ ] Standard library expansion
- [ ] IDE support and language server
- [ ] Debugging capabilities

### Medium Term (v1.5)
- [ ] Generic types implementation
- [ ] Module system
- [ ] Concurrency support
- [ ] Foreign function interface

### Long Term (v2.0)
- [ ] JIT compilation
- [ ] WebAssembly target
- [ ] Advanced optimization
- [ ] Cross-platform GUI framework

## Performance

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
- Designed to make programming accessible to Hindi speakers
- Community-driven development approach

## Contact

- GitHub: [@dhruv-15-03](https://github.com/dhruv-15-03)
- Project Link: [https://github.com/dhruv-15-03/DhrLang](https://github.com/dhruv-15-03/DhrLang)

---

*DhrLang - Making programming accessible in your native language* 🚀
