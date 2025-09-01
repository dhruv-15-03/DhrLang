package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LoopBreakContinueFastTests {
  @Test void breakSkipsRemaining(){
    String src = "class A { static kaam main(){ num i=0; while(i<5){ print(i); if(i==2) { break; } i=i+1; } } }";
    var r = RuntimeTestUtil.runSource(src);
    assertTrue(r.stdout.startsWith("012"));
  }
  @Test void continueSkipsIteration(){
    String src = "class A { static kaam main(){ num i=0; while(i<4){ i=i+1; if(i==2){ continue; } print(i); } } }";
    var r = RuntimeTestUtil.runSource(src);
    assertEquals("134", r.stdout.trim());
  }
}
