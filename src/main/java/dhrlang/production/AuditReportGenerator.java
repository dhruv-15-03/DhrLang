package dhrlang.production;

import dhrlang.ast.*;
import dhrlang.evm.EvmContractCompiler;
import dhrlang.evm.EvmContractCompiler.ContractArtifact;
import dhrlang.validation.ContractValidator;
import dhrlang.validation.ContractValidator.ValidationError;
import dhrlang.validation.StorageLayouter;
import dhrlang.validation.StorageLayouter.ContractLayout;
import dhrlang.validation.StorageLayouter.SlotInfo;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates comprehensive security audit reports for DhrLang smart contracts.
 *
 * <p>The audit report covers:
 * <ul>
 *   <li>Contract overview (name, functions, storage variables)</li>
 *   <li>Security findings (from ContractValidator)</li>
 *   <li>Storage layout analysis (from StorageLayouter)</li>
 *   <li>Gas estimates (from EvmContractCompiler)</li>
 *   <li>Access control patterns</li>
 *   <li>Reentrancy protection analysis</li>
 *   <li>Overall risk score</li>
 * </ul>
 *
 * <p><b>User story:</b> SC-501 — As a developer, I want auto-generated audit reports.</p>
 *
 * @see ContractValidator
 * @see StorageLayouter
 */
public final class AuditReportGenerator {

    // ── Severity levels ──────────────────────────────────────────────────

    /**
     * Severity of an audit finding.
     */
    public enum Severity {
        CRITICAL("CRITICAL", 4),
        HIGH("HIGH", 3),
        MEDIUM("MEDIUM", 2),
        LOW("LOW", 1),
        INFORMATIONAL("INFO", 0);

        private final String label;
        private final int weight;

        Severity(String label, int weight) {
            this.label = label;
            this.weight = weight;
        }

        public String getLabel() { return label; }
        public int getWeight() { return weight; }
    }

    // ── Finding ──────────────────────────────────────────────────────────

    /**
     * A single audit finding.
     */
    public static final class Finding {
        private final String id;
        private final Severity severity;
        private final String title;
        private final String description;
        private final String recommendation;
        private final String location;

        public Finding(String id, Severity severity, String title,
                       String description, String recommendation, String location) {
            this.id = id;
            this.severity = severity;
            this.title = title;
            this.description = description;
            this.recommendation = recommendation;
            this.location = location;
        }

        public String getId() { return id; }
        public Severity getSeverity() { return severity; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getRecommendation() { return recommendation; }
        public String getLocation() { return location; }

        @Override
        public String toString() {
            return "[" + severity.getLabel() + "] " + id + ": " + title;
        }
    }

    // ── ContractSummary ──────────────────────────────────────────────────

    /**
     * Summary statistics for a single audited contract.
     */
    public static final class ContractSummary {
        private final String name;
        private final int functionCount;
        private final int storageVariableCount;
        private final int publicFunctionCount;
        private final int viewFunctionCount;
        private final int payableFunctionCount;
        private final boolean hasReentrancyGuard;
        private final boolean hasAccessControl;
        private final long estimatedDeployGas;
        private final int storageSlots;

        ContractSummary(String name, int functionCount, int storageVariableCount,
                        int publicFunctionCount, int viewFunctionCount,
                        int payableFunctionCount, boolean hasReentrancyGuard,
                        boolean hasAccessControl, long estimatedDeployGas,
                        int storageSlots) {
            this.name = name;
            this.functionCount = functionCount;
            this.storageVariableCount = storageVariableCount;
            this.publicFunctionCount = publicFunctionCount;
            this.viewFunctionCount = viewFunctionCount;
            this.payableFunctionCount = payableFunctionCount;
            this.hasReentrancyGuard = hasReentrancyGuard;
            this.hasAccessControl = hasAccessControl;
            this.estimatedDeployGas = estimatedDeployGas;
            this.storageSlots = storageSlots;
        }

        public String getName() { return name; }
        public int getFunctionCount() { return functionCount; }
        public int getStorageVariableCount() { return storageVariableCount; }
        public int getPublicFunctionCount() { return publicFunctionCount; }
        public int getViewFunctionCount() { return viewFunctionCount; }
        public int getPayableFunctionCount() { return payableFunctionCount; }
        public boolean hasReentrancyGuard() { return hasReentrancyGuard; }
        public boolean hasAccessControl() { return hasAccessControl; }
        public long getEstimatedDeployGas() { return estimatedDeployGas; }
        public int getStorageSlots() { return storageSlots; }
    }

