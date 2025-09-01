package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ExceptionFastTests {
    @Test void simpleThrowCatch(){
        String src = "class A { static kaam main(){ try { throw new Error(); } pakdo(e Error) { print(1); } } }";
        var r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); assertEquals("1", r.stdout.trim());
    }
    @Test void finallyAlwaysRuns(){
        String src = "class A { static kaam main(){ try { print(1); } finally { print(2); } } }";
        var r = RuntimeTestUtil.runSource(src);
        assertEquals("12", r.stdout.trim());
    }
    @Test void catchAndFinallyOrder(){
        String src = "class A { static kaam main(){ try { throw new Error(); } pakdo(e Error){ print(1);} finally { print(2);} } }";
        var r = RuntimeTestUtil.runSource(src);
        assertEquals("12", r.stdout.trim());
    }
}
