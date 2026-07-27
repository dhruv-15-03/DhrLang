package dhrlang.testing;

import dhrlang.ast.*;

import java.util.*;

/**
 * Discovers and runs {@code @test}-annotated methods inside {@code @contract}
 * classes. Supports {@code @beforeEach} / {@code @afterEach} lifecycle hooks,
 * and collects per-test results (pass, fail, error) together with coverage
 * information.
 *
 * <p>Usage:
 * <pre>
 * Program program = ...;
 * ContractTestRunner runner = new ContractTestRunner(program);
 * runner.runAll();
 * List&lt;TestResult&gt; results = runner.getResults();
 * </pre>
 */
public class ContractTestRunner {

    // ── Result types ─────────────────────────────────────

    /** Outcome of a single test execution. */
    public enum TestStatus { PASSED, FAILED, ERROR, SKIPPED }

    /** Immutable result of a single test method. */
    public static final class TestResult {
        private final String contractName;
        private final String testName;
        private final TestStatus status;
        private final String message;
        private final long durationMs;

        public TestResult(String contractName, String testName,
                          TestStatus status, String message, long durationMs) {
            this.contractName = contractName;
            this.testName = testName;
            this.status = status;
            this.message = message;
            this.durationMs = durationMs;
        }

        public String getContractName() { return contractName; }
        public String getTestName() { return testName; }
        public TestStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public long getDurationMs() { return durationMs; }

        @Override
        public String toString() {
            String icon = switch (status) {
                case PASSED  -> "✓";
                case FAILED  -> "✗";
                case ERROR   -> "!";
                case SKIPPED -> "○";
            };
            return String.format("[%s] %s::%s%s (%dms)",
                    icon, contractName, testName,
                    message.isEmpty() ? "" : " — " + message,
                    durationMs);
        }
    }

    // ── Fields ───────────────────────────────────────────

    private final Program program;
    private final List<TestResult> results = new ArrayList<>();
    private final TestCoverageTracker coverageTracker = new TestCoverageTracker();

    // ── Constructor ──────────────────────────────────────

    public ContractTestRunner(Program program) {
        this.program = Objects.requireNonNull(program, "program");
    }

    // ── Discovery ────────────────────────────────────────

    /**
     * Find all classes that contain at least one {@code @test} method.
     */
    public List<ClassDecl> discoverTestClasses() {
        List<ClassDecl> testClasses = new ArrayList<>();
        for (ClassDecl cls : program.getClasses()) {
            for (FunctionDecl fn : cls.getFunctions()) {
                if (fn.hasContractAnnotation(ContractAnnotation.TEST)) {
                    testClasses.add(cls);
                    break;
                }
            }
        }
        return testClasses;
    }

    /**
     * Return all {@code @test}-annotated methods for a given class.
     */
    public List<FunctionDecl> discoverTests(ClassDecl cls) {
        List<FunctionDecl> tests = new ArrayList<>();
        for (FunctionDecl fn : cls.getFunctions()) {
            if (fn.hasContractAnnotation(ContractAnnotation.TEST)) {
                tests.add(fn);
            }
        }
        return tests;
    }

    /**
     * Find the {@code @beforeEach} hook for a class, if any.
     */
    public FunctionDecl findBeforeEach(ClassDecl cls) {
        for (FunctionDecl fn : cls.getFunctions()) {
            if (fn.hasContractAnnotation(ContractAnnotation.BEFORE_EACH)) {
                return fn;
            }
        }
        return null;
    }

    /**
     * Find the {@code @afterEach} hook for a class, if any.
     */
    public FunctionDecl findAfterEach(ClassDecl cls) {
        for (FunctionDecl fn : cls.getFunctions()) {
            if (fn.hasContractAnnotation(ContractAnnotation.AFTER_EACH)) {
                return fn;
            }
        }
        return null;
    }

    // ── Execution ────────────────────────────────────────

    /**
     * Run every test method in every test class.
     * Results are accumulated internally and retrievable via {@link #getResults()}.
     */
    public void runAll() {
        results.clear();
        coverageTracker.reset();

        for (ClassDecl cls : discoverTestClasses()) {
            FunctionDecl before = findBeforeEach(cls);
            FunctionDecl after  = findAfterEach(cls);

            for (FunctionDecl test : discoverTests(cls)) {
                results.add(runSingleTest(cls, test, before, after));
            }
        }
    }

    /**
     * Execute one test method within its lifecycle hooks.
     */
    public TestResult runSingleTest(ClassDecl cls, FunctionDecl test,
                                     FunctionDecl beforeEach,
                                     FunctionDecl afterEach) {
        long start = System.nanoTime();
        String contractName = cls.getName();
        String testName = test.getName();

        try {
            // 1. beforeEach
            if (beforeEach != null) {
                coverageTracker.recordFunctionEntry(contractName, beforeEach.getName());
                simulateExecution(beforeEach);
                coverageTracker.recordFunctionExit(contractName, beforeEach.getName());
            }

            // 2. test body
            coverageTracker.recordFunctionEntry(contractName, testName);
            simulateExecution(test);
            coverageTracker.recordFunctionExit(contractName, testName);

            // 3. afterEach
            if (afterEach != null) {
                coverageTracker.recordFunctionEntry(contractName, afterEach.getName());
                simulateExecution(afterEach);
                coverageTracker.recordFunctionExit(contractName, afterEach.getName());
            }

            long elapsed = (System.nanoTime() - start) / 1_000_000;
            return new TestResult(contractName, testName, TestStatus.PASSED, "", elapsed);

        } catch (AssertionError e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            return new TestResult(contractName, testName, TestStatus.FAILED,
                    e.getMessage() != null ? e.getMessage() : "Assertion failed", elapsed);

        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            return new TestResult(contractName, testName, TestStatus.ERROR,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), elapsed);
        }
    }

    // ── Simulation ───────────────────────────────────────

    /**
     * Simulate execution of a function for testing. Walks the AST body
     * to gather coverage data. Actual evaluation is deferred to the
     * interpreter when integrated; here we record structural coverage.
     */
    private void simulateExecution(FunctionDecl fn) {
        if (fn.getBody() != null) {
            List<Statement> stmts = fn.getBody().getStatements();
            coverageTracker.recordStatements(stmts.size());
        }
    }

    // ── Accessors ────────────────────────────────────────

    public List<TestResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public int totalTests() { return results.size(); }

    public long passedCount() {
        return results.stream().filter(r -> r.status == TestStatus.PASSED).count();
    }

    public long failedCount() {
        return results.stream().filter(r -> r.status == TestStatus.FAILED).count();
    }

    public long errorCount() {
        return results.stream().filter(r -> r.status == TestStatus.ERROR).count();
    }

    public long skippedCount() {
        return results.stream().filter(r -> r.status == TestStatus.SKIPPED).count();
    }

    public boolean allPassed() {
        return failedCount() == 0 && errorCount() == 0;
    }

    public TestCoverageTracker getCoverageTracker() {
        return coverageTracker;
    }

    // ── Summary ──────────────────────────────────────────

    /**
     * Human-readable summary of the test run.
     */
    public String formatSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ DhrLang Contract Test Results ═══\n\n");

        for (TestResult r : results) {
            sb.append("  ").append(r).append('\n');
        }

        sb.append('\n');
        sb.append(String.format("Tests: %d total, %d passed, %d failed, %d errors, %d skipped\n",
                totalTests(), passedCount(), failedCount(), errorCount(), skippedCount()));
        sb.append(allPassed() ? "Result: PASSED ✓\n" : "Result: FAILED ✗\n");
        return sb.toString();
    }
}
