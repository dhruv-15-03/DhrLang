package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ExceptionExtraTests {
    @Test void catchAnyCatchesError(){
        String src = "class A { static kaam main(){ try { throw new Error(); } pakdo(e){ print(1); } } }";
        var r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); assertEquals("1", r.stdout.trim());
    }
    @Test void autoWrapPrimitiveIntoError(){
        String src = "class A { static kaam main(){ try { throw 123; } pakdo(ex Error){ print( (ex sab).length(); ) } } }"; // invalid cast syntax maybe; just print(1) for now
        src = "class A { static kaam main(){ try { throw 123; } pakdo(e Error){ print(1); } } }";
        var r = RuntimeTestUtil.runSource(src);
        assertEquals("1", r.stdout.trim());
    }
    @Test void nestedTryFinallyPropagation(){
        String src = "class A { static kaam main(){ try { try { throw new Error(); } finally { print(1); } } pakdo(e Error){ print(2); } } }";
        var r = RuntimeTestUtil.runSource(src);
        assertEquals("12", r.stdout.trim());
    }
    @Test void uncaughtErrorSetsRuntimeFlag(){
        String src = "class A { static kaam main(){ throw new Error(); } }";
        var r = RuntimeTestUtil.runSource(src);
        assertTrue(r.hadRuntimeError); // no catch
    }
}
