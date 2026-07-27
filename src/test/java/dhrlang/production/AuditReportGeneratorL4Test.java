package dhrlang.production;

import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.production.AuditReportGenerator.AuditReport;
import dhrlang.production.AuditReportGenerator.Finding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the provable-safety level L4 additions to {@link AuditReportGenerator}:
 * the opt-in spec-fuzzing pass (invariant counterexamples become findings), the
 * derived safety score / grade, and the Markdown report.
 */
@DisplayName("L4: safety report (fuzzing + score + markdown)")
class AuditReportGeneratorL4Test {

    /** Invariant {@code total == a + b} is broken by the {@code + 1}. */
    private static final String BUGGY = """
            @invariant(total == a + b)
            @contract
            class Buggy {
                @storage num a;
                @storage num b;
                @storage num total;
                kaam set(num x, num y) {
                    a = x;
                    b = y;
                    total = x + y + 1;
                }
            }
            """;

    /** Same contract with the invariant correctly maintained. */
    private static final String CORRECT = """
            @invariant(total == a + b)
            @contract
            class Correct {
                @storage num a;
                @storage num b;
                @storage num total;
                kaam set(num x, num y) {
                    a = x;
                    b = y;
                    total = x + y;
                }
            }
            """;

    private static Program parse(String source) {
        ErrorReporter errors = new ErrorReporter();
        List<Token> tokens = new Lexer(source, errors).scanTokens();
        Program program = new Parser(tokens, errors).parse();
        assertFalse(errors.hasErrors(), "unexpected parse errors: " + errors.getErrorCount());
        return program;
    }

    private static AuditReport audit(String source, boolean fuzz) {
        var auditor = new AuditReportGenerator();
        if (fuzz) auditor.enableSpecFuzzing(64, 1L);
        return auditor.analyze(parse(source));
    }

    private static boolean hasRule(AuditReport r, String id) {
        return r.getFindings().stream().anyMatch(f -> id.equals(f.getId()));
    }

    @Test
    @DisplayName("fuzzing is opt-in: a plain audit emits no FUZZ findings")
    void fuzzingDisabledByDefault() {
        AuditReport report = audit(BUGGY, false);
        assertFalse(hasRule(report, "FUZZ-INVARIANT"),
                "fuzzing must be off unless explicitly enabled");
    }

    @Test
    @DisplayName("enabled fuzzing surfaces the invariant counterexample as a HIGH finding")
    void fuzzingFindsInvariantViolation() {
        AuditReport report = audit(BUGGY, true);

        Finding fuzz = report.getFindings().stream()
                .filter(f -> "FUZZ-INVARIANT".equals(f.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(fuzz, "expected a FUZZ-INVARIANT finding for the buggy contract");
        assertEquals(AuditReportGenerator.Severity.HIGH, fuzz.getSeverity());
        assertTrue(fuzz.getDescription().contains("set("),
                "the finding should carry the minimized counterexample, was: " + fuzz.getDescription());
        assertEquals("Buggy.set", fuzz.getLocation());
    }

    @Test
    @DisplayName("a correct contract yields no FUZZ-INVARIANT finding even with fuzzing on")
    void correctContractHasNoFuzzFinding() {
        AuditReport report = audit(CORRECT, true);
        assertFalse(hasRule(report, "FUZZ-INVARIANT"),
                "a sound invariant must not be flagged");
    }

    @Test
    @DisplayName("safety score is the inverse of the risk score, with a matching grade")
    void safetyScoreInverseOfRiskAndGraded() {
        AuditReport report = audit(BUGGY, true);

        assertEquals(100 - report.getRiskScore(), report.getSafetyScore(),
                "safety score must be the inverse of the risk score");
        assertEquals(expectedGrade(report.getSafetyScore()), report.getSafetyGrade(),
                "grade must follow the documented thresholds");
    }

    @Test
    @DisplayName("a finding-free audit scores 100 / grade A")
    void cleanAuditScoresFull() {
        // A finding-free report is the boundary case for the score/grade math.
        var report = new AuditReportGenerator().analyze(new Program(List.of()));
        assertTrue(report.getFindings().isEmpty(), "empty program should have no findings");
        assertEquals(0, report.getRiskScore());
        assertEquals(100, report.getSafetyScore());
        assertEquals("A", report.getSafetyGrade());
    }

    @Test
    @DisplayName("markdown report leads with the safety score and lists fuzz findings")
    void markdownContainsScoreAndFindings() {
        AuditReport report = audit(BUGGY, true);
        String md = AuditReportGenerator.formatMarkdown(report);

        assertTrue(md.startsWith("# DhrLang Safety Report"), "missing report title");
        assertTrue(md.contains("## Safety score: "), "missing safety score heading");
        assertTrue(md.contains("## Findings ("), "missing findings section");
        assertTrue(md.contains("FUZZ-INVARIANT"), "fuzz finding should appear in the report");
        assertTrue(md.contains("Buggy.set"), "finding location should appear");
    }

    @Test
    @DisplayName("JSON output carries the safety score and grade")
    void jsonContainsSafetyFields() {
        AuditReport report = audit(BUGGY, true);
        String json = AuditReportGenerator.formatJson(report);

        assertTrue(json.contains("\"safetyScore\":"), "JSON should include safetyScore");
        assertTrue(json.contains("\"safetyGrade\":"), "JSON should include safetyGrade");
    }

    private static String expectedGrade(int safetyScore) {
        if (safetyScore >= 90) return "A";
        if (safetyScore >= 75) return "B";
        if (safetyScore >= 60) return "C";
        if (safetyScore >= 40) return "D";
        return "F";
    }
}
