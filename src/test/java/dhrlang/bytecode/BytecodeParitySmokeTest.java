package dhrlang.bytecode;

import org.junit.jupiter.api.Test;

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
}
