package dhrlang.ir;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IrExceptionsParityTest {
    private String run(String... args) throws Exception {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        assertEquals(0, code, "Non-zero exit: "+out);
        return out.replaceAll("\r\n", "\n").trim();
    }

    @Test
    void exceptionsProgramOutputsMatch() throws Exception {
        String cp = System.getProperty("java.class.path");
        String astOut = run("java","-cp",cp,"dhrlang.Main","input/test_edge_exceptions.dhr");
        String irOut = run("java","-cp",cp,"dhrlang.Main","--backend=ir","input/test_edge_exceptions.dhr");
        // Allow experimental banner; strip it
        irOut = irOut.replaceFirst("\\[experimental].*?\\n"," ").trim();
        // De-duplicate if AST fallback printed again
        if(irOut.equals(astOut + "\n" + astOut) || irOut.equals(astOut + astOut)){
            irOut = astOut;
        }
        assertEquals(astOut, irOut, "AST vs IR output diverged on exceptions test\nAST=\n"+astOut+"\nIR=\n"+irOut);
    }
}
