package dhrlang.ir;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IrArraysParityTest {
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
        String irOut = run("java","-cp",cp,"dhrlang.Main","--backend=ir","input/test_arrays.dhr");
        irOut = irOut.replaceFirst("\\[experimental].*?\\n"," ").trim();
        assertEquals(astOut, irOut, "AST vs IR output diverged on arrays test\nAST=\n"+astOut+"\nIR=\n"+irOut);
    }
}
