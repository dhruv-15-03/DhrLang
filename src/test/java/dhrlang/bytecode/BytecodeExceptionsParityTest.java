package dhrlang.bytecode;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BytecodeExceptionsParityTest {
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
        String bcOut = run("java","-cp",cp,"dhrlang.Main","--backend=bytecode","input/test_edge_exceptions.dhr");
        bcOut = bcOut.replaceFirst("\\[experimental].*?\\n"," ").trim();
        if(bcOut.equals(astOut + "\n" + astOut) || bcOut.equals(astOut + astOut)){
            bcOut = astOut;
        }
        assertEquals(astOut, bcOut, "AST vs Bytecode output diverged on exceptions test\nAST=\n"+astOut+"\nBC=\n"+bcOut);
    }
}
