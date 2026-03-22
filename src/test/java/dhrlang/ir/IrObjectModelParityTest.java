package dhrlang.ir;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrObjectModelParityTest {
    private String run(String... args) throws Exception {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        assertEquals(0, code, "Non-zero exit: " + out);
        return out.replace("\r\n", "\n").trim();
    }

    @Test
    void constructorFieldsAndInstanceMethodsMatchAst() throws Exception {
        Path file = Files.createTempFile("dhrlang-ir-objects", ".dhr");
        Files.writeString(file, """
                class Counter {
                    num value;

                    kaam init(num start) {
                        this.value = start;
                    }

                    num inc() {
                        this.value = this.value + 1;
                        return this.value;
                    }

                    num get() {
                        return this.value;
                    }
                }

                class Main {
                    static kaam main() {
                        Counter c = new Counter(5);
                        printLine(c.get());
                        printLine(c.inc());
                        printLine(c.get());
                    }
                }
                """);

        String cp = System.getProperty("java.class.path");
        String astOut = run("java", "-cp", cp, "dhrlang.Main", file.toString());
        String irOut = run("java", "-cp", cp, "dhrlang.Main", "--backend=ir", file.toString());
        assertEquals(astOut, irOut, "AST vs IR output diverged for object model program");
    }

    @Test
    void inheritedVirtualDispatchMatchesAst() throws Exception {
        Path file = Files.createTempFile("dhrlang-ir-inheritance", ".dhr");
        Files.writeString(file, """
                class Animal {
                    sab speak() {
                        return "animal";
                    }
                }

                class Dog extends Animal {
                    sab speak() {
                        return "dog";
                    }
                }

                class Main {
                    static kaam main() {
                        Animal a = new Dog();
                        printLine(a.speak());
                    }
                }
                """);

        String cp = System.getProperty("java.class.path");
        String astOut = run("java", "-cp", cp, "dhrlang.Main", file.toString());
        String irOut = run("java", "-cp", cp, "dhrlang.Main", "--backend=ir", file.toString());
        assertEquals(astOut, irOut, "AST vs IR output diverged for inherited dispatch program");
    }
}