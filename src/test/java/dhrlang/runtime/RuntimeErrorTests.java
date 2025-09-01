package dhrlang.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Negative runtime tests asserting specific RuntimeErrorCategory outputs.
 */
public class RuntimeErrorTests {
    private static String canon(String s){return s==null?"":s.replace("\r","");}
    private void assertRuntimeError(String source, String expectedCategorySubstring, String expectedMessageSubstring) {
        var result = RuntimeTestUtil.runSource(source);
        assertFalse(result.hadCompileErrors, "Expected no compile errors. stderr=\n"+result.stderr);
        assertTrue(result.hadRuntimeError, "Expected runtime error but program succeeded. stdout=\n"+result.stdout);
        if (expectedCategorySubstring != null) {
            assertNotNull(result.runtimeErrorCategory, "Runtime error category missing");
            assertTrue(result.runtimeErrorCategory.contains(expectedCategorySubstring),
                "Category mismatch. expected contains '"+expectedCategorySubstring+"' but was '"+result.runtimeErrorCategory+"'");
        }
        if (expectedMessageSubstring != null) {
            String combined = (result.runtimeErrorMessage+"\n"+result.stderr);
            assertTrue(canon(combined).contains(expectedMessageSubstring),
                "Message mismatch. expected substring '"+expectedMessageSubstring+"' but was:\n"+combined);
        }
    }

    private void assertCompileError(String source) {
        var result = RuntimeTestUtil.runSource(source);
        assertTrue(result.hadCompileErrors, "Expected compile/type error but none. stdout=\n"+result.stdout+"\nstderr=\n"+result.stderr);
        assertFalse(result.hadRuntimeError, "Did not expect runtime error for compile-time failure");
    }

    @Test @DisplayName("Type error (compile-time): calling non-function")
    void typeErrorCallingNonFunctionCompile() {
        // This is caught during type checking, not at runtime
        String src = "class A { static kaam main(){ num x = 5; x(); } }";
        assertCompileError(src);
    }

    @Test @DisplayName("Index error: array out of bounds")
    void indexErrorArrayOob() {
        // Create array of length 2 then access index 5
        String src = "class A { static kaam main(){ num[] arr = [1,2]; num y = arr[5]; } }";
        assertRuntimeError(src, "Index Error", "out of bounds");
    }

    @Test @DisplayName("Arithmetic error: division by zero")
    void arithmeticDivZero() { assertRuntimeError("class A { static kaam main(){ duo x = 10 / 0; }}", "Arithmetic Error", "Division by zero"); }

    @Test @DisplayName("Validation error: negative array size")
    void validationNegativeArraySize() {
        String src = "class A { static kaam main(){ num[] a = new num[-1]; } }";
        assertRuntimeError(src, "Validation Error", "cannot be negative");
    }

    @Test @DisplayName("Access error (compile-time): undefined static member")
    void accessUndefinedStaticMemberCompile() {
        String src = "class Example { } class Test { static kaam main(){ printLine(Example.missing); } }";
        assertCompileError(src);
    }
}
