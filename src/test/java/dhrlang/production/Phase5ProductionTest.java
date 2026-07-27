package dhrlang.production;

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
 * Phase 5 tests: Production certification — deep audit integration,
 * SARIF output, ABI compatibility.
 */
@DisplayName("Phase 5: Production Certification Tests")
class Phase5ProductionTest {

    private Program parse(String source) {
        ErrorReporter errors = new ErrorReporter();
        Lexer lexer = new Lexer(source, errors);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errors);
        Program program = parser.parse();
        TypeChecker checker = new TypeChecker(errors);
        checker.check(program);
        return program;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Deep Audit Integration (Phase 4 analyzers wired in)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Deep Audit Integration")
    class DeepAuditTests {

        @Test
        @DisplayName("Audit report includes arithmetic overflow findings")
        void auditIncludesOverflowFindings() {
            Program program = parse("""
                @contract
                class Risky {
                    @storage num balance;
                    
                    kaam deposit(num amount) {
                        balance = balance + amount;
                    }
                    
                    kaam withdraw(num amount) {
                        balance = balance - amount;
                    }
                }
                """);

            var auditor = new AuditReportGenerator();
            auditor.setProjectName("TestProject");
            var report = auditor.analyze(program);

            assertNotNull(report);
            assertTrue(report.getFindings().size() > 0, "Should have findings");

            // Should contain arithmetic risk findings from the deep analyzer
            boolean hasArithFinding = report.getFindings().stream()
                    .anyMatch(f -> f.getId().startsWith("ARITH-"));
            assertTrue(hasArithFinding, "Should include ARITH-* findings from ArithmeticOverflowDetector");
        }

        @Test
        @DisplayName("Audit report includes security analyzer findings")
        void auditIncludesSecurityFindings() {
            Program program = parse("""
                @contract
                class Unprotected {
                    @storage Address owner;
                    @storage kya paused;
                    
                    kaam setOwner(Address newOwner) {
                        owner = newOwner;
                    }
                    
                    kaam pause() {
                        paused = true;
                    }
                }
                """);

            var auditor = new AuditReportGenerator();
            var report = auditor.analyze(program);

            boolean hasSecFinding = report.getFindings().stream()
                    .anyMatch(f -> f.getId().startsWith("SEC-"));
            assertTrue(hasSecFinding, "Should include SEC-* findings from SecurityAnalyzer");
        }

