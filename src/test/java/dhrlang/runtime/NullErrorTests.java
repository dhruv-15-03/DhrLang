package dhrlang.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

public class NullErrorTests {
    @Test @DisplayName("Runtime error: property access on null")
    void nullPropertyAccess() {
        String src = "class A { static kaam main(){ sab x = null; printLine(x.length); } }";
        var result = RuntimeTestUtil.runSource(src);
        // null is now a valid literal; property access on null is a runtime error
        assertFalse(result.hadCompileErrors, "No compile error expected: " + result.stderr);
        assertTrue(result.hadRuntimeError, "Expected runtime error for property access on null");
    }
    @Test @DisplayName("Runtime error: property access on null return")
    void nullPropertyAccessRuntime() {
        String src = "class A { num v; } class Maker { static A make(){ return null; } static kaam main(){ A obj = Maker.make(); printLine(obj.v); } }";
        var result = RuntimeTestUtil.runSource(src);
        // null is valid at compile time; property access on null is a runtime error
        assertFalse(result.hadCompileErrors, "No compile error expected: " + result.stderr);
        assertTrue(result.hadRuntimeError, "Expected runtime error for property access on null");
    }
}
