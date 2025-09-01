package dhrlang.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GoldenOutputTests {

    private void assertRuns(String file, String expected) throws Exception {
        RuntimeTestUtil.Result r = RuntimeTestUtil.runFile(file);
        assertFalse(r.hadCompileErrors, "Compile errors in " + file + "\n" + r.stderr);
        assertFalse(r.hadRuntimeError, "Runtime error in " + file + "\n" + r.stderr);
        String canonExpected = canonical(expected);
        String canonActual = canonical(r.stdout);
        assertEquals(canonExpected, canonActual, "Mismatched stdout for " + file + "\nExpected:\n" + canonExpected + "\nActual:\n" + canonActual);
    }

    private String canonical(String text) {
        return text.replace("\r", "")
                .lines()
                .map(line -> line.stripTrailing())
                .reduce((a,b)-> a + "\n" + b)
                .orElse("")
                .trim();
    }

    @Test @DisplayName("sample.dhr golden output")
    void sampleGolden() throws Exception {
        String expected = String.join("\n",
                "Number: 42",
                "Message: Hello from DhrLang!",
                "Boolean: true",
                "Count: 1",
                "Count: 2",
                "Count: 3",
                "Count: 4",
                "Count: 5");
        assertRuns("input/sample.dhr", expected);
    }
}