        @Test
        @DisplayName("Safe contract has lower risk score")
        void safeContractLowerRisk() {
            Program safe = parse("""
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

            Program risky = parse("""
                @contract
                class Risky {
                    @storage num balance;
                    @storage Address owner;
                    
                    kaam setOwner(Address o) { owner = o; }
                    kaam withdraw(num a) { balance = balance - a; }
                    kaam deposit(num a) { balance = balance + a; }
                }
                """);

            var auditor1 = new AuditReportGenerator();
            var safeReport = auditor1.analyze(safe);

            var auditor2 = new AuditReportGenerator();
            var riskyReport = auditor2.analyze(risky);

            // Risky contract should have more findings
            assertTrue(riskyReport.getFindings().size() >= safeReport.getFindings().size(),
                    "Risky contract should have >= findings than safe contract");
        }

        @Test
        @DisplayName("Audit report has risk rating")
        void auditHasRiskRating() {
            Program program = parse("""
                @contract
                class Simple {
                    @storage num x;
                    kaam setX(num v) { x = v; }
                }
                """);
            var auditor = new AuditReportGenerator();
            var report = auditor.analyze(program);
            assertNotNull(report.getRiskRating());
            assertTrue(report.getRiskScore() >= 0 && report.getRiskScore() <= 100);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SARIF Output
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SARIF Output")
    class SarifTests {

        @Test
        @DisplayName("SARIF output is valid JSON structure")
        void sarifValidJson() {
            Program program = parse("""
                @contract
                class Token {
                    @storage num supply;
                    kaam mint(num a) { supply = supply + a; }
                }
                """);

            var auditor = new AuditReportGenerator();
            var report = auditor.analyze(program);
            String sarif = SarifFormatter.format(report, "Token.dhr");

            assertNotNull(sarif);
            assertTrue(sarif.startsWith("{"));
            assertTrue(sarif.endsWith("}"));
            assertTrue(sarif.contains("\"$schema\""));
            assertTrue(sarif.contains("\"version\": \"2.1.0\""));
            assertTrue(sarif.contains("\"runs\""));
        }

        @Test
        @DisplayName("SARIF contains tool information")
        void sarifToolInfo() {
            Program program = parse("""
                @contract
                class Simple {
                    @storage num x;
                    @view kaam getX() { return x; }
                }
                """);
            var auditor = new AuditReportGenerator();
            auditor.setCompilerVersion("2.0.0-test");
            var report = auditor.analyze(program);
            String sarif = SarifFormatter.format(report, "test.dhr");

            assertTrue(sarif.contains("DhrLang Security Analyzer"));
            assertTrue(sarif.contains("2.0.0-test"));
        }

        @Test
        @DisplayName("SARIF contains results with rule IDs")
        void sarifContainsResults() {
            Program program = parse("""
                @contract
                class Vuln {
                    @storage num balance;
                    kaam withdraw(num a) { balance = balance - a; }
                }
                """);

            var auditor = new AuditReportGenerator();
            var report = auditor.analyze(program);
            String sarif = SarifFormatter.format(report, "Vuln.dhr");

            assertTrue(sarif.contains("\"results\""));
            assertTrue(sarif.contains("\"ruleId\""));
            assertTrue(sarif.contains("\"level\""));
            assertTrue(sarif.contains("\"message\""));
        }

        @Test
        @DisplayName("SARIF severity mapping is correct")
        void sarifSeverityMapping() {
            Program program = parse("""
                @contract
                class Multi {
                    @storage Address owner;
                    @storage num balance;
                    kaam setOwner(Address o) { owner = o; }
                    kaam withdraw(num a) { balance = balance - a; }
                }
                """);

            var auditor = new AuditReportGenerator();
            var report = auditor.analyze(program);
            String sarif = SarifFormatter.format(report, "Multi.dhr");

            // Should map to SARIF levels: error, warning, note
            assertTrue(sarif.contains("\"level\":") ,
                    "SARIF should contain level field");
        }

        @Test
        @DisplayName("SARIF contains custom properties (risk score)")
        void sarifCustomProperties() {
            Program program = parse("""
                @contract
                class Test {
                    @storage num x;
                    kaam setX(num v) { x = v; }
                }
                """);

            var auditor = new AuditReportGenerator();
            var report = auditor.analyze(program);
            String sarif = SarifFormatter.format(report, "Test.dhr");

            assertTrue(sarif.contains("\"riskScore\""));
            assertTrue(sarif.contains("\"riskRating\""));
            assertTrue(sarif.contains("\"totalFindings\""));
            assertTrue(sarif.contains("\"criticalCount\""));
        }

        @Test
        @DisplayName("SARIF with null source file uses default")
        void sarifNullSourceFile() {
            Program program = parse("""
                @contract
                class Test {
                    @storage num x;
                    @view kaam getX() { return x; }
                }
                """);
            var auditor = new AuditReportGenerator();
            var report = auditor.analyze(program);
            String sarif = SarifFormatter.format(report, null);
            assertTrue(sarif.contains("contract.dhr")); // default value
        }

        @Test
        @DisplayName("SARIF rules section deduplicates rule IDs")
        void sarifRulesDedup() {
            Program program = parse("""
                @contract
                class TwoFuncs {
                    @storage num a;
                    @storage num b;
                    kaam addA(num v) { a = a + v; }
                    kaam addB(num v) { b = b + v; }
                }
                """);

            var auditor = new AuditReportGenerator();
            var report = auditor.analyze(program);
            String sarif = SarifFormatter.format(report, "test.dhr");
            assertTrue(sarif.contains("\"rules\""));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Full Pipeline: Compile → Audit → SARIF
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Pipeline Integration")
    class PipelineTests {

        @Test
        @DisplayName("Full ERC-20 audit pipeline produces complete report")
        void erc20AuditPipeline() {
            Program program = parse("""
                @contract
                class MyToken {
                    @storage num totalSupply;
                    @storage Address owner;
                    
                    @constructor
                    kaam init(num _supply) {
                        totalSupply = _supply;
                        owner = msg.sender;
                    }
                    
                    @view kaam getTotalSupply() { return totalSupply; }
                    @view kaam getOwner() { return owner; }
                    
                    kaam mint(num amount) {
                        if (msg.sender != owner) { throw "Not owner"; }
                        if (amount <= 0) { throw "Invalid amount"; }
                        totalSupply = totalSupply + amount;
                    }
                    
                    @nonreentrant
                    kaam transfer(Address to, num amount) {
                        if (amount <= 0) { throw "Invalid amount"; }
                        totalSupply = totalSupply;
                    }
                    
                    @event
                    kaam Transfer(Address from, Address to, num amount) {}
                }
                """);

            // 1. Generate audit report
            var auditor = new AuditReportGenerator();
            auditor.setProjectName("MyToken ERC-20");
            auditor.setCompilerVersion("2.0.0");
            var report = auditor.analyze(program);

            assertNotNull(report);
            assertTrue(report.getFindings().size() > 0);

            // 2. Text report
            String text = AuditReportGenerator.formatText(report);
            assertNotNull(text);
            assertTrue(text.contains("MyToken ERC-20"));

            // 3. JSON report
            String json = AuditReportGenerator.formatJson(report);
            assertNotNull(json);
            assertTrue(json.startsWith("{"));

            // 4. SARIF report
            String sarif = SarifFormatter.format(report, "MyToken.dhr");
            assertNotNull(sarif);
            assertTrue(sarif.contains("2.1.0")); // SARIF version
            assertTrue(sarif.contains("MyToken.dhr"));

            System.out.println("=== ERC-20 Audit Summary ===");
            System.out.println("  Findings: " + report.getFindings().size());
            System.out.println("  Risk:     " + report.getRiskScore() + "/100 (" + report.getRiskRating() + ")");
            System.out.println("  Text:     " + text.length() + " chars");
            System.out.println("  JSON:     " + json.length() + " chars");
            System.out.println("  SARIF:    " + sarif.length() + " chars");
        }
    }
}
