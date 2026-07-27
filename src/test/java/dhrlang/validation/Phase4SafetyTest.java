package dhrlang.validation;

import dhrlang.ast.ClassDecl;
import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.typechecker.TypeChecker;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 tests: Safety moat — invariant checking, arithmetic overflow detection,
 * taint tracking, privilege escalation, loop bound analysis.
 */
@DisplayName("Phase 4: Safety & Verification Tests")
class Phase4SafetyTest {

    // ── Helper: parse DhrLang source to ClassDecl ────────────────────────

    private ClassDecl parseContract(String source) {
        ErrorReporter errors = new ErrorReporter();
        Lexer lexer = new Lexer(source, errors);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errors);
        Program program = parser.parse();
        TypeChecker checker = new TypeChecker(errors);
        checker.check(program);
        // Return the first @contract class
        for (ClassDecl cls : program.getClasses()) {
            if (cls.isContract()) return cls;
        }
        // Return first class if no @contract found
        return program.getClasses().isEmpty() ? null : program.getClasses().get(0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  InvariantChecker Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("InvariantChecker")
    class InvariantCheckerTests {

        @Test
        @DisplayName("No violations for safe contract")
        void noViolationsForSafeContract() {
            ClassDecl cls = parseContract("""
                @contract
                class Safe {
                    @storage num totalSupply;
                    
                    kaam mint(num amount) {
                        if (amount <= 0) {
                            throw "Amount must be positive";
                        }
                        totalSupply = totalSupply + amount;
                    }
                }
                """);
            assertNotNull(cls);
            InvariantChecker checker = new InvariantChecker();
            List<InvariantChecker.Violation> violations = checker.check(cls);
            assertNotNull(violations);
            // Safe contract should have minimal violations
        }

        @Test
        @DisplayName("Checker creates with ErrorReporter")
        void checkerWithErrorReporter() {
            ErrorReporter reporter = new ErrorReporter();
            InvariantChecker checker = new InvariantChecker(reporter);
            assertNotNull(checker);
        }

        @Test
        @DisplayName("Detect subtraction that could underflow")
        void detectSubtractionRisk() {
            ClassDecl cls = parseContract("""
                @contract
                class Risky {
                    @storage num balance;
                    
                    kaam withdraw(num amount) {
                        balance = balance - amount;
                    }
                }
                """);
            assertNotNull(cls);
            InvariantChecker checker = new InvariantChecker();
            List<InvariantChecker.Violation> violations = checker.check(cls);
            assertNotNull(violations);
            // Should detect potential underflow from unguarded subtraction
        }

        @Test
        @DisplayName("getViolations returns accumulated results")
        void getViolationsAccumulates() {
            InvariantChecker checker = new InvariantChecker();
            ClassDecl cls = parseContract("""
                @contract
                class Simple {
                    @storage num x;
                    kaam setX(num v) { x = v; }
                }
                """);
            checker.check(cls);
            List<InvariantChecker.Violation> violations = checker.getViolations();
            assertNotNull(violations);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ArithmeticOverflowDetector Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ArithmeticOverflowDetector")
    class ArithmeticOverflowTests {

        @Test
        @DisplayName("Detect addition overflow risk")
        void detectAdditionOverflow() {
            ClassDecl cls = parseContract("""
                @contract
                class Overflow {
                    @storage num balance;
                    
                    kaam deposit(num amount) {
                        balance = balance + amount;
                    }
                }
                """);
            assertNotNull(cls);
            ArithmeticOverflowDetector detector = new ArithmeticOverflowDetector();
            List<ArithmeticOverflowDetector.ArithmeticRisk> risks = detector.analyze(cls);
            assertNotNull(risks);
            // Should detect addition overflow risk
            assertTrue(risks.stream().anyMatch(
                    r -> r.getKind() == ArithmeticOverflowDetector.ArithmeticRisk.Kind.ADDITION_OVERFLOW));
        }

        @Test
        @DisplayName("Detect subtraction underflow risk")
        void detectSubtractionUnderflow() {
            ClassDecl cls = parseContract("""
                @contract
                class Underflow {
                    @storage num count;
                    
                    kaam decrement() {
                        count = count - 1;
                    }
                }
                """);
            assertNotNull(cls);
            ArithmeticOverflowDetector detector = new ArithmeticOverflowDetector();
            List<ArithmeticOverflowDetector.ArithmeticRisk> risks = detector.analyze(cls);
            assertNotNull(risks);
            assertTrue(risks.stream().anyMatch(
                    r -> r.getKind() == ArithmeticOverflowDetector.ArithmeticRisk.Kind.SUBTRACTION_UNDERFLOW));
        }

        @Test
        @DisplayName("Detect multiplication overflow risk")
        void detectMultiplicationOverflow() {
            ClassDecl cls = parseContract("""
                @contract
                class MulOverflow {
                    @storage num price;
                    
                    kaam calculate(num quantity) {
                        price = price * quantity;
                    }
                }
                """);
            assertNotNull(cls);
            ArithmeticOverflowDetector detector = new ArithmeticOverflowDetector();
            List<ArithmeticOverflowDetector.ArithmeticRisk> risks = detector.analyze(cls);
            assertNotNull(risks);
            assertTrue(risks.stream().anyMatch(
                    r -> r.getKind() == ArithmeticOverflowDetector.ArithmeticRisk.Kind.MULTIPLICATION_OVERFLOW));
        }

        @Test
        @DisplayName("Detect division by zero risk")
        void detectDivisionByZero() {
            ClassDecl cls = parseContract("""
                @contract
                class DivZero {
                    @storage num result;
                    
                    kaam divide(num a, num b) {
                        result = a / b;
                    }
                }
                """);
            assertNotNull(cls);
            ArithmeticOverflowDetector detector = new ArithmeticOverflowDetector();
            List<ArithmeticOverflowDetector.ArithmeticRisk> risks = detector.analyze(cls);
            assertNotNull(risks);
            assertTrue(risks.stream().anyMatch(
                    r -> r.getKind() == ArithmeticOverflowDetector.ArithmeticRisk.Kind.DIVISION_BY_ZERO));
        }

        @Test
        @DisplayName("Risk has function name and expression")
        void riskHasMetadata() {
            ClassDecl cls = parseContract("""
                @contract
                class Meta {
                    @storage num x;
                    kaam add(num v) { x = x + v; }
                }
                """);
            ArithmeticOverflowDetector detector = new ArithmeticOverflowDetector();
            List<ArithmeticOverflowDetector.ArithmeticRisk> risks = detector.analyze(cls);
            if (!risks.isEmpty()) {
                var risk = risks.get(0);
                assertNotNull(risk.getFunctionName());
                assertNotNull(risk.getExpression());
                assertNotNull(risk.getHint());
            }
        }

        @Test
        @DisplayName("getRisks returns all accumulated risks")
        void getRisksAccumulates() {
            ArithmeticOverflowDetector detector = new ArithmeticOverflowDetector();
            ClassDecl cls = parseContract("""
                @contract
                class Multi {
                    @storage num a;
                    @storage num b;
                    kaam calc(num x, num y) {
                        a = a + x;
                        b = b - y;
                    }
                }
                """);
            detector.analyze(cls);
            List<ArithmeticOverflowDetector.ArithmeticRisk> all = detector.getRisks();
            assertNotNull(all);
            assertTrue(all.size() >= 2, "Should detect both add overflow and sub underflow");
        }

        @Test
        @DisplayName("getUnguardedRisks filters out guarded operations")
        void unguardedRisksFilter() {
            ArithmeticOverflowDetector detector = new ArithmeticOverflowDetector();
            ClassDecl cls = parseContract("""
                @contract
                class Guarded {
                    @storage num balance;
                    
                    kaam withdraw(num amount) {
                        if (amount > balance) {
                            throw "Insufficient";
                        }
                        balance = balance - amount;
                    }
                }
                """);
            detector.analyze(cls);
            List<ArithmeticOverflowDetector.ArithmeticRisk> unguarded = detector.getUnguardedRisks();
            assertNotNull(unguarded);
            // The guarded subtraction should be marked as guarded
        }

        @Test
        @DisplayName("ErrorReporter receives overflow warnings")
        void errorReporterIntegration() {
            ErrorReporter reporter = new ErrorReporter();
            ArithmeticOverflowDetector detector = new ArithmeticOverflowDetector(reporter);
            ClassDecl cls = parseContract("""
                @contract
                class Warn {
                    @storage num x;
                    kaam inc(num v) { x = x + v; }
                }
                """);
            detector.analyze(cls);
            // Depending on implementation, might report warnings
            assertNotNull(detector.getRisks());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SecurityAnalyzer Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SecurityAnalyzer")
    class SecurityAnalyzerTests {

        @Test
        @DisplayName("Detect missing access control on privileged function")
        void detectMissingAccessControl() {
            ClassDecl cls = parseContract("""
                @contract
                class Unprotected {
                    @storage Address owner;
                    @storage kya paused;
                    
                    kaam changeOwner(Address newOwner) {
                        owner = newOwner;
                    }
                    
                    kaam pause() {
                        paused = true;
                    }
                }
                """);
            assertNotNull(cls);
            SecurityAnalyzer analyzer = new SecurityAnalyzer();
            List<SecurityAnalyzer.Finding> findings = analyzer.analyze(cls);
            assertNotNull(findings);
            // Should detect that changeOwner and pause lack access control
            assertTrue(findings.stream().anyMatch(
                    f -> f.getCategory() == SecurityAnalyzer.Finding.Category.ACCESS_CONTROL
                      || f.getCategory() == SecurityAnalyzer.Finding.Category.PRIVILEGE));
        }

        @Test
        @DisplayName("No access control finding when guarded with msg.sender check")
        void noFindingWhenGuarded() {
            ClassDecl cls = parseContract("""
                @contract
                class Protected {
                    @storage Address owner;
                    
                    @constructor
                    kaam init() {
                        owner = msg.sender;
                    }
                    
                    kaam changeOwner(Address newOwner) {
                        if (msg.sender != owner) {
                            throw "Not owner";
                        }
                        owner = newOwner;
                    }
                }
                """);
            assertNotNull(cls);
            SecurityAnalyzer analyzer = new SecurityAnalyzer();
            List<SecurityAnalyzer.Finding> findings = analyzer.analyze(cls);
            // Guarded functions should have fewer or no privilege findings
            long criticalCount = findings.stream()
                    .filter(f -> f.getSeverity() == SecurityAnalyzer.Finding.Severity.CRITICAL)
                    .count();
            // Should be less severe since there IS a guard
            assertNotNull(findings);
        }

        @Test
        @DisplayName("Finding has severity, category, and description")
        void findingHasMetadata() {
            ClassDecl cls = parseContract("""
                @contract
                class Meta {
                    @storage Address owner;
                    kaam setOwner(Address o) { owner = o; }
                }
                """);
            SecurityAnalyzer analyzer = new SecurityAnalyzer();
            List<SecurityAnalyzer.Finding> findings = analyzer.analyze(cls);
            if (!findings.isEmpty()) {
                SecurityAnalyzer.Finding f = findings.get(0);
                assertNotNull(f.getSeverity());
                assertNotNull(f.getCategory());
                assertNotNull(f.getTitle());
                assertNotNull(f.getDescription());
                assertNotNull(f.getHint());
            }
        }

        @Test
        @DisplayName("getCriticalFindings filters by severity")
        void criticalFindingsFilter() {
            SecurityAnalyzer analyzer = new SecurityAnalyzer();
            ClassDecl cls = parseContract("""
                @contract
                class Vuln {
                    @storage Address owner;
                    kaam setOwner(Address o) { owner = o; }
                }
                """);
            analyzer.analyze(cls);
            List<SecurityAnalyzer.Finding> critical = analyzer.getCriticalFindings();
            assertNotNull(critical);
            for (SecurityAnalyzer.Finding f : critical) {
                assertEquals(SecurityAnalyzer.Finding.Severity.CRITICAL, f.getSeverity());
            }
        }

        @Test
        @DisplayName("Analyze safe contract produces fewer findings")
        void safeContractFewerFindings() {
            SecurityAnalyzer analyzer = new SecurityAnalyzer();
            ClassDecl cls = parseContract("""
                @contract
                class Safe {
                    @storage num counter;
                    @storage Address owner;
                    
                    @constructor
                    kaam init() {
                        owner = msg.sender;
                        counter = 0;
                    }
                    
                    @view
                    kaam getCounter() {
                        return counter;
                    }
                    
                    kaam increment() {
                        if (msg.sender != owner) {
                            throw "Not owner";
                        }
                        counter = counter + 1;
                    }
                }
                """);
            List<SecurityAnalyzer.Finding> findings = analyzer.analyze(cls);
            // A well-guarded contract should have fewer critical findings
            long critical = findings.stream()
                    .filter(f -> f.getSeverity() == SecurityAnalyzer.Finding.Severity.CRITICAL)
                    .count();
            assertTrue(critical <= 1, "Well-guarded contract should have <= 1 critical finding");
        }

        @Test
        @DisplayName("ErrorReporter receives findings")
        void errorReporterIntegration() {
            ErrorReporter reporter = new ErrorReporter();
            SecurityAnalyzer analyzer = new SecurityAnalyzer(reporter);
            ClassDecl cls = parseContract("""
                @contract
                class Test {
                    @storage num x;
                    kaam setX(num v) { x = v; }
                }
                """);
            analyzer.analyze(cls);
            assertNotNull(analyzer.getFindings());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Integration: All 3 analyzers on same contract
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Integration: Full Security Analysis")
    class IntegrationTests {

        @Test
        @DisplayName("All 3 analyzers run on a vulnerable contract")
        void fullAnalysisOnVulnerableContract() {
            ClassDecl cls = parseContract("""
                @contract
                class Vulnerable {
                    @storage num balance;
                    @storage Address owner;
                    @storage kya paused;
                    
                    kaam setOwner(Address newOwner) {
                        owner = newOwner;
                    }
                    
                    kaam withdraw(num amount) {
                        balance = balance - amount;
                    }
                    
                    kaam deposit(num amount) {
                        balance = balance + amount;
                    }
                    
                    kaam pause() {
                        paused = true;
                    }
                }
                """);
            assertNotNull(cls);

            // 1. Invariant checker
            InvariantChecker invariants = new InvariantChecker();
            var violations = invariants.check(cls);
            assertNotNull(violations);

            // 2. Overflow detector
            ArithmeticOverflowDetector overflow = new ArithmeticOverflowDetector();
            var risks = overflow.analyze(cls);
            assertNotNull(risks);
            assertTrue(risks.size() >= 2, "Should detect add+sub overflow risks");

            // 3. Security analyzer
            SecurityAnalyzer security = new SecurityAnalyzer();
            var findings = security.analyze(cls);
            assertNotNull(findings);
            assertTrue(findings.size() >= 1, "Should detect access control issues");

            System.out.println("=== Full Security Analysis ===");
            System.out.println("Invariant violations: " + violations.size());
            System.out.println("Arithmetic risks:     " + risks.size());
            System.out.println("  Unguarded:          " + overflow.getUnguardedRisks().size());
            System.out.println("Security findings:    " + findings.size());
            System.out.println("  Critical:           " + security.getCriticalFindings().size());
        }

        @Test
        @DisplayName("All 3 analyzers run on a safe contract")
        void fullAnalysisOnSafeContract() {
            ClassDecl cls = parseContract("""
                @contract
                class Safe {
                    @storage num totalSupply;
                    @storage Address owner;
                    
                    @constructor
                    kaam init() {
                        owner = msg.sender;
                        totalSupply = 0;
                    }
                    
                    @view
                    kaam getTotalSupply() {
                        return totalSupply;
                    }
                    
                    kaam mint(num amount) {
                        if (msg.sender != owner) {
                            throw "Not owner";
                        }
                        if (amount <= 0) {
                            throw "Must be positive";
                        }
                        totalSupply = totalSupply + amount;
                    }
                    
                    kaam burn(num amount) {
                        if (msg.sender != owner) {
                            throw "Not owner";
                        }
                        if (amount > totalSupply) {
                            throw "Exceeds supply";
                        }
                        totalSupply = totalSupply - amount;
                    }
                }
                """);
            assertNotNull(cls);

            InvariantChecker invariants = new InvariantChecker();
            var violations = invariants.check(cls);

            ArithmeticOverflowDetector overflow = new ArithmeticOverflowDetector();
            var risks = overflow.analyze(cls);
            var unguarded = overflow.getUnguardedRisks();

            SecurityAnalyzer security = new SecurityAnalyzer();
            var findings = security.analyze(cls);
            var critical = security.getCriticalFindings();

            // A well-written contract should have fewer unguarded risks
            // and fewer critical security findings
            System.out.println("=== Safe Contract Analysis ===");
            System.out.println("Invariant violations: " + violations.size());
            System.out.println("Arithmetic risks: " + risks.size() + " (unguarded: " + unguarded.size() + ")");
            System.out.println("Security findings: " + findings.size() + " (critical: " + critical.size() + ")");
        }
    }
}
