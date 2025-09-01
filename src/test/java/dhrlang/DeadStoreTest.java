package dhrlang;

import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.ast.Program;
import dhrlang.typechecker.TypeChecker;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class DeadStoreTest {
    @Test
    void deadStoreEmittedOnOverwrite() {
        String code = "class D { num f; kaam main(){ num x = 1; x = 2; } }"; // dead store: initial 1 never read
        ErrorReporter er = new ErrorReporter("test.dhr", code);
        Lexer lexer = new Lexer(code, er);
        List<Token> toks = lexer.scanTokens();
        Parser parser = new Parser(toks, er);
        Program p = parser.parse();
        TypeChecker tc = new TypeChecker(er);
        tc.check(p);
    assertTrue(er.getWarnings().stream().anyMatch(w -> w.getMessage().contains("never read before being overwritten") || w.getMessage().contains("never read.")), "Expected DEAD_STORE warning");
    }

    @Test
    void deadStoreSuppressed() {
        String code = "class E { // @suppress: DEAD_STORE\n kaam main(){ num x = 1; x = 2; } }"; // suppression comment
        ErrorReporter er = new ErrorReporter("test2.dhr", code);
        Lexer lexer = new Lexer(code, er);
        List<Token> toks = lexer.scanTokens();
        Parser parser = new Parser(toks, er);
        Program p = parser.parse();
        TypeChecker tc = new TypeChecker(er);
        tc.check(p);
        // Other warnings (like unused) might appear; ensure no dead store specific wording
    assertTrue(er.getWarnings().stream().noneMatch(w -> w.getMessage().contains("never read before being overwritten")), "Expected DEAD_STORE suppressed");
    }
}
