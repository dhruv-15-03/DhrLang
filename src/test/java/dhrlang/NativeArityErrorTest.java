package dhrlang;

import dhrlang.ast.Program;
import dhrlang.error.DhrError;
import dhrlang.error.ErrorCode;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.typechecker.TypeChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Arity checking for native (built-in) functions.
 *
 * <p>An earlier version of the {@code print()} case asserted
 * {@code codes.contains(NATIVE_ARITY) || codes.contains("NONE")}, where {@code "NONE"} was the
 * placeholder used for an error carrying a {@code null} code. That disjunction made the
 * assertion pass for essentially any reported error, so it could not fail for the reason the
 * test existed. Both cases below now assert a specific outcome.
 */
public class NativeArityErrorTest {

    private ErrorReporter check(String fileName, String src) {
        ErrorReporter reporter = new ErrorReporter(fileName, src);
        Lexer lexer = new Lexer(src, reporter);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, reporter);
        Program program = parser.parse();
        new TypeChecker(reporter).check(program);
        return reporter;
    }

    private String codesOf(ErrorReporter reporter) {
        return reporter.getErrors().stream()
                .map(e -> e.getCode() == null ? "NONE" : e.getCode().name())
                .collect(Collectors.joining(","));
    }

    @Test
    @DisplayName("print() with no arguments is rejected")
    public void printWithNoArgumentsIsRejected() {
        ErrorReporter reporter = check("print.dhr", "class Main { static kaam main() { print(); } }");

        assertTrue(reporter.hasErrors(), "print() with no arguments should be rejected");

        // print is dispatched separately from the general native-arity path, so it does not
        // carry NATIVE_ARITY. Assert the diagnostic actually describes the argument problem
        // rather than accepting any error at all.
        String messages = reporter.getErrors().stream()
                .map(DhrError::getMessage)
                .collect(Collectors.joining(" | "))
                .toLowerCase();
        assertTrue(messages.contains("argument") || messages.contains("arity") || messages.contains("print"),
                () -> "Expected a diagnostic describing the argument problem, got: " + messages);
    }

    @Test
    @DisplayName("substring() with too few arguments reports NATIVE_ARITY")
    public void substringWithTooFewArgumentsReportsNativeArity() {
        ErrorReporter reporter = check("substring.dhr",
                "class Main { static kaam main() { substring(\"abc\", 1); } }");

        assertTrue(reporter.hasErrors(), "substring() with two arguments should be rejected");

        String codes = codesOf(reporter);
        assertTrue(codes.contains(ErrorCode.NATIVE_ARITY.name()),
                () -> "Expected NATIVE_ARITY in " + codes);
    }
}
