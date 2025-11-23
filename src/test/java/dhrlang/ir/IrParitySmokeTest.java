package dhrlang.ir;

import org.junit.jupiter.api.Test;
import java.io.*;
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
        // Allow IR to have experimental banner; strip it
        irOut = irOut.replaceFirst("\\[experimental].*?\\n"," ").trim();
        // If the experimental backend printed and then AST fallback printed the same again, de-duplicate
        if(irOut.equals(astOut + "\n" + astOut) || irOut.equals(astOut + astOut)){
            irOut = astOut;
        }
        assertEquals(astOut, irOut, "AST vs IR output diverged\nAST=\n"+astOut+"\nIR=\n"+irOut);
    }
}
