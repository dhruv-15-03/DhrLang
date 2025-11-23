package dhrlang.ir;

import org.junit.jupiter.api.Test;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/** Ensures --backend=ir flag does not crash and prints experimental notice. */
public class IrBackendFlagTest {
    @Test
    void backendFlagPrintsNotice() throws Exception {
        Process p = new ProcessBuilder("java","-cp",System.getProperty("java.class.path"),"dhrlang.Main","--backend=ir","input/sample.dhr").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        assertEquals(0, code, "Program should exit successfully under IR stub backend");
        assertTrue(out.contains("IR backend selected"), "Expected experimental IR backend notice in output. Got: " + out);
    }
}
