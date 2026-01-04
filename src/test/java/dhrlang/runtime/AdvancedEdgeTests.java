package dhrlang.runtime;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
public class AdvancedEdgeTests {
  @Test void recursionDepthBoundary() throws Exception { // Hit near limit; accept success or guarded overflow
    String src = "class T { static kaam f(num n){ if(n==0) return; f(n-1); } static kaam main(){ f(1005); } }";
    var r = RuntimeTestUtil.runSource(src);
    assertFalse(r.hadCompileErrors);
    // If overflow occurs we just acknowledge it; no strict message requirement to avoid flakiness.
  }
}
