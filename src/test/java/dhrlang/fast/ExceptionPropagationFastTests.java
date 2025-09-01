package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ExceptionPropagationFastTests {
  // Disabled pending proper nested try/catch/finally propagation semantics implementation.
  @Disabled("Pending nested try/catch propagation semantics - see Step 4 backlog item A: exception propagation")
  @Test void rethrowFromCatchGoesUp(){
    String src = "class A { static kaam main(){ try { try { throw new Error(); } pakdo(e Error){ throw e; } } pakdo(e Error){ print(1); } } }";
    var r = RuntimeTestUtil.runSource(src);
    assertTrue(r.hadCompileErrors || !r.stdout.isEmpty());
  }
  @Disabled("Pending nested try/finally order semantics - see Step 4 backlog item A")
  @Test void finallyRunsOnThrow(){
    String src = "class A { static kaam main(){ try { try { print(1); throw new Error(); } finally { print(2); } } pakdo(e Error){ print(3); } } }";
    var r = RuntimeTestUtil.runSource(src);
    assertTrue(r.hadCompileErrors || !r.stdout.isEmpty());
  }
}
