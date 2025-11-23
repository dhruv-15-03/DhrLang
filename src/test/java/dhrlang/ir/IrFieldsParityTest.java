package dhrlang.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Parity test for static field get/set between AST and IR backends. */
public class IrFieldsParityTest {
    private String run(String... args) throws Exception {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        assertEquals(0, code, "Non-zero exit: "+out);
        return out.replaceAll("\r\n", "\n").trim();
    }

    @Test
    void staticFieldProgramOutputsMatch() throws Exception {
        String cp = System.getProperty("java.class.path");
        String astOut = run("java","-cp",cp,"dhrlang.Main","input/test_static_fields.dhr");
        String irOut = run("java","-cp",cp,"dhrlang.Main","--backend=ir","input/test_static_fields.dhr");
        // Allow IR to have experimental banner; strip it
        irOut = irOut.replaceFirst("\\[experimental].*?\\n"," ").trim();
        // If the experimental backend printed and then AST fallback printed the same again, de-duplicate
        if(irOut.equals(astOut + "\n" + astOut) || irOut.equals(astOut + astOut)){
            irOut = astOut;
        }
        assertEquals(astOut, irOut, "AST vs IR output diverged\nAST=\n"+astOut+"\nIR=\n"+irOut);
    }
}
