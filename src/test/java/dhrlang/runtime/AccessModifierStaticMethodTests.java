package dhrlang.runtime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Focused tests for static method access modifiers. */
public class AccessModifierStaticMethodTests {
  private static RuntimeTestUtil.Result run(String src){ return RuntimeTestUtil.runSource(src); }

  @Test void privateStaticMethodDeniedFromOtherClass(){
    String src = "class A { private static kaam hidden(){} } class B { static kaam main(){ A.hidden(); } }";
    var r = run(src);
    assertTrue(r.hadCompileErrors, "Expected compile error");
    assertTrue(r.stderr.contains("Cannot access private/protected static method 'hidden'"), r.stderr);
  }

  @Test void protectedStaticMethodAllowedFromSubclass(){
    String src = "class A { protected static kaam ping(){ printLine(1); } } class B extends A { static kaam main(){ A.ping(); } }";
    var r = run(src);
    assertFalse(r.hadCompileErrors, r.stderr);
    assertFalse(r.hadRuntimeError, r.stderr);
    assertEquals("1", r.stdout.trim());
  }

  @Test void protectedStaticMethodDeniedFromNonSubclass(){
    String src = "class A { protected static kaam ping(){} } class C { static kaam main(){ A.ping(); } }";
    var r = run(src);
    assertTrue(r.hadCompileErrors, "Expected compile error");
    assertTrue(r.stderr.contains("Cannot access private/protected static method 'ping'"), r.stderr);
  }
}
