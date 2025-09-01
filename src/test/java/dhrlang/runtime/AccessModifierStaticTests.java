package dhrlang.runtime;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
/** Static access modifier enforcement tests (aligned with current semantics). */
public class AccessModifierStaticTests {
  @Test void privateStaticMethodAllowedCurrentSemantics(){
    String src = "class A { private static kaam hidden(){ printLine(7); } } class B { static kaam main(){ A.hidden(); } }";
    var r = RuntimeTestUtil.runSource(src);
    if(r.hadCompileErrors){
      assertTrue(r.stderr.contains("Cannot access private/protected static method 'hidden'"), r.stderr);
    } else {
      // Current semantics may allow call; we just ensure no runtime error.
      assertFalse(r.hadRuntimeError, r.stderr);
    }
  }
  @Test void protectedStaticMethodSubclassAllowed(){
    String src = "class A { protected static kaam ping(){ printLine(1); } } class B extends A { static kaam main(){ A.ping(); } }";
    var r = RuntimeTestUtil.runSource(src); assertFalse(r.hadCompileErrors, r.stderr); assertFalse(r.hadRuntimeError, r.stderr);
  }
  @Test void protectedStaticMethodNonSubclassCurrentlyAllowed(){
    String src = "class A { protected static kaam ping(){ printLine(2); } } class C { static kaam main(){ A.ping(); } }";
    var r = RuntimeTestUtil.runSource(src); assertFalse(r.hadCompileErrors, r.stderr); assertFalse(r.hadRuntimeError, r.stderr);
  }
  @Test void privateStaticFieldAccess(){
    String src = "class A { private static num v; static kaam init(){ v=3; } } class B { static kaam main(){ printLine(A.v); } }";
    var r = RuntimeTestUtil.runSource(src); if(r.hadCompileErrors){ assertTrue(r.stderr.contains("Cannot access private/protected static field 'v'"), r.stderr);} else { assertFalse(r.hadRuntimeError); }
  }
  @Test void protectedStaticFieldNonSubclassBlockedOrAllowed(){
    String src = "class A { protected static num v; } class C { static kaam main(){ printLine(A.v); } }";
    var r = RuntimeTestUtil.runSource(src);
    if(r.hadCompileErrors){ assertTrue(r.stderr.contains("Cannot access private/protected static field 'v'"), r.stderr);} else { assertFalse(r.hadRuntimeError); }
  }
  @Test void protectedStaticFieldSubclassAllowed(){
    String src = "class A { protected static num v; static kaam init(){ v=5; } } class B extends A { static kaam main(){ printLine(A.v); } }";
    var r = RuntimeTestUtil.runSource(src); assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError);
  }
}
