package dhrlang.ir;

import org.junit.jupiter.api.Test;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/** Ensures --backend=ir flag does not crash. */
public class IrBackendFlagTest {
    @Test
    void backendFlagDoesNotCrash() throws Exception {
        Process p = new ProcessBuilder("java","-cp",System.getProperty("java.class.path"),"dhrlang.Main","--backend=ir","input/sample.dhr").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        assertEquals(0, code, "Program should exit successfully under IR backend");
        assertFalse(out.contains("COMPILATION FAILED"), "IR backend should not fail to compile sample. Got: " + out);
    }
}
