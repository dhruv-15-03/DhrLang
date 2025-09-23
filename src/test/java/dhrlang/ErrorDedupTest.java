package dhrlang;

import dhrlang.error.*;
import dhrlang.lexer.Lexer;
import dhrlang.parser.Parser;
import dhrlang.typechecker.TypeChecker;
import dhrlang.ast.Program;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class ErrorDedupTest {

    @Test
    public void duplicateTypeErrorsReportedOnce() {
    ErrorReporter er = new ErrorReporter("manual.dhr", "");
    SourceLocation loc = new SourceLocation("manual.dhr", 1, 1);
    er.error(loc, "Type mismatch: Cannot assign type 'sab' to variable 'a' of type 'num'.", "hint", ErrorCode.TYPE_MISMATCH);
    er.error(loc, "Type mismatch: Cannot assign type 'sab' to variable 'a' of type 'num'.", "hint", ErrorCode.TYPE_MISMATCH);
    assertEquals(1, er.getErrorCount(), "Duplicate identical error should be suppressed");

    String code = "class Main1 { static kaam main() { num a = \"str\"; sab s = 123; } }";
    ErrorReporter er2 = new ErrorReporter("sem.dhr", code);
    Lexer lx = new Lexer(code, er2);
    List<dhrlang.lexer.Token> tokens = lx.scanTokens();
    Parser p = new Parser(tokens, er2);
    Program prog = p.parse();
    TypeChecker tc = new TypeChecker(er2);
    tc.check(prog);
    long distinct = er2.getErrors().stream().map(DhrError::getMessage).distinct().count();
    assertTrue(distinct >= 2, "Expected at least two distinct errors; got " + distinct);
    }

    @Test
    public void differentMessagesBothKept() {
    // Two different mismatches: assigning string to num and number literal to sab (string) variable
    String code = "class Main { static kaam main() { num x = \"str\"; sab s = 123; } }";
        ErrorReporter er = new ErrorReporter("dedup2.dhr", code);
        Lexer lx = new Lexer(code, er);
        List<dhrlang.lexer.Token> tokens = lx.scanTokens();
        Parser p = new Parser(tokens, er);
        Program prog = p.parse();
        TypeChecker tc = new TypeChecker(er);
        tc.check(prog);
        // Expect at least two distinct mismatch errors (messages differ by variable name or type mismatch description)
    long mismatchCount = er.getErrors().stream().map(DhrError::getMessage).distinct().count();
    assertTrue(mismatchCount >= 2, "Expected distinct mismatch diagnostics preserved (found=" + mismatchCount + ")");
    }

    @Test
    public void hintVariationDoesNotCreateDuplicate() {
        ErrorReporter er = new ErrorReporter("hints.dhr", "");
        SourceLocation loc = new SourceLocation("hints.dhr", 2, 4);
        er.error(loc, "Same core message", "hint A", ErrorCode.TYPE_MISMATCH);
        er.error(loc, "Same core message", "hint B", ErrorCode.TYPE_MISMATCH); // different hint, same key
        assertEquals(1, er.getErrorCount(), "Different hints for identical error should be deduplicated");
    }

    @Test
    public void differentCodesAreDistinct() {
        ErrorReporter er = new ErrorReporter("codes.dhr", "");
        SourceLocation loc = new SourceLocation("codes.dhr", 3, 7);
        er.error(loc, "Problem here", null, ErrorCode.TYPE_MISMATCH);
        er.error(loc, "Problem here", null, ErrorCode.DEAD_STORE); // same message, different code
        assertEquals(2, er.getErrorCount(), "Different codes should produce separate diagnostics");
    }

    @Test
    public void warningDedupWorks() {
        ErrorReporter er = new ErrorReporter("warns.dhr", "");
        SourceLocation loc = new SourceLocation("warns.dhr", 10, 2);
        er.warning(loc, "Possible issue", "hint", ErrorCode.UNUSED_VARIABLE);
        er.warning(loc, "Possible issue", "other hint", ErrorCode.UNUSED_VARIABLE);
        assertEquals(1, er.getWarningCount(), "Duplicate warning should be suppressed");
    }
}
