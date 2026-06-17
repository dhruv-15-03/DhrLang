package dhrlang.production;

import dhrlang.production.AuditReportGenerator.AuditReport;
import dhrlang.production.AuditReportGenerator.Finding;
import dhrlang.production.AuditReportGenerator.Severity;

import java.util.List;

/**
 * SARIF (Static Analysis Results Interchange Format) output for DhrLang audits.
 *
 * <p>Produces <a href="https://sarifweb.azurewebsites.net/">SARIF v2.1.0</a>
 * compatible JSON, enabling integration with:</p>
 * <ul>
 *   <li>GitHub Code Scanning / Security tab</li>
 *   <li>Azure DevOps</li>
 *   <li>VS Code SARIF Viewer extension</li>
 *   <li>any CI/CD tool supporting SARIF uploads</li>
 * </ul>
 */
public final class SarifFormatter {

    private SarifFormatter() {}

    /**
     * Format an audit report as SARIF v2.1.0 JSON.
     *
     * @param report the audit report
     * @param sourceFile the source file path (for result locations)
     * @return SARIF JSON string
     */
    public static String format(AuditReport report, String sourceFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"$schema\": \"https://raw.githubusercontent.com/oasis-tcs/sarif-spec/main/sarif-2.1/schema/sarif-schema-2.1.0.json\",\n");
        sb.append("  \"version\": \"2.1.0\",\n");
        sb.append("  \"runs\": [{\n");

        // Tool
        sb.append("    \"tool\": {\n");
        sb.append("      \"driver\": {\n");
        sb.append("        \"name\": \"DhrLang Security Analyzer\",\n");
        sb.append("        \"version\": \"").append(escape(report.getCompilerVersion())).append("\",\n");
        sb.append("        \"informationUri\": \"https://github.com/dhruv-15-03/DhrLang\",\n");
        sb.append("        \"rules\": ").append(formatRules(report.getFindings())).append("\n");
        sb.append("      }\n");
        sb.append("    },\n");

        // Results
        sb.append("    \"results\": [\n");
        List<Finding> findings = report.getFindings();
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append("      {\n");
            sb.append("        \"ruleId\": \"").append(escape(f.getId())).append("\",\n");
            sb.append("        \"level\": \"").append(sarifLevel(f.getSeverity())).append("\",\n");
            sb.append("        \"message\": {\n");
            String rec = f.getRecommendation();
            String msg = f.getTitle() + ": " + f.getDescription();
            if (rec != null && !rec.isEmpty()) msg += " Recommendation: " + rec;
            sb.append("          \"text\": \"").append(escape(msg)).append("\"\n");
            sb.append("        },\n");
            sb.append("        \"locations\": [{\n");
            sb.append("          \"physicalLocation\": {\n");
            sb.append("            \"artifactLocation\": {\n");
            sb.append("              \"uri\": \"").append(escape((sourceFile != null ? sourceFile : "contract.dhr").replace('\\', '/'))).append("\"\n");
            if (f.getLine() > 0) {
                sb.append("            },\n");
                sb.append("            \"region\": {\n");
                sb.append("              \"startLine\": ").append(f.getLine()).append("\n");
                sb.append("            }\n");
            } else {
                sb.append("            }\n");
            }
            sb.append("          },\n");
            sb.append("          \"logicalLocations\": [{\n");
            sb.append("            \"fullyQualifiedName\": \"").append(escape(f.getLocation())).append("\"\n");
            sb.append("          }]\n");
            sb.append("        }],\n");
            sb.append("        \"partialFingerprints\": {\n");
            sb.append("          \"dhrlangAuditFingerprint/v1\": \"").append(fingerprint(f)).append("\"\n");
            sb.append("        }\n");
            sb.append("      }");
            if (i < findings.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ],\n");

