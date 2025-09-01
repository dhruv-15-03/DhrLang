package dhrlang.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

public class NullErrorTests {
    @Test @DisplayName("Compile-time rejection: property access on null")
    void nullPropertyAccess() {
        String src = "class A { static kaam main(){ sab x = null; printLine(x.length); } }";
        var result = RuntimeTestUtil.runSource(src);
        assertTrue(result.hadCompileErrors, "Expected compile/type error");
        assertFalse(result.hadRuntimeError, "Should not reach runtime for invalid property access");
    }
    @Test @DisplayName("Compile-time rejection: property access on possibly-null return")
    void nullPropertyAccessRuntime() {
        String src = "class A { num v; } class Maker { static A make(){ return null; } static kaam main(){ A obj = Maker.make(); printLine(obj.v); } }";
        var result = RuntimeTestUtil.runSource(src);
        assertTrue(result.hadCompileErrors, "Expected compile/type error for property on potential null");
        assertFalse(result.hadRuntimeError, "No runtime error expected");
    }
}
