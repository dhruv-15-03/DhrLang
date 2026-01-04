package dhrlang.bytecode;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BytecodeCallsParityTest {
    private String run(String... args) throws Exception {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        assertEquals(0, code, "Non-zero exit: "+out);
        return out.replaceAll("\r\n", "\n").trim();
    }

    @Test
    void callHelperOutputsMatch() throws Exception {
        String cp = System.getProperty("java.class.path");
        String astOut = run("java","-cp",cp,"dhrlang.Main","input/calls_sample.dhr");
        String bcOut = run("java","-cp",cp,"dhrlang.Main","--backend=bytecode","input/calls_sample.dhr");
        assertEquals(astOut, bcOut, "AST vs Bytecode output diverged on calls test\nAST=\n"+astOut+"\nBC=\n"+bcOut);
    }
}
