# Contributing to DhrLang

Thank you for your interest in contributing to DhrLang! This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Contributing Guidelines](#contributing-guidelines)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Submitting Changes](#submitting-changes)
- [Release Process](#release-process)

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please read it before contributing.

## Getting Started

### Prerequisites

- Java 17 or higher
- Git
- Basic understanding of compiler design concepts
- Familiarity with Java development

### Development Setup

1. **Fork the repository**
   ```bash
   # Fork the repo on GitHub, then clone your fork
   git clone https://github.com/YOUR-USERNAME/DhrLang.git
   cd DhrLang
   ```

2. **Set up upstream remote**
   ```bash
   git remote add upstream https://github.com/dhruv-15-03/DhrLang.git
   ```

3. **Build the project**
   ```bash
   ./gradlew build
   ```

4. **Run tests**
   ```bash
   ./gradlew test
   ```

5. **Run sample program**
   ```bash
   ./gradlew runSample
   ```

## Contributing Guidelines

### Types of Contributions

We welcome several types of contributions:

- **Bug fixes**: Fix issues in the existing codebase
- **Features**: Add new language features or compiler improvements
- **Documentation**: Improve or add documentation
- **Tests**: Add or improve test coverage
- **Examples**: Create educational examples or tutorials
- **Performance**: Optimize compilation or runtime performance

### Before You Start

1. **Check existing issues**: Look for existing issues or feature requests
2. **Create an issue**: For new features, create an issue to discuss the approach
3. **Small PRs**: Keep pull requests focused and reasonably sized
4. **Communication**: Ask questions if you're unsure about anything

### Branching Strategy

- `main`: Stable release branch
- `develop`: Integration branch for new features
- `feature/feature-name`: Feature development branches
- `bugfix/bug-name`: Bug fix branches
- `hotfix/fix-name`: Critical fixes for production

### Workflow

1. **Create a feature branch**
   ```bash
   git checkout develop
   git pull upstream develop
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**
   - Write code following our coding standards
   - Add tests for new functionality
   - Update documentation as needed

3. **Test your changes**
   ```bash
   ./gradlew test
   ./gradlew integrationTest
   ./gradlew check
   ```

4. **Commit your changes**
   ```bash
   git add .
   git commit -m "Add feature: descriptive commit message"
   ```

5. **Push and create PR**
   ```bash
   git push origin feature/your-feature-name
   # Create PR on GitHub
   ```

## Coding Standards

### Java Code Style

We follow standard Java conventions with some project-specific guidelines:

#### General Guidelines
- Use meaningful variable and method names
- Keep methods under 150 lines
- Keep classes focused on a single responsibility
- Add Javadoc comments for public APIs

#### Naming Conventions
- Classes: `PascalCase` (e.g., `TypeChecker`, `ErrorReporter`)
- Methods: `camelCase` (e.g., `checkExpression`, `reportError`)
- Variables: `camelCase` (e.g., `tokenType`, `errorMessage`)
- Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_ERRORS`, `DEFAULT_BUFFER_SIZE`)

#### Code Formatting
- Indentation: 4 spaces (no tabs)
- Line length: 120 characters maximum
- Braces: Opening brace on same line
- Spaces around operators and after commas

#### Example:
```java
public class ExampleClass {
    private static final int MAX_RETRIES = 3;
    
    public void processTokens(List<Token> tokens) {
        for (Token token : tokens) {
            if (token.getType() == TokenType.IDENTIFIER) {
                processIdentifier(token);
            }
        }
    }
}
```

### Code Quality Tools

We use several tools to maintain code quality:

- **Checkstyle**: Code style enforcement
- **PMD**: Code analysis for potential issues
- **SpotBugs**: Bug pattern detection
- **JaCoCo**: Test coverage reporting

Run code quality checks:
```bash
./gradlew checkstyleMain
./gradlew pmdMain
./gradlew spotbugsMain
```

## Testing Guidelines

### Test Categories

1. **Unit Tests**: Test individual components in isolation
2. **Integration Tests**: Test component interactions
3. **End-to-End Tests**: Test complete compilation pipeline
4. **Language Tests**: Test DhrLang programs execution

### Writing Tests

#### Unit Tests
```java
@Test
void testLexerTokenization() {
    Lexer lexer = new Lexer("num x = 42;", errorReporter);
    List<Token> tokens = lexer.scanTokens();
    
    assertEquals(TokenType.NUM, tokens.get(0).getType());
    assertEquals(TokenType.IDENTIFIER, tokens.get(1).getType());
    assertEquals("x", tokens.get(1).getLexeme());
}
```

#### Language Tests
Create test files in `input/test_*.dhr`:
```dhrlang
// test_new_feature.dhr
class TestClass {
    static kaam main() {
        // Test your new feature here
        printLine("Feature test");
    }
}
```

### Test Coverage

- Aim for 80%+ test coverage on new code
- Critical paths should have 100% coverage
- Add tests for both success and error cases

### Running Tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "dhrlang.lexer.LexerTest"

# Integration tests
./gradlew integrationTest

# Generate coverage report
./gradlew jacocoTestReport
```

## Submitting Changes

### Pull Request Process

1. **Ensure CI passes**: All tests and quality checks must pass
2. **Update documentation**: Update relevant documentation
3. **Add changelog entry**: Add entry to CHANGELOG.md
4. **Descriptive title**: Use clear, descriptive PR titles
5. **Detailed description**: Explain what changes and why

### PR Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation update
- [ ] Performance improvement
- [ ] Code refactoring

## Testing
- [ ] Added/updated unit tests
- [ ] Added/updated integration tests
- [ ] Manual testing performed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] No breaking changes (or marked as such)
```

### Review Process

1. **Automated checks**: CI pipeline must pass
2. **Code review**: At least one maintainer approval required
3. **Testing**: Ensure adequate test coverage
4. **Documentation**: Verify documentation is updated

## Development Areas

### Core Compiler Components

- **Lexer** (`src/main/java/dhrlang/lexer/`): Tokenization and scanning
- **Parser** (`src/main/java/dhrlang/parser/`): Syntax analysis
- **AST** (`src/main/java/dhrlang/ast/`): Abstract syntax tree nodes
- **Type Checker** (`src/main/java/dhrlang/typechecker/`): Semantic analysis
- **Interpreter** (`src/main/java/dhrlang/interpreter/`): Code execution
- **Error System** (`src/main/java/dhrlang/error/`): Error reporting

### Priority Areas for Contribution

1. **Standard Library**: Expand built-in functions and utilities
2. **Error Messages**: Improve error reporting and hints
3. **Performance**: Optimize compilation and runtime performance
4. **Documentation**: API docs, tutorials, and examples
5. **Testing**: Increase test coverage and add edge cases

### Future Features (Roadmap)

- Generic types system
- Package management
- Module system
- Concurrency support
- IDE integration
- Language server protocol

## Getting Help

- **GitHub Discussions**: For questions and general discussion
- **GitHub Issues**: For bug reports and feature requests
- **Code Review**: Ask for feedback on your changes
- **Documentation**: Check existing docs and examples

## Recognition

Contributors are recognized in several ways:
- Listed in CONTRIBUTORS.md
- Mentioned in release notes
- GitHub contributor statistics
- Special recognition for significant contributions

Thank you for contributing to DhrLang! 🚀
