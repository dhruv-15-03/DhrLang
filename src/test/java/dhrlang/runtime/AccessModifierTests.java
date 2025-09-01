package dhrlang.runtime;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
public class AccessModifierTests {
  @Test void accessEnforcement() throws Exception {
    var r = RuntimeTestUtil.runFile("input/test_access_modifiers.dhr");
    // We expect no compile errors for valid accesses (only via getter or allowed modifiers)
    if(r.hadCompileErrors){ fail("Compile errors when accessing via allowed paths: \n"+r.stderr); }
    assertFalse(r.hadRuntimeError, r.stderr);
    String canon = r.stdout.trim().replace("\r","");
    assertEquals("7\n1\n3", canon);
  }
}
