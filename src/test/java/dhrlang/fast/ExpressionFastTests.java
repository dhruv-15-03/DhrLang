package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fast micro tests for core expression semantics (Step 4). */
public class ExpressionFastTests {

    private RuntimeTestUtil.Result run(String expr){
        String src = "class E { static kaam main(){ duo r = "+expr+"; printLine(r); } }";
        return RuntimeTestUtil.runSource(src);
    }

    @Test void arithmeticPrecedence() {
        var r = run("1 + 2 * 3");
        assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); assertEquals("7", r.stdout.trim());
    }

    @Test void parenthesesOverride() {
        var r = run("(1 + 2) * 3");
        assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); assertEquals("9", r.stdout.trim());
    }

    @Test void divisionProducesDuo() {
        var r = run("5 / 2.0");
        assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); assertTrue(r.stdout.trim().startsWith("2.5"));
    }

    @Test void logicalShortCircuitAnd() {
        String src = "class L { static kya side(){ printLine(\"side\"); return false; } static kaam main(){ kya a = false && side(); } }";
        var r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); // no 'side' printed
        assertTrue(r.stdout.trim().isEmpty());
    }

    @Test void logicalShortCircuitOr() {
        String src = "class L { static kya side(){ printLine(\"side\"); return true; } static kaam main(){ kya a = true || side(); } }";
        var r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors); assertFalse(r.hadRuntimeError); assertTrue(r.stdout.trim().isEmpty());
    }
}
