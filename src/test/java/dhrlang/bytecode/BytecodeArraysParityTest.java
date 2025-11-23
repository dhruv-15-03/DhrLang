package dhrlang.bytecode;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BytecodeArraysParityTest {
    private String run(String... args) throws Exception {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        assertEquals(0, code, "Non-zero exit: "+out);
        return out.replaceAll("\r\n", "\n").trim();
    }

    @Test
    void arraysProgramOutputsMatch() throws Exception {
        String cp = System.getProperty("java.class.path");
        String astOut = run("java","-cp",cp,"dhrlang.Main","input/test_arrays.dhr");
        String bcOut = run("java","-cp",cp,"dhrlang.Main","--backend=bytecode","input/test_arrays.dhr");
        bcOut = bcOut.replaceFirst("\\[experimental].*?\\n"," ").trim();
        assertEquals(astOut, bcOut, "AST vs Bytecode output diverged on arrays test\nAST=\n"+astOut+"\nBC=\n"+bcOut);
    }
}
