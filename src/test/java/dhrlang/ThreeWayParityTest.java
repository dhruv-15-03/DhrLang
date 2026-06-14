package dhrlang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Three-way parity tests: verifies AST interpreter, IR interpreter,
 * and Bytecode VM all produce identical output for key language constructs.
 */
@DisplayName("Three-Way Backend Parity Tests")
public class ThreeWayParityTest {

    private String run(String... args) throws Exception {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        assertEquals(0, code, "Non-zero exit: " + out);
        return out.replaceAll("\r\n", "\n").trim();
    }

    private void assertParity(String source, String testName) throws Exception {
        Path file = Files.createTempFile("dhrlang-parity-", ".dhr");
        Files.writeString(file, source);
        String cp = System.getProperty("java.class.path");

        String astOut = run("java", "-cp", cp, "dhrlang.Main", file.toString());
        String irOut  = run("java", "-cp", cp, "dhrlang.Main", "--backend=ir", file.toString());
        String bcOut  = run("java", "-cp", cp, "dhrlang.Main", "--backend=bytecode", file.toString());

        assertAll(testName,
            () -> assertEquals(astOut, irOut,  testName + ": AST vs IR diverged\nAST=\n" + astOut + "\nIR=\n" + irOut),
            () -> assertEquals(astOut, bcOut,  testName + ": AST vs Bytecode diverged\nAST=\n" + astOut + "\nBC=\n" + bcOut)
        );

        Files.deleteIfExists(file);
    }

