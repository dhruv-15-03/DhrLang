package dhrlang;

import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.interpreter.Interpreter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.lexer.TokenType;
import dhrlang.parser.Parser;
import dhrlang.parser.ParseException;
import dhrlang.typechecker.TypeChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class DhrLangCompilerTest {

    private ErrorReporter errorReporter;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void setUp() {
        errorReporter = new ErrorReporter();
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outputStream));
        System.setErr(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // ===================== LEXER TESTS =====================

    @Test
    void testLexerHindiKeywords() {
        String code = "num duo sab kya ek kaam class";
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        
        assertEquals(TokenType.NUM, tokens.get(0).getType());
        assertEquals(TokenType.DUO, tokens.get(1).getType());
        assertEquals(TokenType.SAB, tokens.get(2).getType());
        assertEquals(TokenType.KYA, tokens.get(3).getType());
        assertEquals(TokenType.EK, tokens.get(4).getType());
        assertEquals(TokenType.KAAM, tokens.get(5).getType());
        assertEquals(TokenType.CLASS, tokens.get(6).getType());
    }

    @Test
    void testLexerOperators() {
        String code = "+ - * / = == != < > <= >= && || ! ++ --";
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        
        assertEquals(TokenType.PLUS, tokens.get(0).getType());
        assertEquals(TokenType.MINUS, tokens.get(1).getType());
        assertEquals(TokenType.STAR, tokens.get(2).getType());
        assertEquals(TokenType.SLASH, tokens.get(3).getType());
        assertEquals(TokenType.ASSIGN, tokens.get(4).getType());
        assertEquals(TokenType.EQUALITY, tokens.get(5).getType());
        assertEquals(TokenType.NEQ, tokens.get(6).getType());
        assertEquals(TokenType.LESS, tokens.get(7).getType());
        assertEquals(TokenType.GREATER, tokens.get(8).getType());
        assertEquals(TokenType.LEQ, tokens.get(9).getType());
        assertEquals(TokenType.GEQ, tokens.get(10).getType());
        assertEquals(TokenType.AND, tokens.get(11).getType());
        assertEquals(TokenType.OR, tokens.get(12).getType());
        assertEquals(TokenType.NOT, tokens.get(13).getType());
        assertEquals(TokenType.INCREMENT, tokens.get(14).getType());
        assertEquals(TokenType.DECREMENT, tokens.get(15).getType());
    }

    @Test
    void testLexerStringLiterals() {
        String code = "\"Hello World\" 'A' \"String with \\\"quotes\\\"\"";
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        
        assertEquals(TokenType.STRING, tokens.get(0).getType());
        assertEquals("Hello World", tokens.get(0).getLexeme()); // String content without quotes
        assertEquals(TokenType.CHAR, tokens.get(1).getType());
        assertEquals("A", tokens.get(1).getLexeme()); // Character content without quotes
        assertEquals(TokenType.STRING, tokens.get(2).getType());
    }

    @Test
    void testLexerNumbers() {
        String code = "42 3.14159 0 -5 123.456";
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        
        assertEquals(TokenType.NUMBER, tokens.get(0).getType());
        assertEquals("42", tokens.get(0).getLexeme());
        assertEquals(TokenType.NUMBER, tokens.get(1).getType());
        assertEquals("3.14159", tokens.get(1).getLexeme());
        assertEquals(TokenType.NUMBER, tokens.get(2).getType());
        assertEquals("0", tokens.get(2).getLexeme());
        assertEquals(TokenType.MINUS, tokens.get(3).getType());
        assertEquals(TokenType.NUMBER, tokens.get(4).getType());
        assertEquals("5", tokens.get(4).getLexeme());
    }

    @Test
    void testLexerComments() {
        String code = """
            // Single line comment
            num x = 5; /* Multi-line
                          comment */
            sab msg = "Hello";
            """;
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        
        // Should ignore comments and only tokenize actual code
        assertEquals(TokenType.NUM, tokens.get(0).getType());
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).getType());
        assertEquals("x", tokens.get(1).getLexeme());
        assertEquals(TokenType.ASSIGN, tokens.get(2).getType());
        assertEquals(TokenType.NUMBER, tokens.get(3).getType());
        assertEquals(TokenType.SEMICOLON, tokens.get(4).getType());
    }

    // ===================== PARSER TESTS =====================

    @Test
    void testParserBasicClass() {
        String code = """
            class TestClass {
                num x;
                
                kaam init() {
                    x = 10;
                }
            }
            """;
        
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errorReporter);
        
        assertDoesNotThrow(() -> {
            Program program = parser.parse();
            assertNotNull(program);
            assertEquals(1, program.getClasses().size());
            assertEquals("TestClass", program.getClasses().get(0).getName());
        });
    }

    @Test
    void testParserInheritance() {
        String code = """
            class Parent {
                protected num value;
            }
            
            class Child extends Parent {
                kaam init() {
                    value = 42;
                }
            }
            """;
        
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errorReporter);
        
        assertDoesNotThrow(() -> {
            Program program = parser.parse();
            assertEquals(2, program.getClasses().size());
            assertEquals("Parent", program.getClasses().get(0).getName());
            assertEquals("Child", program.getClasses().get(1).getName());
            assertNotNull(program.getClasses().get(1).getSuperclass());
        });
    }

    @Test
    void testParserControlFlow() {
        String code = """
            class ControlTest {
                static kaam main() {
                    num i = 0;
                    while (i < 5) {
                        if (i == 2) {
                            i++;
                            continue;
                        }
                        printLine(i);
                        i++;
                    }
                    
                    for (num j = 0; j < 3; j++) {
                        if (j == 1) break;
                        printLine(j);
                    }
                }
            }
            """;
        
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errorReporter);
        
        assertDoesNotThrow(() -> {
            Program program = parser.parse();
            assertNotNull(program);
            assertFalse(errorReporter.hasErrors());
        });
    }

    @Test
    void testParserExceptionHandling() {
        String code = """
            class ExceptionTest {
                static kaam main() {
                    try {
                        num result = 10 / 0;
                    }
                    catch(error) {
                        printLine("Error: " + error);
                    }
                    finally {
                        printLine("Cleanup");
                    }
                }
            }
            """;
        
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errorReporter);
        
        assertDoesNotThrow(() -> {
            Program program = parser.parse();
            assertNotNull(program);
            assertFalse(errorReporter.hasErrors());
        });
    }

    @Test
    void testParserArrays() {
        String code = """
            class ArrayTest {
                static kaam main() {
                    num[] numbers = [1, 2, 3, 4, 5];
                    sab[] names = new sab[10];
                    numbers[0] = 100;
                    printLine(numbers[0]);
                }
            }
            """;
        
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errorReporter);
        
        assertDoesNotThrow(() -> {
            Program program = parser.parse();
            assertNotNull(program);
            assertFalse(errorReporter.hasErrors());
        });
    }

    // ===================== TYPE CHECKER TESTS =====================

    @Test
    void testTypeCheckerBasicTypes() {
        String code = """
            class TypeTest {
                static kaam main() {
                    num x = 42;
                    duo y = 3.14;
                    sab message = "Hello";
                    kya flag = true;
                    ek grade = 'A';
                }
            }
            """;
        
        Program program = parseProgram(code);
        TypeChecker typeChecker = new TypeChecker(errorReporter);
        
        assertDoesNotThrow(() -> {
            typeChecker.check(program);
            assertFalse(errorReporter.hasErrors());
        });
    }

    @Test
    void testTypeCheckerTypeErrors() {
        String code = """
            class TypeErrorTest {
                static kaam main() {
                    num x = "Hello"; // Type error: string assigned to number
                    sab message = 42; // Type error: number assigned to string
                }
            }
            """;
        
        Program program = parseProgram(code);
        TypeChecker typeChecker = new TypeChecker(errorReporter);
        
        typeChecker.check(program);
        assertTrue(errorReporter.hasErrors());
        assertTrue(errorReporter.getErrorCount() >= 2);
    }

    @Test
    void testTypeCheckerArrayTypes() {
        String code = """
            class ArrayTypeTest {
                static kaam main() {
                    num[] numbers = [1, 2, 3];
                    sab[] words = ["hello", "world"];
                    numbers[0] = 100; // Valid
                    // numbers[0] = "invalid"; // Would be type error
                }
            }
            """;
        
        Program program = parseProgram(code);
        TypeChecker typeChecker = new TypeChecker(errorReporter);
        
        assertDoesNotThrow(() -> {
            typeChecker.check(program);
            assertFalse(errorReporter.hasErrors());
        });
    }

    @Test
    void testTypeCheckerFunctionCalls() {
        String code = """
            class FunctionTest {
                static num add(num a, num b) {
                    return a + b;
                }
                
                static kaam main() {
                    num result = add(5, 10);
                    // num invalid = add("hello", "world"); // Would be type error
                }
            }
            """;
        
        Program program = parseProgram(code);
        TypeChecker typeChecker = new TypeChecker(errorReporter);
        
        assertDoesNotThrow(() -> {
            typeChecker.check(program);
            assertFalse(errorReporter.hasErrors());
        });
    }

    @Test
    void testTypeCheckerInheritance() {
        String code = """
            class Animal {
                protected sab name;
                
                kaam init(sab name) {
                    this.name = name;
                }
                
                sab getName() {
                    return name;
                }
            }
            
            class Dog extends Animal {
                kaam init(sab name) {
                    super.init(name);
                }
                
                kaam bark() {
                    printLine(this.name + " barks!");
                }
            }
            
            class Main {
                static kaam main() {
                    Dog dog = new Dog("Buddy");
                    dog.bark();
                }
            }
            """;
        
        Program program = parseProgram(code);
        TypeChecker typeChecker = new TypeChecker(errorReporter);
        
        assertDoesNotThrow(() -> {
            typeChecker.check(program);
            assertFalse(errorReporter.hasErrors());
        });
    }

    // ===================== INTERPRETER TESTS =====================

    @Test
    void testInterpreterBasicExecution() {
        String code = """
            class Main {
                static kaam main() {
                    printLine("Hello, DhrLang!");
                    num x = 42;
                    printLine("Number: " + x);
                }
            }
            """;
        
        Program program = parseProgram(code);
        TypeChecker typeChecker = new TypeChecker(errorReporter);
        typeChecker.check(program);
        
        assertFalse(errorReporter.hasErrors());
        
        Interpreter interpreter = new Interpreter();
        assertDoesNotThrow(() -> {
            interpreter.execute(program);
        });
        
        String output = outputStream.toString();
        assertTrue(output.contains("Hello, DhrLang!"));
        assertTrue(output.contains("Number: 42"));
    }

    @Test
    void testInterpreterArithmetic() {
        String code = """
            class ArithmeticTest {
                static kaam main() {
                    num a = 10;
                    num b = 5;
                    printLine("Addition: " + (a + b));
                    printLine("Subtraction: " + (a - b));
                    printLine("Multiplication: " + (a * b));
                    printLine("Division: " + (a / b));
                    printLine("Modulo: " + (a % b));
                }
            }
            """;
        
        executeProgram(code);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Addition: 15"));
        assertTrue(output.contains("Subtraction: 5"));
        assertTrue(output.contains("Multiplication: 50"));
        assertTrue(output.contains("Division: 2"));
        assertTrue(output.contains("Modulo: 0"));
    }

    @Test
    void testInterpreterControlFlow() {
        String code = """
            class ControlFlowTest {
                static kaam main() {
                    for (num i = 1; i <= 3; i++) {
                        if (i == 2) {
                            printLine("Even: " + i);
                        } else {
                            printLine("Odd: " + i);
                        }
                    }
                }
            }
            """;
        
        executeProgram(code);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Odd: 1"));
        assertTrue(output.contains("Even: 2"));
        assertTrue(output.contains("Odd: 3"));
    }

    @Test
    void testInterpreterArrays() {
        String code = """
            class ArrayTest {
                static kaam main() {
                    num[] numbers = [10, 20, 30];
                    for (num i = 0; i < arrayLength(numbers); i++) {
                        printLine("Element " + i + ": " + numbers[i]);
                    }
                    
                    numbers[1] = 99;
                    printLine("Modified: " + numbers[1]);
                }
            }
            """;
        
        executeProgram(code);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Element 0: 10"));
        assertTrue(output.contains("Element 1: 20"));
        assertTrue(output.contains("Element 2: 30"));
        assertTrue(output.contains("Modified: 99"));
    }

    @Test
    void testInterpreterObjectOriented() {
        String code = """
            class Person {
                private sab name;
                private num age;
                
                kaam init(sab name, num age) {
                    this.name = name;
                    this.age = age;
                }
                
                sab getName() {
                    return this.name;
                }
                
                num getAge() {
                    return this.age;
                }
                
                kaam introduce() {
                    printLine("Hi, I'm " + this.name + " and I'm " + this.age + " years old.");
                }
            }
            
            class Main {
                static kaam main() {
                    Person person = new Person("Alice", 25);
                    person.introduce();
                    printLine("Name: " + person.getName());
                    printLine("Age: " + person.getAge());
                }
            }
            """;
        
        executeProgram(code);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Hi, I'm Alice and I'm 25 years old."));
        assertTrue(output.contains("Name: Alice"));
        assertTrue(output.contains("Age: 25"));
    }

    @Test
    void testInterpreterExceptionHandling() {
        String code = """
            class ExceptionTest {
                static kaam main() {
                    try {
                        printLine("Before exception");
                        throw "Custom error message";
                        printLine("This should not print");
                    }
                    catch(error) {
                        printLine("Caught: " + error);
                    }
                    finally {
                        printLine("Finally block executed");
                    }
                    printLine("After try-catch");
                }
            }
            """;
        
        executeProgram(code);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Before exception"));
        assertTrue(output.contains("Caught: Custom error message"));
        assertTrue(output.contains("Finally block executed"));
        assertTrue(output.contains("After try-catch"));
        assertFalse(output.contains("This should not print"));
    }

    // ===================== INTEGRATION TESTS =====================

    @Test
    void testComplexProgram() {
        String code = """
            class Calculator {
                static num add(num a, num b) {
                    return a + b;
                }
                
                static num factorial(num n) {
                    if (n <= 1) {
                        return 1;
                    }
                    return n * Calculator.factorial(n - 1);
                }
            }
            
            class Main {
                static kaam main() {
                    num sum = Calculator.add(5, 10);
                    printLine("Sum: " + sum);
                    
                    num fact = Calculator.factorial(5);
                    printLine("Factorial: " + fact);
                    
                    num[] numbers = [1, 2, 3, 4, 5];
                    num total = 0;
                    for (num i = 0; i < arrayLength(numbers); i++) {
                        total = total + numbers[i];
                    }
                    printLine("Array sum: " + total);
                }
            }
            """;
        
        executeProgram(code);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Sum: 15"));
        assertTrue(output.contains("Factorial: 120"));
        assertTrue(output.contains("Array sum: 15"));
    }

    // ===================== ERROR HANDLING TESTS =====================

    @Test
    void testSyntaxErrorReporting() {
        String code = """
            class SyntaxError {
                static kaam main() {
                    num x = 5
                    // Missing semicolon
                }
            }
            """;
        
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errorReporter);
        
        assertThrows(ParseException.class, () -> {
            parser.parse();
        });
    }

    // ===================== HELPER METHODS =====================

    private Program parseProgram(String code) {
        Lexer lexer = new Lexer(code, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errorReporter);
        
        try {
            return parser.parse();
        } catch (ParseException e) {
            fail("Failed to parse program: " + e.getMessage());
            return null;
        }
    }

    private void executeProgram(String code) {
        Program program = parseProgram(code);
        TypeChecker typeChecker = new TypeChecker(errorReporter);
        typeChecker.check(program);
        
        if (errorReporter.hasErrors()) {
            fail("Type checking failed");
        }
        
        Interpreter interpreter = new Interpreter();
        try {
            interpreter.execute(program);
        } catch (Exception e) {
            fail("Execution failed: " + e.getMessage());
        }
    }
}
