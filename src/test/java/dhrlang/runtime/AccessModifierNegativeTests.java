package dhrlang.runtime;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
/** Negative tests for runtime access enforcement (private/protected). */
public class AccessModifierNegativeTests {
  private void assertCompileAccessError(String src, String contains){
    var r = RuntimeTestUtil.runSource(src);
    assertTrue(r.hadCompileErrors, "Expected compile-time access error");
    assertTrue(r.stderr.contains(contains) || (r.runtimeErrorMessage!=null && r.runtimeErrorMessage.contains(contains)), "Missing expected substring: "+contains+"\nActual stderr: "+r.stderr);
  }
  @Test void privateFieldReadOutsideClass(){
    String src = "class A { private num x; kaam init(){ this.x=5; } num get(){ return this.x; } } class B { static kaam main(){ A a = new A(); printLine(a.x); } }";
    assertCompileAccessError(src, "access modifier");
  }
  @Test void privateFieldWriteOutsideClass(){
    String src = "class A { private num x; kaam init(){ this.x=5; } } class B { static kaam main(){ A a = new A(); a.x = 9; } }";
    assertCompileAccessError(src, "access modifier");
  }
  @Test void protectedFieldAccessFromNonSubclass(){
    String src = "class A { protected num y; kaam init(){ this.y=3; } } class C { static kaam main(){ A a = new A(); printLine(a.y); } }";
    assertCompileAccessError(src, "access modifier");
  }
  @Test void protectedFieldAccessFromSubclassAllowed(){
    String src = "class A { protected num y; kaam init(){ this.y=3; } } class B extends A { static kaam main(){ B b = new B(); printLine(b.y); } }";
    var r = RuntimeTestUtil.runSource(src);
    assertFalse(r.hadCompileErrors, r.stderr);
    assertFalse(r.hadRuntimeError, r.stderr);
    assertEquals("3", r.stdout.trim());
  }
  @Test void privateStaticFieldAccessOutside(){
    String src = "class A { private static num z; } class Main { static kaam main(){ printLine(A.z); } }";
    assertCompileAccessError(src, "private/protected static field");
  }
  @Test void privateMethodCallOutside(){
    String src = "class A { private num foo(){ return 1; } kaam init(){} } class B { static kaam main(){ A a = new A(); printLine(a.foo()); } }";
    assertCompileAccessError(src, "access modifier");
  }
  @Test void protectedMethodCallFromSubclassAllowed(){
    String src = "class A { protected num foo(){ return 5; } } class B extends A { static kaam main(){ B b = new B(); printLine(b.foo()); } }";
    var r = RuntimeTestUtil.runSource(src); assertFalse(r.hadCompileErrors, r.stderr); assertFalse(r.hadRuntimeError, r.stderr); assertEquals("5", r.stdout.trim());
  }
  @Test void protectedMethodCallFromNonSubclassRejected(){
    String src = "class A { protected num foo(){ return 5; } } class C { static kaam main(){ A a = new A(); printLine(a.foo()); } }";
    assertCompileAccessError(src, "access modifier");
  }
}
