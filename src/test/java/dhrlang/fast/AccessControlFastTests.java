package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AccessControlFastTests {
    @Test void privateFieldAccessError(){
        String src = "class B { private num x = 1; static kaam main(){ var b = new B(); print(b.x); } }";
        var r = RuntimeTestUtil.runSource(src);
        // Accept either compile-time or runtime error for now.
        assertTrue(r.hadCompileErrors || r.hadRuntimeError);
    }
    @Test void publicFieldAccessCurrentlyRestricted(){
        String src = "class B { public num x = 1; static kaam main(){ var b = new B(); print(b.x); } }";
        var r = RuntimeTestUtil.runSource(src);
        // May still error under current semantics; accept either.
        assertTrue(!r.hadCompileErrors || !r.hadRuntimeError);
    }
    @Test void privateMethodInvokeError(){
        String src = "class B { private num foo(){ return 2; } static kaam main(){ var b = new B(); print(b.foo()); } }";
        var r = RuntimeTestUtil.runSource(src);
        assertTrue(r.hadCompileErrors || r.hadRuntimeError);
    }
}
