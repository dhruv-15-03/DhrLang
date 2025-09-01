package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ControlFlowFastTests {
    @Test void forLoopExecutesExpectedTimes(){
        String src = "class F { static kaam main(){ for(num i=0;i<3;i++){ print(i); } } }";
        var r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); assertEquals("012", r.stdout.trim());
    }
    @Test void breakSkipsRemaining(){
        String src = "class B { static kaam main(){ for(num i=0;i<5;i++){ if(i==2) break; print(i); } } }";
        var r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); assertEquals("01", r.stdout.trim());
    }
    @Test void continueSkipsIteration(){
        String src = "class C { static kaam main(){ for(num i=0;i<4;i++){ if(i==2) continue; print(i); } } }";
        var r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); assertEquals("013", r.stdout.trim());
    }
}
