package dhrlang;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GenericsDiagnosticsTests {
    @Test
    void nonGenericClassGivenTypeArgsIsError() {
        String src = "class A { static kaam main(){ new Error<num>(); } }";
        var result = RuntimeTestUtil.runSource(src);
        assertTrue(result.hadCompileErrors);
        assertTrue(result.stderr.contains("not generic") || result.stderr.contains("GENERIC_ARITY"), result.stderr);
    }

    @Test
    void missingTypeArgsForGenericClassIsError() {
        String src = "class List<T> { kaam init(){} } class A { static kaam main(){ new List(); } }";
        var result = RuntimeTestUtil.runSource(src);
        assertTrue(result.hadCompileErrors);
        assertTrue(result.stderr.contains("requires type arguments") || result.stderr.contains("GENERIC_ARITY"), result.stderr);
    }

    @Test
    void wrongArityForGenericClassIsError() {
        String src = "class Pair<A,B> { kaam init(){} } class A { static kaam main(){ new Pair<num>(); } }";
        var result = RuntimeTestUtil.runSource(src);
        assertTrue(result.hadCompileErrors);
        assertTrue(result.stderr.contains("expects 2 type arguments") || result.stderr.contains("GENERIC_ARITY"), result.stderr);
    }

    @Test
    void boundsViolationProducesError() {
        String src = "class Box<T extends Number> { kaam init(){} } class Foo { static kaam main(){ new Box<sab>(); } }";
        var result = RuntimeTestUtil.runSource(src);
        assertTrue(result.hadCompileErrors);
        assertTrue(result.stderr.contains("does not satisfy bound") || result.stderr.contains("BOUNDS_VIOLATION"), result.stderr);
    }

    @Test
    void substitutionWorksForFieldAndMethod() {
        String src = String.join("\n",
            "class Box<T> {",
            "  public T value;",
            "  T get(){ return value; }",
            "  kaam init(T v){ value = v; }",
            "}",
            "class A { static kaam main(){",
            "  Box<num> b = new Box<num>(3);",
            "  num x = b.get();",
            "  printLine(x);",
            "} }"
        );
        var result = RuntimeTestUtil.runSource(src);
        assertFalse(result.hadCompileErrors, result.stderr);
        assertFalse(result.hadRuntimeError, result.runtimeErrorMessage);
        assertEquals("3", result.stdout.trim());
    }
}
