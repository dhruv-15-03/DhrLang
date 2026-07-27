package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ExceptionPropagationFastTests {
  @Test void rethrowFromCatchGoesUp(){
    String src = "class A { static kaam main(){ try { try { throw new Error(); } pakdo(e Error){ throw e; } } pakdo(e Error){ print(1); } } }";
    var r = RuntimeTestUtil.runSource(src);
    assertFalse(r.hadCompileErrors, "Unexpected compile errors: \n"+r.stderr);
    assertFalse(r.hadRuntimeError, "Unexpected runtime error: \n"+r.stderr);
    assertEquals("1", r.stdout);
  }
  @Test void finallyRunsOnThrow(){
    String src = "class A { static kaam main(){ try { try { print(1); throw new Error(); } finally { print(2); } } pakdo(e Error){ print(3); } } }";
    var r = RuntimeTestUtil.runSource(src);
    assertFalse(r.hadCompileErrors, "Unexpected compile errors: \n"+r.stderr);
    assertFalse(r.hadRuntimeError, "Unexpected runtime error: \n"+r.stderr);
    assertEquals("123", r.stdout);
  }
}
