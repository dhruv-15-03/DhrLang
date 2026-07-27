package dhrlang.testing;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Formats and produces comprehensive test reports combining results from
 * the test runner, invariant checker, fuzzer, and coverage tracker into
 * a unified human-readable output.
 *
 * <p>Supports multiple output formats:
 * <ul>
 *   <li>{@link OutputFormat#TEXT} — plain text for terminal output</li>
 *   <li>{@link OutputFormat#COMPACT} — single-line summary</li>
 *   <li>{@link OutputFormat#JSON} — machine-readable JSON</li>
 * </ul>
 */
public class TestReporter {

    // ── Format options ───────────────────────────────────

    public enum OutputFormat { TEXT, COMPACT, JSON }

    // ── Fields ───────────────────────────────────────────

    private final List<ContractTestRunner.TestResult> testResults;
    private final List<InvariantChecker.InvariantViolation> invariantViolations;
    private final List<ContractFuzzer.FuzzResult> fuzzResults;
    private final TestCoverageTracker coverageTracker;
    private final long totalDurationMs;

    // ── Builder ──────────────────────────────────────────

    public static class Builder {
        private List<ContractTestRunner.TestResult> testResults = List.of();
        private List<InvariantChecker.InvariantViolation> invariantViolations = List.of();
        private List<ContractFuzzer.FuzzResult> fuzzResults = List.of();
        private TestCoverageTracker coverageTracker = null;
        private long totalDurationMs = 0;

        public Builder testResults(List<ContractTestRunner.TestResult> results) {
            this.testResults = results != null ? results : List.of();
            return this;
        }

        public Builder invariantViolations(List<InvariantChecker.InvariantViolation> violations) {
            this.invariantViolations = violations != null ? violations : List.of();
            return this;
        }

        public Builder fuzzResults(List<ContractFuzzer.FuzzResult> results) {
            this.fuzzResults = results != null ? results : List.of();
            return this;
        }

        public Builder coverageTracker(TestCoverageTracker tracker) {
            this.coverageTracker = tracker;
            return this;
        }

        public Builder totalDurationMs(long ms) {
            this.totalDurationMs = ms;
            return this;
        }

        public TestReporter build() {
            return new TestReporter(this);
        }
    }

    private TestReporter(Builder builder) {
        this.testResults = builder.testResults;
        this.invariantViolations = builder.invariantViolations;
        this.fuzzResults = builder.fuzzResults;
        this.coverageTracker = builder.coverageTracker;
        this.totalDurationMs = builder.totalDurationMs;
    }

    // ── Format methods ───────────────────────────────────

    /**
     * Generate a report in the given format.
     */
    public String format(OutputFormat fmt) {
        return switch (fmt) {
            case TEXT    -> formatText();
            case COMPACT -> formatCompact();
            case JSON    -> formatJson();
        };
    }

    // ── Text format ──────────────────────────────────────

    public String formatText() {
        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        sb.append("╔══════════════════════════════════════════════════╗\n");
        sb.append("║     DhrLang Contract Test Report                ║\n");
        sb.append("║     ").append(timestamp).append("                      ║\n");
        sb.append("╚══════════════════════════════════════════════════╝\n\n");

        // --- Test Results ---
        sb.append("── Test Results ──────────────────────────────────\n\n");
        if (testResults.isEmpty()) {
            sb.append("  No tests executed.\n");
        } else {
            long passed = testResults.stream()
                    .filter(r -> r.getStatus() == ContractTestRunner.TestStatus.PASSED).count();
            long failed = testResults.stream()
                    .filter(r -> r.getStatus() == ContractTestRunner.TestStatus.FAILED).count();
            long errors = testResults.stream()
                    .filter(r -> r.getStatus() == ContractTestRunner.TestStatus.ERROR).count();

            for (ContractTestRunner.TestResult r : testResults) {
                sb.append("  ").append(r).append('\n');
            }
            sb.append('\n');
            sb.append(String.format("  Total: %d | Passed: %d | Failed: %d | Errors: %d\n",
                    testResults.size(), passed, failed, errors));
        }
        sb.append('\n');

        // --- Invariant Analysis ---
        sb.append("── Invariant Analysis ────────────────────────────\n\n");
        if (invariantViolations.isEmpty()) {
            sb.append("  All invariants hold. ✓\n");
        } else {
            for (InvariantChecker.InvariantViolation v : invariantViolations) {
                sb.append("  ✗ ").append(v).append('\n');
            }
            sb.append(String.format("\n  Violations found: %d\n", invariantViolations.size()));
        }
        sb.append('\n');

        // --- Fuzzer Results ---
        sb.append("── Fuzzer Results ────────────────────────────────\n\n");
        if (fuzzResults.isEmpty()) {
            sb.append("  No fuzzing performed.\n");
        } else {
            long fuzzFailures = fuzzResults.stream()
                    .filter(r -> r.getOutcome() != ContractFuzzer.FuzzOutcome.OK).count();
            sb.append(String.format("  Runs: %d | Failures: %d\n",
                    fuzzResults.size(), fuzzFailures));

            if (fuzzFailures > 0) {
                sb.append("  Failing cases:\n");
                fuzzResults.stream()
                        .filter(r -> r.getOutcome() != ContractFuzzer.FuzzOutcome.OK)
                        .limit(10)
                        .forEach(r -> sb.append("    ✗ ").append(r).append('\n'));
            }
        }
        sb.append('\n');

        // --- Coverage ---
        sb.append("── Coverage ──────────────────────────────────────\n\n");
        if (coverageTracker == null || coverageTracker.getAllCoverage().isEmpty()) {
            sb.append("  No coverage data.\n");
        } else {
            for (var entry : coverageTracker.getAllCoverage().entrySet()) {
                TestCoverageTracker.ContractCoverage cc = entry.getValue();
                sb.append(String.format("  %s: %.1f%% functions, %.1f%% statements\n",
                        cc.getContractName(),
                        cc.getFunctionCoveragePercentage(),
                        cc.getStatementCoveragePercentage()));
            }
        }
        sb.append('\n');

        // --- Summary ---
        sb.append("── Summary ───────────────────────────────────────\n\n");
        boolean allGood = testResults.stream()
                .allMatch(r -> r.getStatus() == ContractTestRunner.TestStatus.PASSED)
                && invariantViolations.isEmpty();
        sb.append(String.format("  Duration: %dms\n", totalDurationMs));
        sb.append(allGood ? "  Result: ALL CHECKS PASSED ✓\n" : "  Result: ISSUES FOUND ✗\n");

        return sb.toString();
    }

    // ── Compact format ───────────────────────────────────

    public String formatCompact() {
        long passed = testResults.stream()
                .filter(r -> r.getStatus() == ContractTestRunner.TestStatus.PASSED).count();
        long failed = testResults.stream()
                .filter(r -> r.getStatus() != ContractTestRunner.TestStatus.PASSED).count();

        return String.format("Tests: %d/%d passed | Invariants: %s | Fuzz: %d runs | %dms",
                passed, testResults.size(),
                invariantViolations.isEmpty() ? "OK" : invariantViolations.size() + " violations",
                fuzzResults.size(),
                totalDurationMs);
    }

    // ── JSON format ──────────────────────────────────────

    public String formatJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // Tests
        sb.append("  \"tests\": {\n");
        sb.append("    \"total\": ").append(testResults.size()).append(",\n");
        sb.append("    \"passed\": ").append(
                testResults.stream()
                        .filter(r -> r.getStatus() == ContractTestRunner.TestStatus.PASSED).count()
        ).append(",\n");
        sb.append("    \"failed\": ").append(
                testResults.stream()
                        .filter(r -> r.getStatus() == ContractTestRunner.TestStatus.FAILED).count()
        ).append(",\n");
        sb.append("    \"errors\": ").append(
                testResults.stream()
                        .filter(r -> r.getStatus() == ContractTestRunner.TestStatus.ERROR).count()
        ).append("\n");
        sb.append("  },\n");

        // Invariants
        sb.append("  \"invariants\": {\n");
        sb.append("    \"violations\": ").append(invariantViolations.size()).append("\n");
        sb.append("  },\n");

        // Fuzzer
        sb.append("  \"fuzzer\": {\n");
        sb.append("    \"totalRuns\": ").append(fuzzResults.size()).append(",\n");
        sb.append("    \"failures\": ").append(
                fuzzResults.stream()
                        .filter(r -> r.getOutcome() != ContractFuzzer.FuzzOutcome.OK).count()
        ).append("\n");
        sb.append("  },\n");

        // Duration
        sb.append("  \"durationMs\": ").append(totalDurationMs).append('\n');
        sb.append("}");

        return sb.toString();
    }

    // ── Accessors ────────────────────────────────────────

    public List<ContractTestRunner.TestResult> getTestResults() {
        return testResults;
    }

    public List<InvariantChecker.InvariantViolation> getInvariantViolations() {
        return invariantViolations;
    }

    public List<ContractFuzzer.FuzzResult> getFuzzResults() {
        return fuzzResults;
    }

    public TestCoverageTracker getCoverageTracker() {
        return coverageTracker;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }
}
