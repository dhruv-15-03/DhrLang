package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ArrayOpsFastTests {
    @Test void arrayCreationAndIndexing(){
        String src = "class A { static kaam main(){ num[] xs = [1,2,3]; print(xs[0]); print(xs[2]); } }";
        var r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); assertEquals("13", r.stdout.trim());
    }
    @Test void arrayBoundsRuntimeError(){
        String src = "class A { static kaam main(){ num[] xs = [1]; print(xs[1]); } }";
        var r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors); assertTrue(r.hadRuntimeError);
    }
}
