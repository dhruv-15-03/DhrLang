package dhrlang.testing;

import dhrlang.ast.*;

import java.util.*;

/**
 * Tracks test coverage for DhrLang contract tests.
 * Records which functions are entered/exited during test execution,
 * how many statements are covered, and produces per-contract and
 * per-function coverage percentages.
 *
 * <p>Usage:
 * <pre>
 * TestCoverageTracker tracker = new TestCoverageTracker();
 * tracker.recordFunctionEntry("Token", "transfer");
 * tracker.recordStatements(5);
 * tracker.recordFunctionExit("Token", "transfer");
 * double pct = tracker.getCoverage("Token");
 * </pre>
 */
public class TestCoverageTracker {

    // ── Coverage data ────────────────────────────────────

    /** Coverage info for a single function. */
    public static final class FunctionCoverage {
        private final String contractName;
        private final String functionName;
        private int totalStatements;
        private int coveredStatements;
        private int callCount;

        public FunctionCoverage(String contractName, String functionName) {
            this.contractName = contractName;
            this.functionName = functionName;
        }

        public String getContractName() { return contractName; }
        public String getFunctionName() { return functionName; }
        public int getTotalStatements() { return totalStatements; }
        public int getCoveredStatements() { return coveredStatements; }
        public int getCallCount() { return callCount; }

        public double getCoveragePercentage() {
            if (totalStatements == 0) return callCount > 0 ? 100.0 : 0.0;
            return (coveredStatements * 100.0) / totalStatements;
        }

        void recordEntry() { callCount++; }
        void recordStatements(int count) {
            totalStatements += count;
            coveredStatements += count;
        }
    }

    /** Coverage info for an entire contract. */
    public static final class ContractCoverage {
        private final String contractName;
        private final Map<String, FunctionCoverage> functions = new LinkedHashMap<>();
        private int totalFunctions;

        public ContractCoverage(String contractName) {
            this.contractName = contractName;
        }

        public String getContractName() { return contractName; }
        public int getTotalFunctions() { return totalFunctions; }
        public int getCoveredFunctions() { return functions.size(); }

        public Map<String, FunctionCoverage> getFunctions() {
            return Collections.unmodifiableMap(functions);
        }

        public double getFunctionCoveragePercentage() {
            if (totalFunctions == 0) return 0.0;
            return (functions.size() * 100.0) / totalFunctions;
        }

        public double getStatementCoveragePercentage() {
            int totalStmts = 0;
            int coveredStmts = 0;
            for (FunctionCoverage fc : functions.values()) {
                totalStmts += fc.totalStatements;
                coveredStmts += fc.coveredStatements;
            }
            if (totalStmts == 0) return functions.isEmpty() ? 0.0 : 100.0;
            return (coveredStmts * 100.0) / totalStmts;
        }
    }

    // ── Fields ───────────────────────────────────────────

    private final Map<String, ContractCoverage> contractCoverages = new LinkedHashMap<>();
    private String activeContract = null;
    private String activeFunction = null;

    // ── Recording ────────────────────────────────────────

    /**
     * Record entry into a function.
     */
    public void recordFunctionEntry(String contractName, String functionName) {
        activeContract = contractName;
        activeFunction = functionName;

        ContractCoverage cc = contractCoverages.computeIfAbsent(
                contractName, ContractCoverage::new);
        FunctionCoverage fc = cc.functions.computeIfAbsent(
                functionName, fn -> new FunctionCoverage(contractName, fn));
        fc.recordEntry();
    }

    /**
     * Record exit from a function.
     */
    public void recordFunctionExit(String contractName, String functionName) {
        if (Objects.equals(activeContract, contractName)
                && Objects.equals(activeFunction, functionName)) {
            activeContract = null;
            activeFunction = null;
        }
    }

    /**
     * Record that some statements were executed in the active function.
     */
    public void recordStatements(int count) {
        if (activeContract != null && activeFunction != null) {
            ContractCoverage cc = contractCoverages.get(activeContract);
            if (cc != null) {
                FunctionCoverage fc = cc.functions.get(activeFunction);
                if (fc != null) {
                    fc.recordStatements(count);
                }
            }
        }
    }

    /**
     * Register total function count for a contract (for percentage calculations).
     */
    public void registerContract(String contractName, int totalFunctions) {
        ContractCoverage cc = contractCoverages.computeIfAbsent(
                contractName, ContractCoverage::new);
        cc.totalFunctions = totalFunctions;
    }

    // ── Queries ──────────────────────────────────────────

    /**
     * Get function coverage percentage for a specific contract.
     */
    public double getCoverage(String contractName) {
        ContractCoverage cc = contractCoverages.get(contractName);
        if (cc == null) return 0.0;
        return cc.getFunctionCoveragePercentage();
    }

    /**
     * Get statement coverage percentage for a specific contract.
     */
    public double getStatementCoverage(String contractName) {
        ContractCoverage cc = contractCoverages.get(contractName);
        if (cc == null) return 0.0;
        return cc.getStatementCoveragePercentage();
    }

    /**
     * Get all contract coverage data.
     */
    public Map<String, ContractCoverage> getAllCoverage() {
        return Collections.unmodifiableMap(contractCoverages);
    }

    /**
     * Get the coverage data for a specific contract.
     */
    public ContractCoverage getContractCoverage(String contractName) {
        return contractCoverages.get(contractName);
    }

    /**
     * Check if a specific function was covered.
     */
    public boolean isFunctionCovered(String contractName, String functionName) {
        ContractCoverage cc = contractCoverages.get(contractName);
        if (cc == null) return false;
        return cc.functions.containsKey(functionName);
    }

    /**
     * Get the total number of function calls across all contracts.
     */
    public int getTotalCalls() {
        int total = 0;
        for (ContractCoverage cc : contractCoverages.values()) {
            for (FunctionCoverage fc : cc.functions.values()) {
                total += fc.callCount;
            }
        }
        return total;
    }

    /**
     * Get all uncovered functions for a contract.
     */
    public List<String> getUncoveredFunctions(String contractName,
                                               List<String> allFunctions) {
        ContractCoverage cc = contractCoverages.get(contractName);
        Set<String> covered = cc != null ? cc.functions.keySet() : Set.of();
        List<String> uncovered = new ArrayList<>();
        for (String fn : allFunctions) {
            if (!covered.contains(fn)) {
                uncovered.add(fn);
            }
        }
        return uncovered;
    }

    // ── Reset ────────────────────────────────────────────

    public void reset() {
        contractCoverages.clear();
        activeContract = null;
        activeFunction = null;
    }

    // ── Report ───────────────────────────────────────────

    /**
     * Format a coverage report.
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Coverage Report ═══\n\n");

        for (ContractCoverage cc : contractCoverages.values()) {
            sb.append(String.format("Contract: %s — %.1f%% function coverage, %.1f%% statement coverage\n",
                    cc.getContractName(),
                    cc.getFunctionCoveragePercentage(),
                    cc.getStatementCoveragePercentage()));

            for (FunctionCoverage fc : cc.functions.values()) {
                sb.append(String.format("  %s() — %d calls, %d/%d statements (%.1f%%)\n",
                        fc.getFunctionName(), fc.getCallCount(),
                        fc.getCoveredStatements(), fc.getTotalStatements(),
                        fc.getCoveragePercentage()));
            }
        }

        sb.append('\n');
        sb.append("Total function calls: ").append(getTotalCalls()).append('\n');
        return sb.toString();
    }
}
