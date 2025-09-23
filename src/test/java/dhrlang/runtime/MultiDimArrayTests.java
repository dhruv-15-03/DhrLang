package dhrlang.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MultiDimArrayTests {
    @Test
    void createAndIndex2D() {
        String src = "class A { static kaam main(){ num[][] m = new num[2][3]; m[0][1] = 7; printLine(m[0][1]); } }";
        var result = RuntimeTestUtil.runSource(src);
        assertFalse(result.hadCompileErrors, "compile errors: " + result.stderr);
        assertFalse(result.hadRuntimeError, "runtime error: " + result.runtimeErrorMessage);
        assertEquals("7", result.stdout.trim());
    }

    @Test
    void typeChecking3D() {
        String src = "class A { static kaam main(){ num[][][] m = new num[2][2][2]; m[1][1][1] = 5; printLine(m[1][1][1]); } }";
        var result = RuntimeTestUtil.runSource(src);
        assertFalse(result.hadCompileErrors, result.stderr);
        assertFalse(result.hadRuntimeError, result.runtimeErrorMessage);
        assertEquals("5", result.stdout.trim());
    }

    @Test
    void negativeSizeInAnyDimIsError() {
        String src = "class A { static kaam main(){ num[][] m = new num[2][-1]; } }";
        var result = RuntimeTestUtil.runSource(src);
        assertTrue(result.hadRuntimeError || result.hadCompileErrors);
        assertTrue(result.stderr.contains("negative") || result.stderr.contains("BOUNDS_VIOLATION"), result.stderr);
    }

    @Test
    void nonNumericSizeCompileError() {
        String src = "class A { static kaam main(){ num n = 2; sab s = \"x\"; num[][] m = new num[n][s]; } }";
        var result = RuntimeTestUtil.runSource(src);
        assertTrue(result.hadCompileErrors);
        assertTrue(result.stderr.contains("Array size must be numeric") || result.stderr.contains("BOUNDS_VIOLATION"), result.stderr);
    }
}