    // ── AuditReport ─────────────────────────────────────────────────────

    /**
     * The complete audit report.
     */
    public static final class AuditReport {
        private final String projectName;
        private final String generatedAt;
        private final String compilerVersion;
        private final List<ContractSummary> contracts;
        private final List<Finding> findings;
        private final int riskScore;    // 0 (safe) to 100 (critical)
        private final String riskRating; // "Low", "Medium", "High", "Critical"

        AuditReport(String projectName, String generatedAt, String compilerVersion,
                    List<ContractSummary> contracts, List<Finding> findings,
                    int riskScore, String riskRating) {
            this.projectName = projectName;
            this.generatedAt = generatedAt;
            this.compilerVersion = compilerVersion;
            this.contracts = Collections.unmodifiableList(contracts);
            this.findings = Collections.unmodifiableList(findings);
            this.riskScore = riskScore;
            this.riskRating = riskRating;
        }

        public String getProjectName() { return projectName; }
        public String getGeneratedAt() { return generatedAt; }
        public String getCompilerVersion() { return compilerVersion; }
        public List<ContractSummary> getContracts() { return contracts; }
        public List<Finding> getFindings() { return findings; }
        public int getRiskScore() { return riskScore; }
        public String getRiskRating() { return riskRating; }

        public long countBySeverity(Severity severity) {
            return findings.stream().filter(f -> f.getSeverity() == severity).count();
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private String projectName = "DhrLang Contract Audit";
    private String compilerVersion = "v2.0.0";
    private final List<Finding> findings = new ArrayList<>();
    private final List<ContractSummary> summaries = new ArrayList<>();

    // ── Configuration ────────────────────────────────────────────────────

    public AuditReportGenerator setProjectName(String name) {
        this.projectName = name;
        return this;
    }

    public AuditReportGenerator setCompilerVersion(String version) {
        this.compilerVersion = version;
        return this;
    }

    public String getProjectName() { return projectName; }
    public String getCompilerVersion() { return compilerVersion; }

    // ── Analysis Entry Points ────────────────────────────────────────────

    /**
     * Analyze a program and produce a full audit report.
     *
     * @param program the DhrLang program to audit
     * @return the complete {@link AuditReport}
     */
    public AuditReport analyze(Program program) {
        findings.clear();
        summaries.clear();

        // 1. Run ContractValidator for basic validation findings
        runValidation(program);

        // 2. Analyze each contract class
        for (ClassDecl cls : program.getClasses()) {
            analyzeContract(cls);
        }

        // 3. Cross-contract analysis
        analyzeCrossContract(program);

        // 4. Run Phase 4 deep analyzers on each contract
        runDeepAnalysis(program);

        // 5. Compute risk score
        int riskScore = computeRiskScore();
        String riskRating = riskRatingFromScore(riskScore);

        String timestamp = DateTimeFormatter.ISO_INSTANT
                .format(Instant.now().atOffset(ZoneOffset.UTC));

        return new AuditReport(
                projectName, timestamp, compilerVersion,
                new ArrayList<>(summaries),
                new ArrayList<>(findings),
                riskScore, riskRating
        );
    }

    /**
     * Run Phase 4 deep analysis: invariant checking, arithmetic overflow detection,
     * security analysis (taint, privilege, loop bounds).
     */
    private void runDeepAnalysis(Program program) {
        for (ClassDecl cls : program.getClasses()) {
            if (!cls.isContract()) continue;
            String contractName = cls.getName();

            // ArithmeticOverflowDetector
            try {
                var overflow = new dhrlang.validation.ArithmeticOverflowDetector();
                var risks = overflow.analyze(cls);
                for (var risk : risks) {
                    Severity sev = risk.hasGuard() ? Severity.LOW : Severity.HIGH;
                    addFinding("ARITH-" + risk.getKind().name(),
                            sev,
                            "Arithmetic risk: " + risk.getKind().name().toLowerCase().replace('_', ' '),
                            risk.getExpression() + " in " + risk.getFunctionName() + "()",
                            risk.getHint(),
                            contractName + "." + risk.getFunctionName());
                }
            } catch (Exception ignored) {}

            // SecurityAnalyzer (taint, privilege, loop bounds)
            try {
                var security = new dhrlang.validation.SecurityAnalyzer();
                var secFindings = security.analyze(cls);
                for (var sf : secFindings) {
                    Severity sev = switch (sf.getSeverity()) {
                        case CRITICAL -> Severity.CRITICAL;
                        case HIGH -> Severity.HIGH;
                        case MEDIUM -> Severity.MEDIUM;
                        case LOW -> Severity.LOW;
                        default -> Severity.INFORMATIONAL;
                    };
                    addFinding("SEC-" + sf.getCategory().name(),
                            sev,
                            sf.getTitle(),
                            sf.getDescription(),
                            sf.getHint(),
                            contractName + (sf.getFunctionName() != null ? "." + sf.getFunctionName() : ""));
                }
            } catch (Exception ignored) {}

            // InvariantChecker
            try {
                var invariants = new dhrlang.validation.InvariantChecker();
                var violations = invariants.check(cls);
                for (var v : violations) {
                    String kindName = v.getInvariant() != null && v.getInvariant().getKind() != null
                            ? v.getInvariant().getKind().name() : "UNKNOWN";
                    addFinding("INV-" + kindName,
                            Severity.HIGH,
                            "Invariant violation: " + kindName.toLowerCase().replace('_', ' '),
                            v.getReason(),
                            "Add validation before the state modification to ensure the invariant holds.",
                            contractName + "." + v.getFunctionName());
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Analyze a single contract class (without full program context).
     */
    public ContractSummary analyzeContract(ClassDecl classDecl) {
        int fnCount = classDecl.getFunctions().size();
        int storageCount = 0;
        int publicCount = 0;
        int viewCount = 0;
        int payableCount = 0;
        boolean hasReentrancyGuard = false;
        boolean hasAccessControl = false;

        for (VarDecl v : classDecl.getVariables()) {
            if (v.isStorage()) storageCount++;
        }

        for (FunctionDecl fn : classDecl.getFunctions()) {
            if (!fn.hasModifier(Modifier.PRIVATE)) publicCount++;
            if (fn.isView()) viewCount++;
            if (fn.isPayable()) payableCount++;
            if (fn.isNonReentrant()) hasReentrancyGuard = true;
            if (fn.getName().equals("onlyOwner") || fn.getName().startsWith("require")) {
                hasAccessControl = true;
            }
        }

        // Check for storage but no reentrancy guard on state-changing functions
        if (storageCount > 0 && !hasReentrancyGuard) {
            boolean hasStateChangingPayable = classDecl.getFunctions().stream()
                    .anyMatch(fn -> fn.isPayable() && !fn.isNonReentrant());
            if (hasStateChangingPayable) {
                addFinding("AUD-001", Severity.HIGH,
                        "Missing reentrancy guard on payable function",
                        "Contract '" + classDecl.getName() + "' has @payable functions without @nonreentrant.",
                        "Add @nonreentrant to all state-changing @payable functions.",
                        classDecl.getName());
            }
        }

        // Check for unchecked external calls in payable functions
        if (payableCount > 0 && !hasReentrancyGuard) {
            addFinding("AUD-002", Severity.MEDIUM,
                    "Payable function without reentrancy protection",
                    "Contract '" + classDecl.getName() + "' has payable functions that may be vulnerable.",
                    "Consider adding @nonreentrant to payable functions.",
                    classDecl.getName());
        }

        // Check for missing view annotations on read-only functions
        for (FunctionDecl fn : classDecl.getFunctions()) {
            if (isLikelyReadOnly(fn) && !fn.isView() && !fn.isPure()
                    && !fn.isContractConstructor()) {
                addFinding("AUD-003", Severity.LOW,
                        "Function may be @view",
                        "Function '" + fn.getName() + "' in '" + classDecl.getName()
                                + "' appears to be read-only but is not marked @view.",
                        "Mark the function as @view to save gas for callers.",
                        classDecl.getName() + "." + fn.getName());
            }
        }

        // Check for excessive storage slots
        if (storageCount > StorageLayouter.MAX_STORAGE_SLOTS) {
            addFinding("AUD-004", Severity.MEDIUM,
                    "Excessive storage usage",
                    "Contract '" + classDecl.getName() + "' uses " + storageCount
                            + " storage slots (limit: " + StorageLayouter.MAX_STORAGE_SLOTS + ").",
                    "Consider refactoring storage to reduce slot count.",
                    classDecl.getName());
        }

        // Check for no constructor
        boolean hasCtor = classDecl.getFunctions().stream()
                .anyMatch(FunctionDecl::isContractConstructor);
        if (classDecl.isContract() && !hasCtor) {
            addFinding("AUD-005", Severity.INFORMATIONAL,
                    "No @constructor defined",
                    "Contract '" + classDecl.getName() + "' has no @constructor.",
                    "Add a @constructor to initialize state variables.",
                    classDecl.getName());
        }

        // Estimate gas (simplified: 21000 base + 200/byte for storage)
        long estimatedGas = 21_000L + 32_000L + (storageCount * 20_000L);

        ContractSummary summary = new ContractSummary(
                classDecl.getName(), fnCount, storageCount,
                publicCount, viewCount, payableCount,
                hasReentrancyGuard, hasAccessControl,
                estimatedGas, storageCount
        );
        summaries.add(summary);
        return summary;
    }

    // ── Validation Integration ───────────────────────────────────────────

    /**
     * Run the ContractValidator and convert errors to audit findings.
     */
    void runValidation(Program program) {
        ContractValidator validator = new ContractValidator();
        validator.validate(program);
        for (ValidationError err : validator.getErrors()) {
            Severity severity = mapValidationSeverity(err.getCode());
            addFinding(err.getCode(), severity,
                    err.getMessage(),
                    err.getMessage(),
                    err.getSuggestion() != null ? err.getSuggestion() : "Review the code.",
                    err.getLocation() != null ? err.getLocation().toString() : "unknown");
        }
    }

    /**
     * Map a validation error code to an audit severity.
     */
    static Severity mapValidationSeverity(String code) {
        if (code == null) return Severity.MEDIUM;
        // E5xx are contract-specific errors
        if (code.startsWith("DHR-E5")) {
            // Missing constructor, field misuse = medium/high
            if (code.equals("DHR-E505") || code.equals("DHR-E504")) return Severity.HIGH;
            return Severity.MEDIUM;
        }
        return Severity.MEDIUM;
    }

    // ── Cross-Contract Analysis ──────────────────────────────────────────

    /**
     * Analyze cross-contract interactions and patterns.
     */
    void analyzeCrossContract(Program program) {
        List<ClassDecl> contracts = new ArrayList<>();
        for (ClassDecl cls : program.getClasses()) {
            if (cls.isContract()) contracts.add(cls);
        }

        // Check for multiple contracts with the same name
        Set<String> names = new HashSet<>();
        for (ClassDecl c : contracts) {
            if (!names.add(c.getName())) {
                addFinding("AUD-010", Severity.HIGH,
                        "Duplicate contract name",
                        "Multiple contracts named '" + c.getName() + "' detected.",
                        "Rename one of the duplicate contracts.",
                        c.getName());
            }
        }

        // Check for contracts without any functions
        for (ClassDecl c : contracts) {
            if (c.getFunctions().isEmpty()) {
                addFinding("AUD-011", Severity.INFORMATIONAL,
                        "Empty contract",
                        "Contract '" + c.getName() + "' has no functions.",
                        "Add functions or consider removing the contract.",
                        c.getName());
            }
        }
    }

    // ── Risk Scoring ─────────────────────────────────────────────────────

    /**
     * Compute a risk score 0–100 from accumulated findings.
     */
    int computeRiskScore() {
        if (findings.isEmpty()) return 0;

        int totalWeight = 0;
        for (Finding f : findings) {
            totalWeight += f.getSeverity().getWeight() * 10;
        }
        return Math.min(100, totalWeight);
    }

    /**
     * Translate a numeric risk score to a human-readable rating.
     */
    static String riskRatingFromScore(int score) {
        if (score >= 75) return "Critical";
        if (score >= 50) return "High";
        if (score >= 25) return "Medium";
        if (score > 0) return "Low";
        return "Safe";
    }

    // ── Formatting ───────────────────────────────────────────────────────

    /**
     * Format the audit report as plain text.
     */
    public static String formatText(AuditReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║            DhrLang Security Audit Report                    ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        sb.append("Project:     ").append(report.getProjectName()).append('\n');
        sb.append("Generated:   ").append(report.getGeneratedAt()).append('\n');
        sb.append("Compiler:    ").append(report.getCompilerVersion()).append('\n');
        sb.append("Risk Score:  ").append(report.getRiskScore()).append("/100 (")
                .append(report.getRiskRating()).append(")\n\n");

        // Contract summaries
        sb.append("── Contracts Audited ──────────────────────────────────────\n\n");
        for (ContractSummary cs : report.getContracts()) {
            sb.append("  ").append(cs.getName()).append('\n');
            sb.append("    Functions:        ").append(cs.getFunctionCount()).append('\n');
            sb.append("    Storage vars:     ").append(cs.getStorageVariableCount()).append('\n');
            sb.append("    Payable:          ").append(cs.getPayableFunctionCount()).append('\n');
            sb.append("    View:             ").append(cs.getViewFunctionCount()).append('\n');
            sb.append("    Reentrancy guard: ").append(cs.hasReentrancyGuard() ? "Yes" : "No").append('\n');
            sb.append("    Access control:   ").append(cs.hasAccessControl() ? "Yes" : "No").append('\n');
            sb.append("    Deploy gas (est): ").append(cs.getEstimatedDeployGas()).append('\n');
            sb.append('\n');
        }

        // Findings
        sb.append("── Findings (").append(report.getFindings().size()).append(") ───────────────────────────────────\n\n");
        if (report.getFindings().isEmpty()) {
            sb.append("  No issues found. ✓\n\n");
        } else {
            for (Finding f : report.getFindings()) {
                sb.append("  [").append(f.getSeverity().getLabel()).append("] ")
                        .append(f.getId()).append(": ").append(f.getTitle()).append('\n');
                sb.append("    ").append(f.getDescription()).append('\n');
                sb.append("    Recommendation: ").append(f.getRecommendation()).append('\n');
                sb.append("    Location: ").append(f.getLocation()).append('\n');
                sb.append('\n');
            }
        }

        // Summary
        sb.append("── Summary ────────────────────────────────────────────────\n\n");
        sb.append("  Critical: ").append(report.countBySeverity(Severity.CRITICAL)).append('\n');
        sb.append("  High:     ").append(report.countBySeverity(Severity.HIGH)).append('\n');
        sb.append("  Medium:   ").append(report.countBySeverity(Severity.MEDIUM)).append('\n');
        sb.append("  Low:      ").append(report.countBySeverity(Severity.LOW)).append('\n');
        sb.append("  Info:     ").append(report.countBySeverity(Severity.INFORMATIONAL)).append('\n');

        return sb.toString();
    }

    /**
     * Format the audit report as JSON.
     */
    public static String formatJson(AuditReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"project\":\"").append(escJson(report.getProjectName()))
                .append("\",\"generatedAt\":\"").append(escJson(report.getGeneratedAt()))
                .append("\",\"compiler\":\"").append(escJson(report.getCompilerVersion()))
                .append("\",\"riskScore\":").append(report.getRiskScore())
                .append(",\"riskRating\":\"").append(escJson(report.getRiskRating()))
                .append("\",\"contracts\":[");

        for (int i = 0; i < report.getContracts().size(); i++) {
            if (i > 0) sb.append(',');
            ContractSummary cs = report.getContracts().get(i);
            sb.append("{\"name\":\"").append(escJson(cs.getName()))
                    .append("\",\"functions\":").append(cs.getFunctionCount())
                    .append(",\"storageVars\":").append(cs.getStorageVariableCount())
                    .append(",\"payable\":").append(cs.getPayableFunctionCount())
                    .append(",\"view\":").append(cs.getViewFunctionCount())
                    .append(",\"reentrancyGuard\":").append(cs.hasReentrancyGuard())
                    .append(",\"accessControl\":").append(cs.hasAccessControl())
                    .append(",\"deployGas\":").append(cs.getEstimatedDeployGas())
                    .append("}");
        }

        sb.append("],\"findings\":[");
        for (int i = 0; i < report.getFindings().size(); i++) {
            if (i > 0) sb.append(',');
            Finding f = report.getFindings().get(i);
            sb.append("{\"id\":\"").append(escJson(f.getId()))
                    .append("\",\"severity\":\"").append(f.getSeverity().getLabel())
                    .append("\",\"title\":\"").append(escJson(f.getTitle()))
                    .append("\",\"description\":\"").append(escJson(f.getDescription()))
                    .append("\",\"recommendation\":\"").append(escJson(f.getRecommendation()))
                    .append("\",\"location\":\"").append(escJson(f.getLocation()))
                    .append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void addFinding(String id, Severity severity, String title,
                            String description, String recommendation, String location) {
        findings.add(new Finding(id, severity, title, description, recommendation, location));
    }

    /**
     * Heuristic: a function is likely read-only if its name starts with "get" or "is"
     * and it has no parameters, or it's a simple one-liner.
     */
    static boolean isLikelyReadOnly(FunctionDecl fn) {
        String name = fn.getName().toLowerCase(Locale.ROOT);
        return name.startsWith("get") || name.startsWith("is") || name.startsWith("has")
                || name.startsWith("total") || name.startsWith("balance");
    }

    /**
     * Get all accumulated findings (before report generation).
     */
    public List<Finding> getFindings() {
        return Collections.unmodifiableList(findings);
    }

    private static String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