        // Invocations
        sb.append("    \"invocations\": [{\n");
        sb.append("      \"executionSuccessful\": true,\n");
        sb.append("      \"toolExecutionNotifications\": []\n");
        sb.append("    }],\n");

        // Properties (custom DhrLang metadata)
        sb.append("    \"properties\": {\n");
        sb.append("      \"riskScore\": ").append(report.getRiskScore()).append(",\n");
        sb.append("      \"riskRating\": \"").append(escape(report.getRiskRating())).append("\",\n");
        sb.append("      \"totalFindings\": ").append(findings.size()).append(",\n");
        sb.append("      \"criticalCount\": ").append(countBySeverity(findings, Severity.CRITICAL)).append(",\n");
        sb.append("      \"highCount\": ").append(countBySeverity(findings, Severity.HIGH)).append(",\n");
        sb.append("      \"mediumCount\": ").append(countBySeverity(findings, Severity.MEDIUM)).append(",\n");
        sb.append("      \"lowCount\": ").append(countBySeverity(findings, Severity.LOW)).append("\n");
        sb.append("    }\n");

        sb.append("  }]\n");
        sb.append("}");
        return sb.toString();
    }

    // ── Rules ────────────────────────────────────────────────────────────

    private static String formatRules(List<Finding> findings) {
        // Deduplicate rule IDs
        java.util.LinkedHashMap<String, Finding> ruleMap = new java.util.LinkedHashMap<>();
        for (Finding f : findings) {
            ruleMap.putIfAbsent(f.getId(), f);
        }

        StringBuilder sb = new StringBuilder("[\n");
        int i = 0;
        for (var entry : ruleMap.entrySet()) {
            Finding f = entry.getValue();
            sb.append("          {\n");
            sb.append("            \"id\": \"").append(escape(f.getId())).append("\",\n");
            sb.append("            \"name\": \"").append(escape(f.getTitle())).append("\",\n");
            sb.append("            \"shortDescription\": {\n");
            sb.append("              \"text\": \"").append(escape(f.getTitle())).append("\"\n");
            sb.append("            },\n");
            sb.append("            \"fullDescription\": {\n");
            sb.append("              \"text\": \"").append(escape(f.getDescription())).append("\"\n");
            sb.append("            },\n");
            String ruleRec = f.getRecommendation();
            String help = (f.getDescription() == null ? "" : f.getDescription());
            if (ruleRec != null && !ruleRec.isEmpty()) help += " Recommendation: " + ruleRec;
            sb.append("            \"help\": {\n");
            sb.append("              \"text\": \"").append(escape(help)).append("\"\n");
            sb.append("            },\n");
            sb.append("            \"defaultConfiguration\": {\n");
            sb.append("              \"level\": \"").append(sarifLevel(f.getSeverity())).append("\"\n");
            sb.append("            },\n");
            sb.append("            \"helpUri\": \"https://github.com/dhruv-15-03/DhrLang/blob/main/SECURITY_RULES.md#").append(escape(f.getId().toLowerCase(java.util.Locale.ROOT))).append("\"\n");
            sb.append("          }");
            if (++i < ruleMap.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("        ]");
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static String sarifLevel(Severity severity) {
        return switch (severity) {
            case CRITICAL, HIGH -> "error";
            case MEDIUM -> "warning";
            case LOW, INFORMATIONAL -> "note";
        };
    }

    private static long countBySeverity(List<Finding> findings, Severity severity) {
        return findings.stream().filter(f -> f.getSeverity() == severity).count();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Stable per-result fingerprint so GitHub Code Scanning can track an alert
     * across runs (and dedupe identical findings) even as line numbers shift.
     * Derived from the rule id, logical location, and title — never the line.
     */
    private static String fingerprint(Finding f) {
        String basis = f.getId() + "|"
                + (f.getLocation() == null ? "" : f.getLocation()) + "|"
                + (f.getTitle() == null ? "" : f.getTitle());
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(basis.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(basis.hashCode());
        }
    }
}
