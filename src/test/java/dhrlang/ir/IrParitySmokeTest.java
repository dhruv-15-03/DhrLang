package dhrlang.ir;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Simple parity smoke test comparing AST vs IR output for sample program. */
public class IrParitySmokeTest {
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
        String irOut = run("java","-cp",cp,"dhrlang.Main","--backend=ir","input/sample.dhr");
        assertEquals(astOut, irOut, "AST vs IR output diverged\nAST=\n"+astOut+"\nIR=\n"+irOut);
    }

    @Test
    void moduloProgramOutputsMatch() throws Exception {
        Path file = Files.createTempFile("dhrlang-ir-modulo", ".dhr");
        Files.writeString(file, """
                class Main {
                    static kaam main() {
                        num a = 17;
                        num b = 5;
                        printLine(a % b);
                    }
                }
                """);

        String cp = System.getProperty("java.class.path");
        String astOut = run("java", "-cp", cp, "dhrlang.Main", file.toString());
        String irOut = run("java", "-cp", cp, "dhrlang.Main", "--backend=ir", file.toString());
        assertEquals(astOut, irOut, "AST vs IR output diverged for modulo program");
    }

    @Test
    void prefixIncrementProgramOutputsMatch() throws Exception {
        Path file = Files.createTempFile("dhrlang-ir-prefix", ".dhr");
        Files.writeString(file, """
                class Main {
                    static kaam main() {
                        num x = 2;
                        printLine(++x);
                        printLine(x);
                    }
                }
                """);

        String cp = System.getProperty("java.class.path");
        String astOut = run("java", "-cp", cp, "dhrlang.Main", file.toString());
        String irOut = run("java", "-cp", cp, "dhrlang.Main", "--backend=ir", file.toString());
        assertEquals(astOut, irOut, "AST vs IR output diverged for prefix increment program");
    }

    @Test
    void nativeCallAndStringMethodOutputsMatch() throws Exception {
        Path file = Files.createTempFile("dhrlang-ir-native", ".dhr");
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
        String irOut = run("java", "-cp", cp, "dhrlang.Main", "--backend=ir", file.toString());
        assertEquals(astOut, irOut, "AST vs IR output diverged for native/string method program");
    }

    @Test
    void multiDimArrayProgramOutputsMatch() throws Exception {
        Path file = Files.createTempFile("dhrlang-ir-multidim", ".dhr");
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
        String irOut = run("java", "-cp", cp, "dhrlang.Main", "--backend=ir", file.toString());
        assertEquals(astOut, irOut, "AST vs IR output diverged for multi-dimensional array program");
    }
}
