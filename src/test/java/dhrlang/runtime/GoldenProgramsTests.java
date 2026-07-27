package dhrlang.runtime;

import org.junit.jupiter.api.DisplayName;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;

public class GoldenProgramsTests {
    private String canon(String s){return s.replace("\r","") .lines().map(l->l.stripTrailing()).reduce((a,b)->a+"\n"+b).orElse("").trim();}
    private void runAndAssert(String file, String expected) throws Exception { var r = RuntimeTestUtil.runFile(file); assertFalse(r.hadCompileErrors, "Compile errors in "+file+"\n"+r.stderr); assertFalse(r.hadRuntimeError, "Runtime error in "+file+"\n"+r.stderr); assertEquals(canon(expected), canon(r.stdout), "Mismatch for "+file); }

    @Test @DisplayName("basic syntax program") void basicSyntax() throws Exception { runAndAssert("input/test_basic_syntax.dhr", String.join("\n","Integer: 42","Decimal: 3.14159","String: Hello DhrLang!","Boolean: true","Character: A","Sum: 52","Product: 6.28318","Flag is true","Loop iteration: 1","Loop iteration: 2","Loop iteration: 3","While loop: 0","While loop: 1")); }
}

