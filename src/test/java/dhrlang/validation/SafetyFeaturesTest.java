package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Iteration 2 – Safety Features:
 * <ul>
 *   <li>{@link StatementClassifier} – CEI statement categorization</li>
 *   <li>{@link NonReentrantChecker} – @nonreentrant mutex semantics</li>
 *   <li>{@link EffectOrderingAnalyzer} – CEI pattern enforcement</li>
 *   <li>{@link CheckedArithmetic} – overflow/underflow detection</li>
 *   <li>{@link AccessControlChecker} – @payable / msg.value patterns</li>
 * </ul>
 */
@DisplayName("Iteration 2 — Safety Features Tests")
class SafetyFeaturesTest {

    // ── Shared parse helper ──────────────────────────────────────────────

    private Program parse(String code) {
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  1. StatementClassifier Tests
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("StatementClassifier Tests")
    class StatementClassifierTests {

        @Test
        @DisplayName("Assignment to @storage field is classified as EFFECT")
        void storageAssignmentIsEffect() {
            Set<String> storageFields = Set.of("balance", "totalSupply");
            Set<String> contractMethods = Set.of("init", "deposit");
            StatementClassifier classifier = new StatementClassifier(storageFields, contractMethods);

            // Build a minimal body: balance = 100;
            Token balanceTok = new Token(dhrlang.lexer.TokenType.IDENTIFIER, "balance", 1);
            Expression value = new LiteralExpr(100.0);
            AssignmentExpr assign = new AssignmentExpr(balanceTok, value);
            ExpressionStmt stmt = new ExpressionStmt(assign);

            List<StatementClassifier.ClassifiedStatement> result =
                    classifier.classify(List.of(stmt));

            assertEquals(1, result.size());
            assertEquals(StatementClassifier.Category.EFFECT, result.get(0).getCategory(),
                    "Assignment to @storage field should be EFFECT");
        }

        @Test
        @DisplayName("Assignment to non-storage variable is NONE")
        void nonStorageAssignmentIsNone() {
            Set<String> storageFields = Set.of("balance");
            Set<String> contractMethods = Set.of("init");
            StatementClassifier classifier = new StatementClassifier(storageFields, contractMethods);

            Token localTok = new Token(dhrlang.lexer.TokenType.IDENTIFIER, "temp", 1);
            Expression value = new LiteralExpr(42.0);
            AssignmentExpr assign = new AssignmentExpr(localTok, value);
            ExpressionStmt stmt = new ExpressionStmt(assign);

            List<StatementClassifier.ClassifiedStatement> result =
                    classifier.classify(List.of(stmt));

            assertEquals(StatementClassifier.Category.NONE, result.get(0).getCategory(),
                    "Assignment to non-storage variable should be NONE");
        }

        @Test
        @DisplayName("External method call is classified as INTERACTION")
        void externalCallIsInteraction() {
            Set<String> storageFields = Set.of("balance");
            Set<String> contractMethods = Set.of("init");
            StatementClassifier classifier = new StatementClassifier(storageFields, contractMethods);

            // Build: token.transfer()
            Token tokenTok = new Token(dhrlang.lexer.TokenType.IDENTIFIER, "token", 1);
            Token transferTok = new Token(dhrlang.lexer.TokenType.IDENTIFIER, "transfer", 1);
            VariableExpr tokenExpr = new VariableExpr(tokenTok);
            GetExpr getExpr = new GetExpr(tokenExpr, transferTok);
            CallExpr call = new CallExpr(getExpr, List.of());
            ExpressionStmt stmt = new ExpressionStmt(call);

            List<StatementClassifier.ClassifiedStatement> result =
                    classifier.classify(List.of(stmt));

            assertEquals(StatementClassifier.Category.INTERACTION, result.get(0).getCategory(),
                    "External method call should be INTERACTION");
        }

        @Test
        @DisplayName("this.internalMethod() is classified as NONE")
        void thisMethodCallIsNone() {
            Set<String> storageFields = Set.of("balance");
            Set<String> contractMethods = Set.of("init", "helper");
            StatementClassifier classifier = new StatementClassifier(storageFields, contractMethods);

            // Build: this.helper()
            Token thisTok = new Token(dhrlang.lexer.TokenType.THIS, "this", 1);
            Token helperTok = new Token(dhrlang.lexer.TokenType.IDENTIFIER, "helper", 1);
            ThisExpr thisExpr = new ThisExpr(thisTok);
            GetExpr getExpr = new GetExpr(thisExpr, helperTok);
            CallExpr call = new CallExpr(getExpr, List.of());
            ExpressionStmt stmt = new ExpressionStmt(call);

            List<StatementClassifier.ClassifiedStatement> result =
                    classifier.classify(List.of(stmt));

            assertEquals(1, result.size());
            assertEquals(StatementClassifier.Category.NONE, result.get(0).getCategory(),
                    "this.knownMethod() should be NONE (internal)");
        }

        @Test
        @DisplayName("Empty statement list returns empty classification")
        void emptyListReturnsEmpty() {
            StatementClassifier classifier = new StatementClassifier(Set.of(), Set.of());
            List<StatementClassifier.ClassifiedStatement> result =
                    classifier.classify(List.of());
            assertTrue(result.isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  2. NonReentrantChecker Tests
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("NonReentrantChecker Tests")
    class NonReentrantCheckerTests {

        private NonReentrantChecker checker;

        @BeforeEach
        void setUp() {
            checker = new NonReentrantChecker();
        }

        @Test
        @DisplayName("@nonreentrant method with no calls is valid")
        void nonReentrantWithNoCallsIsValid() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 0;
                    }
                    @nonreentrant
                    kaam withdraw(num amount) {
                        balance = balance - amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertFalse(checker.hasErrors(), "Simple @nonreentrant without calls should be valid");
        }

        @Test
        @DisplayName("@nonreentrant calling another @nonreentrant is DHR-E538")
        void nonReentrantCallingNonReentrantIsError() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 0;
                    }
                    @nonreentrant
                    kaam withdraw(num amount) {
                        balance = balance - amount;
                        this.refund(amount);
                    }
                    @nonreentrant
                    kaam refund(num amount) {
                        balance = balance + amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertTrue(checker.hasErrors(), "Calling @nonreentrant from @nonreentrant should be error");
            assertTrue(checker.hasError("DHR-E538"),
                    "Should produce DHR-E538 nested reentrancy error");
        }

        @Test
        @DisplayName("Non-contract class is skipped")
        void nonContractClassIsSkipped() {
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
            assertFalse(checker.hasErrors(), "Non-contract classes should be skipped");
        }

        @Test
        @DisplayName("@nonreentrant calling a normal method is valid")
        void nonReentrantCallingNormalMethodIsValid() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 0;
                    }
                    @nonreentrant
                    kaam withdraw(num amount) {
                        this.update(amount);
                    }
                    kaam update(num amount) {
                        balance = balance - amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertFalse(checker.hasError("DHR-E538"),
                    "@nonreentrant calling a non-reentrant method should not be error");
        }

        @Test
        @DisplayName("ErrorReporter integration works for NonReentrantChecker")
        void errorReporterIntegration() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 0;
                    }
                    @nonreentrant
                    kaam withdraw(num amount) {
                        this.refund(amount);
                    }
                    @nonreentrant
                    kaam refund(num amount) {
                        balance = balance + amount;
                    }
                }
                """;
            ErrorReporter reporter = new ErrorReporter("test.dhr", code);
            NonReentrantChecker checkerWithReporter = new NonReentrantChecker(reporter);
            Program program = parse(code);
            checkerWithReporter.check(program);
            assertTrue(checkerWithReporter.hasErrors(),
                    "ErrorReporter-backed checker should detect errors");
            assertTrue(reporter.hasErrors(),
                    "ErrorReporter should contain the errors");
        }

        @Test
        @DisplayName("Multiple contracts are each checked independently")
        void multipleContractsCheckedIndependently() {
            String code = """
                @contract
                class Vault1 {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                    @nonreentrant
                    kaam withdraw(num amount) {
                        balance = balance - amount;
                    }
                }
                @contract
                class Vault2 {
                    @storage num funds;
                    @constructor
                    kaam init() { funds = 0; }
                    @nonreentrant
                    kaam deposit(num amount) {
                        funds = funds + amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.check(program);
            assertFalse(checker.hasErrors(), "Two valid contracts should produce no errors");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  3. EffectOrderingAnalyzer Tests
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EffectOrderingAnalyzer Tests")
    class EffectOrderingAnalyzerTests {

        private EffectOrderingAnalyzer analyzer;

        @BeforeEach
        void setUp() {
            analyzer = new EffectOrderingAnalyzer();
        }

        @Test
        @DisplayName("Correct CEI order produces no violations")
        void correctCEIOrderIsValid() {
            // Pattern: check → effect → interaction
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                    kaam withdraw(num amount) {
                        if (balance > amount) {
                            balance = balance - amount;
                        }
                    }
                }
                """;
            Program program = parse(code);
            analyzer.analyze(program);
            assertFalse(analyzer.hasViolations(),
                    "Correct CEI ordering should produce no violations");
        }

        @Test
        @DisplayName("Non-contract class is skipped by analyzer")
        void nonContractSkipped() {
            String code = """
                class Regular {
                    num x;
                    kaam doStuff() {
                        x = 42;
                    }
                }
                """;
            Program program = parse(code);
            analyzer.analyze(program);
            assertFalse(analyzer.hasViolations(),
                    "Non-contract classes should be skipped");
        }

        @Test
        @DisplayName("@view methods are skipped by CEI analyzer")
        void viewMethodsSkipped() {
            String code = """
                @contract
                class Token {
                    @storage num supply;
                    @constructor
                    kaam init() { supply = 0; }
                    @view
                    num getSupply() {
                        return supply;
                    }
                }
                """;
            Program program = parse(code);
            analyzer.analyze(program);
            assertFalse(analyzer.hasViolations(),
                    "@view methods should be skipped in CEI analysis");
        }

        @Test
        @DisplayName("@pure methods are skipped by CEI analyzer")
        void pureMethodsSkipped() {
            String code = """
                @contract
                class MathLib {
                    @storage num dummy;
                    @constructor
                    kaam init() { dummy = 0; }
                    @pure
                    num add(num a, num b) {
                        return a + b;
                    }
                }
                """;
            Program program = parse(code);
            analyzer.analyze(program);
            assertFalse(analyzer.hasViolations(),
                    "@pure methods should be skipped in CEI analysis");
        }

        @Test
        @DisplayName("ErrorReporter integration for EffectOrderingAnalyzer")
        void errorReporterIntegration() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                    kaam withdraw(num amount) {
                        balance = balance - amount;
                    }
                }
                """;
            ErrorReporter reporter = new ErrorReporter("test.dhr", code);
            EffectOrderingAnalyzer analyzerWithReporter = new EffectOrderingAnalyzer(reporter);
            Program program = parse(code);
            analyzerWithReporter.analyze(program);
            // Valid code — no violations expected
            assertFalse(analyzerWithReporter.hasViolations());
        }

        @Test
        @DisplayName("Violation count and list are accessible")
        void violationAccessors() {
            String code = """
                @contract
                class Simple {
                    @storage num val;
                    @constructor
                    kaam init() { val = 0; }
                    kaam setVal(num v) {
                        val = v;
                    }
                }
                """;
            Program program = parse(code);
            analyzer.analyze(program);
            assertEquals(0, analyzer.getViolationCount());
            assertTrue(analyzer.getViolations().isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  4. CheckedArithmetic Tests
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CheckedArithmetic Tests")
    class CheckedArithmeticTests {

        private CheckedArithmetic checker;

        @BeforeEach
        void setUp() {
            checker = new CheckedArithmetic();
        }

        @Test
        @DisplayName("Addition on uint256 flags overflow DHR-E542")
        void uint256AdditionOverflow() {
            String code = """
                @contract
                class Token {
                    @storage uint256 totalSupply;
                    @constructor
                    kaam init() { totalSupply = 0; }
                    kaam mint(uint256 amount) {
                        totalSupply = totalSupply + amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertTrue(checker.hasIssues(), "uint256 addition should flag overflow");
            assertTrue(checker.hasIssue("DHR-E542"),
                    "Should produce DHR-E542 overflow warning");
        }

        @Test
        @DisplayName("Subtraction on uint256 flags underflow DHR-E543")
        void uint256SubtractionUnderflow() {
            String code = """
                @contract
                class Token {
                    @storage uint256 balance;
                    @constructor
                    kaam init() { balance = 0; }
                    kaam burn(uint256 amount) {
                        balance = balance - amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertTrue(checker.hasIssues(), "uint256 subtraction should flag underflow");
            assertTrue(checker.hasIssue("DHR-E543"),
                    "Should produce DHR-E543 underflow warning");
        }

        @Test
        @DisplayName("Multiplication on uint256 flags overflow DHR-E542")
        void uint256MultiplicationOverflow() {
            String code = """
                @contract
                class Token {
                    @storage uint256 total;
                    @constructor
                    kaam init() { total = 0; }
                    kaam scale(uint256 factor) {
                        total = total * factor;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertTrue(checker.hasIssue("DHR-E542"),
                    "uint256 multiplication should flag DHR-E542 overflow");
        }

        @Test
        @DisplayName("Division on uint256 flags division by zero DHR-E544")
        void uint256DivisionByZero() {
            String code = """
                @contract
                class Token {
                    @storage uint256 value;
                    @constructor
                    kaam init() { value = 0; }
                    kaam divide(uint256 divisor) {
                        value = value / divisor;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertTrue(checker.hasIssue("DHR-E544"),
                    "uint256 division should flag DHR-E544 division by zero");
        }

        @Test
        @DisplayName("Arithmetic on num type is NOT checked")
        void numTypeNotChecked() {
            String code = """
                @contract
                class Counter {
                    @storage num count;
                    @constructor
                    kaam init() { count = 0; }
                    kaam increment(num amount) {
                        count = count + amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertFalse(checker.hasIssues(),
                    "num type should not be checked for blockchain arithmetic");
        }

        @Test
        @DisplayName("int256 arithmetic is also checked")
        void int256ArithmeticChecked() {
            String code = """
                @contract
                class SignedMath {
                    @storage int256 value;
                    @constructor
                    kaam init() { value = 0; }
                    kaam update(int256 delta) {
                        value = value + delta;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertTrue(checker.hasIssues(), "int256 arithmetic should be checked");
        }

        @Test
        @DisplayName("wei arithmetic is also checked")
        void weiArithmeticChecked() {
            String code = """
                @contract
                class Payable {
                    @storage wei amount;
                    @constructor
                    kaam init() { amount = 0; }
                    kaam deposit(wei value) {
                        amount = amount + value;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertTrue(checker.hasIssues(), "wei arithmetic should be checked");
        }

        @Test
        @DisplayName("Non-contract class is skipped")
        void nonContractSkipped() {
            String code = """
                class MathHelper {
                    num x;
                    kaam compute(num a, num b) {
                        return a + b;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertFalse(checker.hasIssues(), "Non-contract classes should be skipped");
        }

        @Test
        @DisplayName("isCheckedType utility works correctly")
        void isCheckedTypeUtility() {
            assertTrue(CheckedArithmetic.isCheckedType("uint256"));
            assertTrue(CheckedArithmetic.isCheckedType("int256"));
            assertTrue(CheckedArithmetic.isCheckedType("wei"));
            assertFalse(CheckedArithmetic.isCheckedType("num"));
            assertFalse(CheckedArithmetic.isCheckedType("str"));
            assertFalse(CheckedArithmetic.isCheckedType("bool"));
        }

        @Test
        @DisplayName("Issue accessors return correct data")
        void issueAccessors() {
            String code = """
                @contract
                class Token {
                    @storage uint256 val;
                    @constructor
                    kaam init() { val = 0; }
                    kaam add(uint256 x) {
                        val = val + x;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertTrue(checker.getIssueCount() > 0);
            CheckedArithmetic.ArithmeticIssue issue = checker.getIssues().get(0);
            assertNotNull(issue.getErrorCode());
            assertNotNull(issue.getMessage());
            assertNotNull(issue.getOperator());
            assertNotNull(issue.getTypeName());
            assertNotNull(issue.getFunctionName());
            assertNotNull(issue.toString());
        }

        @Test
        @DisplayName("ErrorReporter integration for CheckedArithmetic")
        void errorReporterIntegration() {
            String code = """
                @contract
                class Token {
                    @storage uint256 val;
                    @constructor
                    kaam init() { val = 0; }
                    kaam add(uint256 x) {
                        val = val + x;
                    }
                }
                """;
            ErrorReporter reporter = new ErrorReporter("test.dhr", code);
            CheckedArithmetic checkerWithReporter = new CheckedArithmetic(reporter);
            Program program = parse(code);
            checkerWithReporter.analyze(program);
            assertTrue(checkerWithReporter.hasIssues());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  5. AccessControlChecker Tests
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AccessControlChecker Tests")
    class AccessControlCheckerTests {

        private AccessControlChecker checker;

        @BeforeEach
        void setUp() {
            checker = new AccessControlChecker();
        }

        @Test
        @DisplayName("@payable function that checks msg.value is valid")
        void payableWithMsgValueCheckIsValid() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                    @payable
                    kaam deposit() {
                        num amount = msg.value;
                        balance = balance + amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertFalse(checker.hasIssue("DHR-E550"),
                    "@payable function using msg.value should NOT produce DHR-E550");
        }

        @Test
        @DisplayName("@payable function without msg.value check is DHR-E550")
        void payableWithoutMsgValueIsError() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                    @payable
                    kaam deposit(num amount) {
                        balance = balance + amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertTrue(checker.hasIssue("DHR-E550"),
                    "@payable without msg.value should produce DHR-E550");
        }

        @Test
        @DisplayName("Non-@payable function using msg.value is DHR-E551")
        void nonPayableWithMsgValueIsError() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                    kaam sneakyRead() {
                        num x = msg.value;
                        balance = x;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertTrue(checker.hasIssue("DHR-E551"),
                    "Non-@payable accessing msg.value should produce DHR-E551");
        }

        @Test
        @DisplayName("Non-contract class is skipped")
        void nonContractSkipped() {
            String code = """
                class Helper {
                    num x;
                    kaam setX(num v) {
                        x = v;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertFalse(checker.hasIssues(), "Non-contract classes should be skipped");
        }

        @Test
        @DisplayName("@view method is skipped for payable analysis")
        void viewMethodSkipped() {
            String code = """
                @contract
                class Token {
                    @storage num supply;
                    @constructor
                    kaam init() { supply = 100; }
                    @view
                    num getSupply() {
                        return supply;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertFalse(checker.hasIssues(),
                    "@view methods should not produce access control issues");
        }

        @Test
        @DisplayName("@pure method is skipped for payable analysis")
        void pureMethodSkipped() {
            String code = """
                @contract
                class MathLib {
                    @storage num dummy;
                    @constructor
                    kaam init() { dummy = 0; }
                    @pure
                    num add(num a, num b) {
                        return a + b;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertFalse(checker.hasIssues(),
                    "@pure methods should not produce access control issues");
        }

        @Test
        @DisplayName("Issue accessors return correct data")
        void issueAccessors() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                    @payable
                    kaam deposit(num amount) {
                        balance = balance + amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            assertTrue(checker.getIssueCount() > 0);
            AccessControlChecker.AccessIssue issue = checker.getIssues().get(0);
            assertNotNull(issue.getErrorCode());
            assertNotNull(issue.getMessage());
            assertNotNull(issue.getFunctionName());
            assertNotNull(issue.getContractName());
            assertNotNull(issue.toString());
        }

        @Test
        @DisplayName("ErrorReporter integration for AccessControlChecker")
        void errorReporterIntegration() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                    @payable
                    kaam deposit(num amount) {
                        balance = balance + amount;
                    }
                }
                """;
            ErrorReporter reporter = new ErrorReporter("test.dhr", code);
            AccessControlChecker checkerWithReporter = new AccessControlChecker(reporter);
            Program program = parse(code);
            checkerWithReporter.analyze(program);
            assertTrue(checkerWithReporter.hasIssues());
        }

        @Test
        @DisplayName("Multiple contracts checked independently")
        void multipleContractsIndependent() {
            String code = """
                @contract
                class Vault1 {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                    @payable
                    kaam deposit() {
                        num amt = msg.value;
                        balance = balance + amt;
                    }
                }
                @contract
                class Vault2 {
                    @storage num funds;
                    @constructor
                    kaam init() { funds = 0; }
                    @payable
                    kaam pay(num amount) {
                        funds = funds + amount;
                    }
                }
                """;
            Program program = parse(code);
            checker.analyze(program);
            // Vault1 is OK (uses msg.value), Vault2 has DHR-E550
            assertTrue(checker.hasIssue("DHR-E550"),
                    "Vault2's @payable without msg.value should be flagged");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  6. Cross-component Smoke Tests
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cross-component Integration Smoke Tests")
    class CrossComponentTests {

        @Test
        @DisplayName("All checkers can analyze the same program without interference")
        void allCheckersCoexist() {
            String code = """
                @contract
                class FullContract {
                    @storage uint256 balance;
                    @constructor
                    kaam init() { balance = 0; }
                    @payable
                    @nonreentrant
                    kaam deposit() {
                        num amount = msg.value;
                        balance = balance + amount;
                    }
                    @view
                    uint256 getBalance() {
                        return balance;
                    }
                }
                """;
            Program program = parse(code);

            NonReentrantChecker nrChecker = new NonReentrantChecker();
            nrChecker.check(program);

            EffectOrderingAnalyzer eoAnalyzer = new EffectOrderingAnalyzer();
            eoAnalyzer.analyze(program);

            CheckedArithmetic caChecker = new CheckedArithmetic();
            caChecker.analyze(program);

            AccessControlChecker acChecker = new AccessControlChecker();
            acChecker.analyze(program);

            // Reentrancy: no nested calls, should be clean
            assertFalse(nrChecker.hasError("DHR-E538"), "No nested @nonreentrant calls");

            // Checked arithmetic: uint256 addition should be flagged
            assertTrue(caChecker.hasIssues(), "uint256 addition should flag arithmetic check");

            // Access control: @payable uses msg.value, should be fine
            assertFalse(acChecker.hasIssue("DHR-E550"), "@payable with msg.value is valid");
        }

        @Test
        @DisplayName("Empty program produces no errors in any checker")
        void emptyProgramNoErrors() {
            String code = """
                @contract
                class Empty {
                    @storage num dummy;
                    @constructor
                    kaam init() { dummy = 0; }
                }
                """;
            Program program = parse(code);

            NonReentrantChecker nrChecker = new NonReentrantChecker();
            nrChecker.check(program);
            assertFalse(nrChecker.hasErrors());

            EffectOrderingAnalyzer eoAnalyzer = new EffectOrderingAnalyzer();
            eoAnalyzer.analyze(program);
            assertFalse(eoAnalyzer.hasViolations());

            CheckedArithmetic caChecker = new CheckedArithmetic();
            caChecker.analyze(program);
            assertFalse(caChecker.hasIssues());

            AccessControlChecker acChecker = new AccessControlChecker();
            acChecker.analyze(program);
            assertFalse(acChecker.hasIssues());
        }
    }
}
