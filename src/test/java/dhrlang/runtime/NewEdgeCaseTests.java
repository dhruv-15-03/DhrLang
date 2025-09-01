package dhrlang.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Additional edge case coverage for newly identified semantic gaps. */
public class NewEdgeCaseTests {
    private void expectCompileError(String src){ var r = RuntimeTestUtil.runSource(src); assertTrue(r.hadCompileErrors, "Expected compile error. stderr=\n"+r.stderr); }
    private void expectRuntimeError(String src, String cat, String msgSub){ var r = RuntimeTestUtil.runSource(src); assertFalse(r.hadCompileErrors,"Unexpected compile error: \n"+r.stderr); assertTrue(r.hadRuntimeError, "Expected runtime error"); if(cat!=null) assertTrue(r.runtimeErrorCategory.contains(cat)); if(msgSub!=null) assertTrue((r.runtimeErrorMessage+"\n"+r.stderr).contains(msgSub)); }

    @Test void nullSetPropertyCompileRejected() {
        // Type system currently rejects property assignment on possibly-null reference at compile time.
        String src = "class A { sab x; static kaam main(){ A a = null; a.x = \"hi\"; } }";
        var r = RuntimeTestUtil.runSource(src);
        assertTrue(r.hadCompileErrors, "Expected compile/type error for property on null");
        assertFalse(r.hadRuntimeError);
    }

    @Test void genericFieldSet() {
        // Ensures generic instance field assignment works post base type fix.
    String src = "class Box<T>{ sab label; kaam init(){ this.label = \"empty\"; } } class Main { static kaam main(){ Box<num> b = new Box<num>(); b.label = \"full\"; printLine(b.label); } }"; // unchanged; placeholder if future adaptation needed
        var r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors, "No compile errors expected: \n"+r.stderr);
        assertFalse(r.hadRuntimeError, "No runtime errors expected: \n"+r.stderr);
        assertEquals("full", r.stdout.trim());
    }

    @Test void forLoopContinueIncrementExecuted() {
        String src = "class T { static kaam main(){ num s=0; for(num i=0; i<5; i=i+1){ if(i==2) continue; s = s + i; } printLine(s); } }";
        var r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors, r.stderr);
        assertFalse(r.hadRuntimeError, r.stderr);
        assertEquals("8", r.stdout.trim());
    }
}