    @Test
    @DisplayName("Arithmetic + type coercion")
    void arithmetic() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        num a = 10;
                        num b = 3;
                        printLine(a + b);
                        printLine(a - b);
                        printLine(a * b);
                        printLine(a % b);
                    }
                }
                """, "Arithmetic");
    }

    @Test
    @DisplayName("String concatenation and methods")
    void stringOps() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        sab s = "hello";
                        printLine(s.toUpperCase());
                        printLine(s.length());
                        printLine(s.charAt(1));
                        printLine(s.substring(1, 4));
                        printLine(s.indexOf("ll"));
                    }
                }
                """, "String operations");
    }

    @Test
    @DisplayName("Control flow: if/else + while + for")
    void controlFlow() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        num x = 5;
                        if (x > 3) {
                            printLine("big");
                        } else {
                            printLine("small");
                        }
                        num i = 0;
                        while (i < 3) {
                            printLine(i);
                            i = i + 1;
                        }
                        for (num j = 10; j < 13; j = j + 1) {
                            printLine(j);
                        }
                    }
                }
                """, "Control flow");
    }

    @Test
    @DisplayName("Logical AND/OR short-circuit")
    void logicalOps() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        kya a = true;
                        kya b = false;
                        printLine(a && b);
                        printLine(a || b);
                        printLine(!a);
                        printLine(a && true);
                        printLine(false || b);
                    }
                }
                """, "Logical operators");
    }

    @Test
    @DisplayName("Functions with recursion")
    void recursion() throws Exception {
        assertParity("""
                class Main {
                    static num fib(num n) {
                        if (n <= 1) { return n; }
                        return fib(n - 1) + fib(n - 2);
                    }
                    static kaam main() {
                        printLine(fib(10));
                    }
                }
                """, "Recursion");
    }

    @Test
    @DisplayName("Arrays: 1D and multi-dimensional")
    void arrays() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        num[] arr = [1, 2, 3, 4, 5];
                        printLine(arrayLength(arr));
                        printLine(arr[2]);
                        num[][] grid = new num[2][3];
                        grid[0][1] = 42;
                        printLine(grid[0][1]);
                    }
                }
                """, "Arrays");
    }

    @Test
    @DisplayName("OOP: classes, fields, methods, inheritance")
    void oopInheritance() throws Exception {
        assertParity("""
                class Animal {
                    sab name;
                    kaam init(sab n) {
                        this.name = n;
                    }
                    sab speak() {
                        return "...";
                    }
                }
                class Dog extends Animal {
                    kaam init(sab n) {
                        this.name = n;
                    }
                    sab speak() {
                        return "Woof";
                    }
                }
                class Main {
                    static kaam main() {
                        Dog d = new Dog("Rex");
                        printLine(d.name);
                        printLine(d.speak());
                    }
                }
                """, "OOP inheritance");
    }

    @Test
    @DisplayName("Static fields and methods")
    void staticMembers() throws Exception {
        assertParity("""
                class Counter {
                    static num count = 0;
                    static num getCount() {
                        return Counter.count;
                    }
                    static kaam increment() {
                        Counter.count = Counter.count + 1;
                    }
                }
                class Main {
                    static kaam main() {
                        Counter.increment();
                        Counter.increment();
                        Counter.increment();
                        printLine(Counter.getCount());
                    }
                }
                """, "Static members");
    }

    @Test
    @DisplayName("Exception handling: try/catch")
    void exceptions() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        try {
                            duo x = 1 / 0;
                        } catch (e) {
                            printLine("caught");
                        }
                        printLine("after");
                    }
                }
                """, "Exception handling");
    }

    @Test
    @DisplayName("Stdlib: math and array functions")
    void stdlibFunctions() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        printLine(abs(-5));
                        printLine(min(3, 7));
                        printLine(max(3, 7));
                        num[] a = [3, 1, 2];
                        num[] sorted = arraySort(a);
                        printLine(sorted[0]);
                        printLine(sorted[1]);
                        printLine(sorted[2]);
                    }
                }
                """, "Stdlib functions");
    }

    @Test
    @DisplayName("Prefix and postfix increment")
    void incrementOps() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        num x = 5;
                        num a = ++x;
                        printLine(a);
                        printLine(x);
                        num b = x++;
                        printLine(b);
                        printLine(x);
                    }
                }
                """, "Increment operators");
    }

    @Test
    @DisplayName("Nested function calls and >4 args")
    void manyArgs() throws Exception {
        assertParity("""
                class Main {
                    static num add6(num a, num b, num c, num d, num e, num f) {
                        return a + b + c + d + e + f;
                    }
                    static kaam main() {
                        printLine(add6(1, 2, 3, 4, 5, 6));
                    }
                }
                """, "Many args");
    }

    @Test
    @DisplayName("Bitwise operators: AND, OR, XOR, NOT, SHIFT")
    void bitwiseOps() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        num a = 0xFF;
                        num b = 0x0F;
                        printLine(a & b);
                        printLine(a | b);
                        printLine(a ^ b);
                        printLine(~0);
                        printLine(1 << 4);
                        printLine(256 >> 3);
                    }
                }
                """, "Bitwise operators");
    }

    @Test
    @DisplayName("Ternary operator")
    void ternaryOp() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        num x = 10;
                        sab result = x > 5 ? "big" : "small";
                        printLine(result);
                        num y = x > 20 ? 1 : 0;
                        printLine(y);
                    }
                }
                """, "Ternary operator");
    }

    @Test
    @DisplayName("String interpolation")
    void stringInterpolation() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        sab name = "World";
                        sab greeting = "Hello ${name}!";
                        printLine(greeting);
                        num x = 42;
                        printLine("value=${x}");
                    }
                }
                """, "String interpolation");
    }

    @Test
    @DisplayName("Numeric as-casts: duo<->num truncation/widening")
    void numericCasts() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        printLine(7.9 as num);
                        printLine(-7.9 as num);
                        num a = 7;
                        num b = 2;
                        printLine((a / b) as num);
                        printLine(a as duo);
                        printLine(toNum(3.9));
                        printLine(toDuo(5));
                    }
                }
                """, "Numeric casts");
    }

    @Test
    @DisplayName("Hex literals")
    void hexLiterals() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        num a = 0xFF;
                        num b = 0x10;
                        num c = 0xABCD;
                        printLine(a);
                        printLine(b);
                        printLine(c);
                    }
                }
                """, "Hex literals");
    }

    @Test
    @DisplayName("Switch statement")
    void switchStmt() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        num x = 2;
                        switch (x) {
                            case 1: { printLine("one"); }
                            case 2: { printLine("two"); }
                            case 3: { printLine("three"); }
                            default: { printLine("other"); }
                        }
                    }
                }
                """, "Switch statement");
    }

    @Test
    @DisplayName("Do-while loop")
    void doWhileLoop() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        num i = 0;
                        do {
                            printLine(i);
                            i = i + 1;
                        } while (i < 3);
                    }
                }
                """, "Do-while loop");
    }

    @Test
    @DisplayName("For-each loop")
    void forEachLoop() throws Exception {
        assertParity("""
                class Main {
                    static kaam main() {
                        num[] arr = [10, 20, 30];
                        num sum = 0;
                        for (num x : arr) {
                            sum = sum + x;
                        }
                        printLine(sum);
                    }
                }
                """, "For-each loop");
    }
}
