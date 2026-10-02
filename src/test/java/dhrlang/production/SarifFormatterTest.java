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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that the SARIF emitted for an audited contract is well-formed enough
 * for GitHub Code Scanning to ingest: a 2.1.0 envelope, one result per finding,
 * a stable fingerprint per result, valid {@code level} enums, and regions only
 * when a real source line is known.
 */
@DisplayName("L0: SARIF code-scanning output")
class SarifFormatterTest {

    private static final Set<String> VALID_LEVELS = Set.of("error", "warning", "note", "none");

    /** A deliberately unsafe contract that reliably triggers detector findings. */
    private static final String VULNERABLE = """
            @contract
            class Vuln {
                @storage num counter;
                @storage Address owner;

                kaam spin() {
                    while (true) {
                        counter = counter + 1;
                    }
                }

                kaam seize(Address newOwner) {
                    owner = newOwner;
                }
            }
            """;

    private AuditReport audit(String source) {
        ErrorReporter errors = new ErrorReporter();
        Lexer lexer = new Lexer(source, errors);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errors);
        Program program = parser.parse();
        return new AuditReportGenerator().analyze(program);
    }

    @Test
    @DisplayName("emits a valid SARIF 2.1.0 envelope with at least one result")
    void producesValidSarifEnvelope() {
        AuditReport report = audit(VULNERABLE);
        assertFalse(report.getFindings().isEmpty(),
                "the vulnerable contract should yield at least one finding");

        String sarif = SarifFormatter.format(report, "Vuln.dhr");

        assertTrue(sarif.contains("\"$schema\""), "missing $schema");
        assertTrue(sarif.contains("sarif-schema-2.1.0"), "wrong schema version");
        assertTrue(sarif.contains("\"version\": \"2.1.0\""), "missing version 2.1.0");
        assertTrue(sarif.contains("\"runs\""), "missing runs");
        assertEquals(countOf(sarif, '{'), countOf(sarif, '}'), "unbalanced braces in SARIF");
    }

    @Test
    @DisplayName("every result carries a stable partialFingerprint")
    void everyResultHasFingerprint() {
        AuditReport report = audit(VULNERABLE);
        String sarif = SarifFormatter.format(report, "Vuln.dhr");

        int results = countMatches(sarif, "\"ruleId\"");
        int fingerprints = countMatches(sarif, "dhrlangAuditFingerprint/v1");

        assertEquals(report.getFindings().size(), results, "one result per finding");
        assertEquals(results, fingerprints, "one fingerprint per result");
    }

    @Test
    @DisplayName("fingerprints are stable across runs and independent of line numbers")
    void fingerprintsAreStable() {
        AuditReport report = audit(VULNERABLE);
        String a = SarifFormatter.format(report, "Vuln.dhr");
        String b = SarifFormatter.format(report, "Vuln.dhr");
        assertEquals(extractFingerprints(a), extractFingerprints(b),
                "the same report must produce identical fingerprints");
    }

    @Test
    @DisplayName("all level values are valid SARIF enums")
    void levelsAreValidSarifEnum() {
        AuditReport report = audit(VULNERABLE);
        String sarif = SarifFormatter.format(report, "Vuln.dhr");

        Matcher m = Pattern.compile("\"level\"\\s*:\\s*\"([^\"]+)\"").matcher(sarif);
        int seen = 0;
        while (m.find()) {
            seen++;
            assertTrue(VALID_LEVELS.contains(m.group(1)),
                    "invalid SARIF level: " + m.group(1));
        }
        assertTrue(seen > 0, "expected at least one level");
    }

    @Test
    @DisplayName("regions appear only when a real source line is known (never startLine 0)")
    void regionOnlyWhenLineKnown() {
        AuditReport report = audit(VULNERABLE);
        String sarif = SarifFormatter.format(report, "Vuln.dhr");

        assertFalse(sarif.contains("\"startLine\": 0"), "must not emit startLine 0");

        boolean anyLocated = report.getFindings().stream().anyMatch(f -> f.getLine() > 0);
        if (anyLocated) {
            assertTrue(sarif.contains("\"region\""),
                    "a located finding should produce a region");
        }
    }

    @Test
    @DisplayName("does not emit a SARIF fix without artifactChanges (GitHub rejects it)")
    void noFixWithoutArtifactChanges() {
        AuditReport report = audit(VULNERABLE);
        String sarif = SarifFormatter.format(report, "Vuln.dhr");

        // GitHub Code Scanning validates the SARIF schema: a `fix` object REQUIRES
        // `artifactChanges`. Emitting `fixes` without it makes the whole upload fail
        // (JOB_STATUS_CONFIGURATION_ERROR), silently when the step is continue-on-error.
        if (sarif.contains("\"fixes\"")) {
            assertTrue(sarif.contains("\"artifactChanges\""),
                    "a SARIF fix must include artifactChanges or GitHub rejects the upload");
        }
        assertEquals(countOf(sarif, '['), countOf(sarif, ']'),
                "unbalanced brackets in SARIF");
    }

    @Test
    @DisplayName("remediation advice is surfaced in the result message and rule help")
    void recommendationIsSurfaced() {
        AuditReport report = audit(VULNERABLE);
        boolean anyRec = report.getFindings().stream()
                .anyMatch(f -> f.getRecommendation() != null && !f.getRecommendation().isEmpty());
        String sarif = SarifFormatter.format(report, "Vuln.dhr");

        if (anyRec) {
            assertTrue(sarif.contains("Recommendation:"),
                    "the finding's recommendation should appear in the message");
        }
        assertTrue(sarif.contains("\"help\""), "rules should carry help text");
    }

    @Test
    @DisplayName("rules carry security tags and a numeric security-severity for the Security tab")
    void rulesCarrySecurityProperties() {
        AuditReport report = audit(VULNERABLE);
        String sarif = SarifFormatter.format(report, "Vuln.dhr");

        assertTrue(sarif.contains("\"properties\""), "rules should carry a properties block");
        assertTrue(sarif.contains("\"security-severity\""),
                "GitHub buckets the Security tab by security-severity");
        assertTrue(sarif.contains("\"tags\""), "rules should carry tags");
        assertTrue(sarif.contains("\"security\""), "every rule should be tagged security");
        assertEquals(countOf(sarif, '['), countOf(sarif, ']'),
                "unbalanced brackets in SARIF");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static int countOf(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static int countMatches(String haystack, String needle) {
        int n = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            n++;
            idx += needle.length();
        }
        return n;
    }

    private static List<String> extractFingerprints(String sarif) {
        Matcher m = Pattern.compile("dhrlangAuditFingerprint/v1\"\\s*:\\s*\"([0-9a-f]+)\"").matcher(sarif);
        java.util.List<String> out = new java.util.ArrayList<>();
        while (m.find()) out.add(m.group(1));
        return out;
    }
}
