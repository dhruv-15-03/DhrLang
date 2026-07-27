package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ViewPureChecker} — @view/@pure enforcement.
 */
@DisplayName("ViewPureChecker Tests")
class ViewPureCheckerTest {

    private ViewPureChecker checker;

    @BeforeEach
    void setUp() {
        checker = new ViewPureChecker();
    }

    private Program parse(String code) {
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    // ── Valid contracts (no violations) ────────────────────────────────────

    @Nested
    @DisplayName("Valid view/pure usage")
    class ValidUsageTests {

        @Test
        @DisplayName("@view function that only reads @storage is valid")
        void viewCanReadStorage() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 100;
                    }
                    @view
                    num getBalance() {
                        return balance;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertEquals(0, checker.getErrorCount(), "A @view method reading state is valid");
        }

        @Test
        @DisplayName("@pure function with no state access is valid")
        void pureWithNoState() {
            String code = """
                @contract
                class MathUtil {
                    @storage num dummy;
                    @constructor
                    kaam init() {
                        dummy = 0;
                    }
                    @pure
                    num add(num a, num b) {
                        return a + b;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertEquals(0, checker.getErrorCount(), "A @pure method without any state access is valid");
        }

        @Test
        @DisplayName("non-annotated function can modify state freely")
        void nonAnnotatedCanModify() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 0;
                    }
                    kaam deposit(num amount) {
                        balance = balance + amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertEquals(0, checker.getErrorCount(), "Non-annotated methods can modify state");
        }

        @Test
        @DisplayName("non-contract class is not checked")
        void nonContractSkipped() {
            String code = """
                class Regular {
                    num x;
                    kaam setX(num v) {
                        x = v;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertEquals(0, checker.getErrorCount(), "Non-contract classes are skipped");
        }
    }

    // ── @view violations ──────────────────────────────────────────────────

    @Nested
    @DisplayName("@view violations (DHR-E530)")
    class ViewViolationTests {

        @Test
        @DisplayName("@view function that assigns to @storage field is rejected")
        void viewAssignsStorage() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 0;
                    }
                    @view
                    kaam modify() {
                        balance = 999;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertTrue(checker.getErrorCount() > 0, "@view modifying state should produce error");
        }
    }

    // ── @pure violations ──────────────────────────────────────────────────

    @Nested
    @DisplayName("@pure violations (DHR-E531 / DHR-E532)")
    class PureViolationTests {

        @Test
        @DisplayName("@pure function that modifies @storage field is rejected (DHR-E532)")
        void pureModifiesStorage() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 0;
                    }
                    @pure
                    kaam modify() {
                        balance = 999;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertTrue(checker.getErrorCount() > 0, "@pure modifying state should produce error");
        }

        @Test
        @DisplayName("@pure function that reads @storage field is rejected (DHR-E531)")
        void pureReadsStorage() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 0;
                    }
                    @pure
                    num readBalance() {
                        return balance;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertTrue(checker.getErrorCount() > 0, "@pure reading state should produce error");
        }
    }

    // ── @immutable violations ─────────────────────────────────────────────

    @Nested
    @DisplayName("@immutable violations (DHR-E536)")
    class ImmutableViolationTests {

        @Test
        @DisplayName("@immutable field can be assigned in @constructor")
        void immutableInConstructorOk() {
            String code = """
                @contract
                class Token {
                    @storage @immutable num owner;
                    @constructor
                    kaam init() {
                        owner = 42;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertEquals(0, checker.getErrorCount(), "@immutable in @constructor is valid");
        }

        @Test
        @DisplayName("@immutable field assigned outside @constructor is rejected")
        void immutableOutsideConstructor() {
            String code = """
                @contract
                class Token {
                    @storage @immutable num owner;
                    @constructor
                    kaam init() {
                        owner = 42;
                    }
                    kaam changeOwner() {
                        owner = 99;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertTrue(checker.getErrorCount() > 0, "@immutable assigned outside @constructor should error");
        }
    }

    // ── With ErrorReporter ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Integration with ErrorReporter")
    class ErrorReporterTests {

        @Test
        @DisplayName("errors are reported to ErrorReporter when provided")
        void errorsReportedToErrorReporter() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 0;
                    }
                    @view
                    kaam modify() {
                        balance = 123;
                    }
                }
                """;
            ErrorReporter reporter = new ErrorReporter("test.dhr", code);
            ViewPureChecker checkerWithReporter = new ViewPureChecker(reporter);
            Program program = parse(code);
            checkerWithReporter.check(program);
            assertTrue(checkerWithReporter.getErrorCount() > 0);
            assertTrue(reporter.hasErrors(), "ErrorReporter should contain the errors");
        }
    }
}
