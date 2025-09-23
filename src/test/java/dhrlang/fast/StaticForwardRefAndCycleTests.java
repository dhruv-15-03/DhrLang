package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StaticForwardRefAndCycleTests {
  @Test void forwardRefIsCompileError(){
    String src = "class A { static num y = A.x + 2; static num x = 1; static kaam main(){ } }";
    var r = RuntimeTestUtil.runSource(src);
    assertTrue(r.hadCompileErrors, "Expected compile-time error for forward ref");
    assertTrue(r.stderr.contains("STATIC_FORWARD_REFERENCE") || r.stderr.toLowerCase().contains("forward reference"), r.stderr);
  }
  @Test void cycleIsCompileError(){
    String src = "class A { static num x = A.y + 1; static num y = A.x + 1; static kaam main(){ } }";
    var r = RuntimeTestUtil.runSource(src);
    assertTrue(r.hadCompileErrors, "Expected compile-time error for cycle");
    assertTrue(r.stderr.contains("STATIC_INIT_CYCLE") || r.stderr.toLowerCase().contains("cycle"), r.stderr);
  }
  @Test void earlierReadStillOk(){
    String src = "class A { static num x = 1; static num y = A.x + 2; static kaam main(){ } }";
    var r = RuntimeTestUtil.runSource(src);
    assertFalse(r.hadCompileErrors, r.stderr);
  }
}
