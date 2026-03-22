package dhrlang.bytecode;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class BytecodeParitySmokeTest {
    private String run(String... args) throws Exception {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        assertEquals(0, code, "Non-zero exit: "+out);
        return out.replaceAll("\r\n", "\n").trim();
    }

    @Test
    void sampleProgramOutputsMatch() throws Exception {
        String cp = System.getProperty("java.class.path");
        String astOut = run("java","-cp",cp,"dhrlang.Main","input/sample.dhr");
        String bcOut = run("java","-cp",cp,"dhrlang.Main","--backend=bytecode","input/sample.dhr");
        assertEquals(astOut, bcOut, "AST vs Bytecode output diverged\nAST=\n"+astOut+"\nBC=\n"+bcOut);
    }

    @Test
    void sixArgFunctionCallOutputsMatch() throws Exception {
        Path file = Files.createTempFile("dhrlang-bc-sixargs", ".dhr");
        Files.writeString(file, """
                class Main {
                    static num sum6(num a, num b, num c, num d, num e, num f) {
                        return a + b + c + d + e + f;
                    }

                    static kaam main() {
                        printLine(sum6(1, 2, 3, 4, 5, 6));
                    }
                }
                """);

        String cp = System.getProperty("java.class.path");
        String astOut = run("java", "-cp", cp, "dhrlang.Main", file.toString());
        String bcOut = run("java", "-cp", cp, "dhrlang.Main", "--backend=bytecode", file.toString());
        assertEquals(astOut, bcOut, "AST vs Bytecode output diverged for 6-arg call program");
    }

    @Test
    void nativeCallAndStringMethodOutputsMatch() throws Exception {
        Path file = Files.createTempFile("dhrlang-bc-native", ".dhr");
        Files.writeString(file, """
                class Main {
                    static kaam main() {
                        sab s = "hello";
                        printLine(arrayLength([1, 2, 3]));
                        printLine(s.substring(1, 4));
                    }
                }
                """);

        String cp = System.getProperty("java.class.path");
        String astOut = run("java", "-cp", cp, "dhrlang.Main", file.toString());
        String bcOut = run("java", "-cp", cp, "dhrlang.Main", "--backend=bytecode", file.toString());
        assertEquals(astOut, bcOut, "AST vs Bytecode output diverged for native/string method program");
    }

    @Test
    void multiDimArrayOutputsMatch() throws Exception {
        Path file = Files.createTempFile("dhrlang-bc-multidim", ".dhr");
        Files.writeString(file, """
                class Main {
                    static kaam main() {
                        num[][] grid = new num[2][3];
                        grid[1][2] = 7;
                        printLine(grid[1][2]);
                    }
                }
                """);

        String cp = System.getProperty("java.class.path");
        String astOut = run("java", "-cp", cp, "dhrlang.Main", file.toString());
        String bcOut = run("java", "-cp", cp, "dhrlang.Main", "--backend=bytecode", file.toString());
        assertEquals(astOut, bcOut, "AST vs Bytecode output diverged for multi-dimensional array program");
    }
}
