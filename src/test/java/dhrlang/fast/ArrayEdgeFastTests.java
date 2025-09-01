package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ArrayEdgeFastTests {
  @Test void negativeSizeRejected(){
    String src = "class A { static kaam main(){ num n = -1; num[] xs = new num[n]; } }";
    var r = RuntimeTestUtil.runSource(src);
    assertTrue(r.hadCompileErrors || r.hadRuntimeError);
  }
  @Test void outOfBoundsAccessRejected(){
    String src = "class A { static kaam main(){ num[] xs = [1,2]; print(xs[2]); } }"; // last index 1
    var r = RuntimeTestUtil.runSource(src);
    assertTrue(r.hadRuntimeError || r.hadCompileErrors);
  }
  @Test void assignmentTypeMismatch(){
    String src = "class A { static kaam main(){ num[] xs = [1,2]; xs[0] = 'hi'; } }";
    var r = RuntimeTestUtil.runSource(src);
    assertTrue(r.hadCompileErrors || r.hadRuntimeError);
  }
}
