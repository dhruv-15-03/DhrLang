package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StaticInitFastTests {
  @Test void staticFieldInitializationOrder(){
    String src = "class A { static num x = 1; static num y = x + 2; static kaam main(){ print(A.y); } }";
    var r = RuntimeTestUtil.runSource(src);
    assertFalse(r.hadCompileErrors);
    assertEquals("3", r.stdout.trim());
  }
}
