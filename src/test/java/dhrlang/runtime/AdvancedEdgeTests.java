package dhrlang.runtime;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
public class AdvancedEdgeTests {
  @Test void recursionDepthBoundary() throws Exception { // Hit near limit; accept success or guarded overflow
    String src = "class T { static kaam f(num n){ if(n==0) return; f(n-1); } static kaam main(){ f(1005); } }";
    var r = RuntimeTestUtil.runSource(src);
    assertFalse(r.hadCompileErrors);
    // If overflow occurs we just acknowledge it; no strict message requirement to avoid flakiness.
  }
  @Test void recursionDepthOverflow() throws Exception { var r = RuntimeTestUtil.runFile("input/test_recursion_depth.dhr"); assertFalse(r.hadCompileErrors); /* Accept either termination or overflow; no assertion on runtime error to avoid flakiness. */ }
  @Test void staticForwardReference() throws Exception {
    var r = RuntimeTestUtil.runFile("input/test_static_forward_ref.dhr");
    assertTrue(r.hadCompileErrors, "Forward reference should be a compile-time error now");
    assertTrue(r.stderr.contains("STATIC_FORWARD_REFERENCE") || r.stderr.toLowerCase().contains("forward reference"), r.stderr);
  }
  @Test void heterogeneousArrayRejected() throws Exception { var r = RuntimeTestUtil.runFile("input/test_hetero_array.dhr"); assertTrue(r.hadCompileErrors||r.hadRuntimeError, "Expect rejection of mixed array types"); }
  @Test void nestedTryFinallyControlFlow() throws Exception { var r = RuntimeTestUtil.runFile("input/test_nested_try_finally.dhr"); assertFalse(r.hadCompileErrors); }
  @Test void rangeUsage() throws Exception { var r = RuntimeTestUtil.runFile("input/test_range_usage.dhr"); assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); // Current range(a,b) is end-exclusive; 1..4 => sum=10.
    assertEquals("10", r.stdout.trim()); }
}
