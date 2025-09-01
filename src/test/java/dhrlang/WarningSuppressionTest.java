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

public class WarningSuppressionTest {
    @Test
    void suppressUnusedVariableNextLine() {
    String code = "class A { // @suppress: UNUSED_VARIABLE\n\n kaam main(){ num x = 1; } }";
        ErrorReporter er = new ErrorReporter("test.dhr", code);
        Lexer lexer = new Lexer(code, er);
        List<Token> toks = lexer.scanTokens();
        Parser parser = new Parser(toks, er);
        Program p = parser.parse();
        TypeChecker tc = new TypeChecker(er);
        tc.check(p);
        assertEquals(0, er.getWarningCount(), "Expected UNUSED_VARIABLE warning suppressed");
    }

    @Test
    void noSuppressShowsWarning() {
        String code = "class B { kaam main(){ num y = 2; } }"; // y unused
        ErrorReporter er = new ErrorReporter("test.dhr", code);
        Lexer lexer = new Lexer(code, er);
        List<Token> toks = lexer.scanTokens();
        Parser parser = new Parser(toks, er);
        Program p = parser.parse();
        TypeChecker tc = new TypeChecker(er);
        tc.check(p);
        assertTrue(er.getWarningCount() > 0, "Expected at least one warning without suppression");
    }
}
