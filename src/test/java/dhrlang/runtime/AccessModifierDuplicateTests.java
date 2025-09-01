package dhrlang.runtime;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
/** Tests for detection of multiple access modifiers on a single member. */
public class AccessModifierDuplicateTests {
  @Test void duplicateFieldModifiersError(){
    String src = "class A { private public num x; static kaam main(){} }";
    var r = RuntimeTestUtil.runSource(src);
    assertTrue(r.hadCompileErrors, "Expected compile errors");
    assertTrue(r.stderr.contains("Field 'x' cannot have multiple access modifiers."), r.stderr);
  }
  @Test void duplicateMethodModifiersError(){
    String src = "class A { private public num foo(){ return 1; } static kaam main(){} }";
    var r = RuntimeTestUtil.runSource(src);
    assertTrue(r.hadCompileErrors, "Expected compile errors");
    assertTrue(r.stderr.contains("Method 'foo' cannot have multiple access modifiers."), r.stderr);
  }
}