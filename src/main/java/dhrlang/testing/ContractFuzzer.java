package dhrlang.testing;

import dhrlang.ast.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fuzzer for DhrLang smart contracts. Generates random inputs for contract
 * functions and runs them repeatedly, checking for invariant violations,
 * unexpected reverts, and other anomalies.
 *
 * <p>The fuzzer:
 * <ol>
 *   <li>Discovers all public/non-view functions in a contract</li>
 *   <li>Generates random arguments based on parameter types</li>
 *   <li>Calls each function with random inputs</li>
 *   <li>After each call, evaluates all invariants</li>
 *   <li>Collects and minimizes failing test cases</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * ContractFuzzer fuzzer = new ContractFuzzer(program);
 * fuzzer.setRuns(1000);
 * fuzzer.fuzzAll();
 * List&lt;FuzzResult&gt; results = fuzzer.getResults();
 * </pre>
 */
public class ContractFuzzer {

    // ── Result types ─────────────────────────────────────

    /**
     * Outcome of a single fuzz iteration.
     *
     * <ul>
     *   <li>{@code OK} — the call completed and every specification held.</li>
     *   <li>{@code INVARIANT_VIOLATION} — a concrete counterexample falsified an
     *       {@code @ensures} postcondition or an {@code @invariant}.</li>
     *   <li>{@code REVERT} — the call reverted (failed {@code require}/{@code revert}
     *       or checked-arithmetic overflow). Acceptable behaviour, not a bug.</li>
     *   <li>{@code SKIPPED} — the run was inconclusive (precondition not met, or the
     *       body used a construct the engine cannot model precisely). Sound: never a
     *       false positive.</li>
     *   <li>{@code EXCEPTION} — an unexpected internal error.</li>
     * </ul>
     */
    public enum FuzzOutcome { OK, INVARIANT_VIOLATION, REVERT, SKIPPED, EXCEPTION }

    /** A single fuzz finding. */
    public static final class FuzzResult {
        private final String contractName;
        private final String functionName;
        private final List<Object> arguments;
        private final FuzzOutcome outcome;
        private final String detail;

        public FuzzResult(String contractName, String functionName,
                          List<Object> arguments, FuzzOutcome outcome,
                          String detail) {
            this.contractName = contractName;
            this.functionName = functionName;
            this.arguments = List.copyOf(arguments);
            this.outcome = outcome;
            this.detail = detail;
        }

        public String getContractName() { return contractName; }
        public String getFunctionName() { return functionName; }
        public List<Object> getArguments() { return arguments; }
        public FuzzOutcome getOutcome() { return outcome; }
        public String getDetail() { return detail; }

        @Override
        public String toString() {
            return String.format("[%s] %s::%s(%s) — %s",
                    outcome, contractName, functionName,
                    argumentsToString(), detail);
        }

        private String argumentsToString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(arguments.get(i));
            }
            return sb.toString();
        }
    }

    /** Statistics for a fuzz campaign on one function. */
    public static final class FuzzStats {
        private final String contractName;
        private final String functionName;
        private int totalRuns;
        private int okCount;
        private int invariantViolations;
        private int reverts;
        private int skipped;
        private int exceptions;

        public FuzzStats(String contractName, String functionName) {
            this.contractName = contractName;
            this.functionName = functionName;
        }

        public void record(FuzzOutcome outcome) {
            totalRuns++;
            switch (outcome) {
                case OK -> okCount++;
                case INVARIANT_VIOLATION -> invariantViolations++;
                case REVERT -> reverts++;
                case SKIPPED -> skipped++;
                case EXCEPTION -> exceptions++;
            }
        }

        public String getContractName() { return contractName; }
        public String getFunctionName() { return functionName; }
        public int getTotalRuns() { return totalRuns; }
        public int getOkCount() { return okCount; }
        public int getInvariantViolations() { return invariantViolations; }
        public int getReverts() { return reverts; }
        public int getSkipped() { return skipped; }
        public int getExceptions() { return exceptions; }
        public boolean hasFailures() {
            return invariantViolations > 0 || exceptions > 0;
        }
    }

    // ── Fields ───────────────────────────────────────────

    private final Program program;
    private int maxRuns = 100;
    private long seed = -1;  // -1 = use random seed
    private final List<FuzzResult> results = new ArrayList<>();
    private final Map<String, FuzzStats> statsMap = new LinkedHashMap<>();
    private Random random;

    // ── Constructor ──────────────────────────────────────

    public ContractFuzzer(Program program) {
        this.program = Objects.requireNonNull(program, "program");
        this.random = ThreadLocalRandom.current();
    }

    // ── Configuration ────────────────────────────────────

    public void setRuns(int maxRuns) {
        if (maxRuns < 1) throw new IllegalArgumentException("maxRuns must be >= 1");
        this.maxRuns = maxRuns;
    }

    public int getRuns() { return maxRuns; }

    public void setSeed(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public long getSeed() { return seed; }

    // ── Discovery ────────────────────────────────────────

    /**
     * Get all fuzzable functions: non-view, non-pure, non-test, non-lifecycle.
     */
    public List<FunctionDecl> discoverFuzzTargets(ClassDecl cls) {
        List<FunctionDecl> targets = new ArrayList<>();
        for (FunctionDecl fn : cls.getFunctions()) {
            Set<ContractAnnotation> annotations = fn.getContractAnnotations();
            boolean skip = annotations.contains(ContractAnnotation.VIEW)
                    || annotations.contains(ContractAnnotation.PURE)
                    || annotations.contains(ContractAnnotation.INVARIANT)
                    || annotations.contains(ContractAnnotation.TEST)
                    || annotations.contains(ContractAnnotation.BEFORE_EACH)
                    || annotations.contains(ContractAnnotation.AFTER_EACH)
                    || annotations.contains(ContractAnnotation.CONSTRUCTOR);
            if (!skip) {
                targets.add(fn);
            }
        }
        return targets;
    }

    // ── Fuzzing ──────────────────────────────────────────

    /**
     * Fuzz all contracts in the program.
     */
    public void fuzzAll() {
        results.clear();
        statsMap.clear();

        for (ClassDecl cls : program.getClasses()) {
            List<FunctionDecl> targets = discoverFuzzTargets(cls);
            if (targets.isEmpty()) continue;

            for (FunctionDecl fn : targets) {
                fuzzFunction(cls, fn);
            }
        }
    }

    /**
     * Fuzz a single function with random inputs for {@code maxRuns} iterations.
     */
    public void fuzzFunction(ClassDecl cls, FunctionDecl fn) {
        String key = cls.getName() + "::" + fn.getName();
        FuzzStats stats = new FuzzStats(cls.getName(), fn.getName());
        statsMap.put(key, stats);

        for (int i = 0; i < maxRuns; i++) {
            List<Object> args = generateArguments(fn.getParameters());
            FuzzResult result = executeFuzzIteration(cls, fn, args);
            stats.record(result.getOutcome());

            // Only store failures and first few OKs
            if (result.getOutcome() != FuzzOutcome.OK || i < 3) {
                results.add(result);
            }
        }
    }

    /**
     * Execute one fuzz iteration: run the function under test over a simulated
     * EVM state and check its design-by-contract specifications.
     *
     * <p>Functions whose contract declares no {@code @ensures}/{@code @invariant}
     * have nothing a fuzzer could falsify, so they short-circuit to {@code OK}.
     * Otherwise the {@link SpecFuzzEngine} executes the body and reports the
     * outcome; a violation is minimized toward a smaller counterexample before
     * being recorded.
     */
    private FuzzResult executeFuzzIteration(ClassDecl cls, FunctionDecl fn,
                                             List<Object> args) {
        try {
            if (args.size() != fn.getParameters().size()) {
                return new FuzzResult(cls.getName(), fn.getName(), args,
                        FuzzOutcome.EXCEPTION, "Argument count mismatch");
            }

            boolean hasSpecs = !fn.getEnsures().isEmpty() || !cls.getInvariants().isEmpty();
            if (!hasSpecs) {
                return new FuzzResult(cls.getName(), fn.getName(), args,
                        FuzzOutcome.OK, "No specifications to check");
            }

            SpecFuzzEngine.Result r = new SpecFuzzEngine(cls).run(fn, args);
            return switch (r.outcome) {
                case OK -> new FuzzResult(cls.getName(), fn.getName(), args,
                        FuzzOutcome.OK, r.detail);
                case REVERT -> new FuzzResult(cls.getName(), fn.getName(), args,
                        FuzzOutcome.REVERT, r.detail);
                case PRECONDITION_SKIP, UNSUPPORTED -> new FuzzResult(
                        cls.getName(), fn.getName(), args, FuzzOutcome.SKIPPED, r.detail);
                case VIOLATION -> {
                    List<Object> minimal = minimizeCounterexample(cls, fn, args);
                    SpecFuzzEngine.Result mr = new SpecFuzzEngine(cls).run(fn, minimal);
                    String detail = mr.outcome == SpecFuzzEngine.Outcome.VIOLATION
                            ? mr.detail : r.detail;
                    yield new FuzzResult(cls.getName(), fn.getName(), minimal,
                            FuzzOutcome.INVARIANT_VIOLATION, detail);
                }
            };

        } catch (ContractAssertions.AssertionError e) {
            return new FuzzResult(cls.getName(), fn.getName(), args,
                    FuzzOutcome.INVARIANT_VIOLATION, e.getMessage());

        } catch (Exception e) {
            return new FuzzResult(cls.getName(), fn.getName(), args,
                    FuzzOutcome.EXCEPTION, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Greedily shrink a failing argument vector toward zero/one while the
     * specification violation still reproduces. Deterministic: the engine runs
     * from a fixed genesis state, so each trial is reproducible.
     */
    private List<Object> minimizeCounterexample(ClassDecl cls, FunctionDecl fn,
                                                 List<Object> args) {
        List<Object> current = new ArrayList<>(args);
        for (int i = 0; i < current.size(); i++) {
            for (Object candidate : simplerValues(current.get(i))) {
                List<Object> trial = new ArrayList<>(current);
                trial.set(i, candidate);
                SpecFuzzEngine.Result r = new SpecFuzzEngine(cls).run(fn, trial);
                if (r.outcome == SpecFuzzEngine.Outcome.VIOLATION) {
                    current = trial;
                    break;
                }
            }
        }
        return current;
    }

    /** Candidate simpler values (0 then 1) for a numeric argument, preserving its type. */
    private static List<Object> simplerValues(Object v) {
        List<Object> out = new ArrayList<>();
        if (v instanceof Integer i) {
            if (i != 0) out.add(0);
            if (i != 0 && i != 1) out.add(1);
        } else if (v instanceof Long l) {
            if (l != 0L) out.add(0L);
            if (l != 0L && l != 1L) out.add(1L);
        }
        return out;
    }

    // ── Random input generation ──────────────────────────

    /**
     * Generate random arguments for a list of parameters based on their types.
     */
    public List<Object> generateArguments(List<VarDecl> params) {
        List<Object> args = new ArrayList<>();
        for (VarDecl param : params) {
            args.add(generateValue(param.getType()));
        }
        return args;
    }

    /**
     * Generate a random value for a given type.
     */
    public Object generateValue(String type) {
        return switch (type) {
            case "num", "int", "Int" -> randomInt();
            case "duo", "double", "Double", "float" -> randomDouble();
            case "kya", "bool", "Boolean" -> random.nextBoolean();
            case "sab", "String", "string" -> randomString();
            case "ek", "char", "Char" -> randomChar();
            case "uint256" -> randomUint256();
            case "int256" -> randomInt256();
            case "Address", "address" -> randomAddress();
            case "bytes32" -> randomBytes32();
            default -> randomInt();  // fallback
        };
    }

    private int randomInt() {
        // Mix of interesting values and random
        return switch (random.nextInt(10)) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> -1;
            case 3 -> Integer.MAX_VALUE;
            case 4 -> Integer.MIN_VALUE;
            default -> random.nextInt();
        };
    }

    private double randomDouble() {
        return switch (random.nextInt(8)) {
            case 0 -> 0.0;
            case 1 -> 1.0;
            case 2 -> -1.0;
            case 3 -> Double.MAX_VALUE;
            case 4 -> Double.MIN_VALUE;
            default -> random.nextDouble() * 1000;
        };
    }

    private String randomString() {
        int len = random.nextInt(20);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    private char randomChar() {
        return (char) ('a' + random.nextInt(26));
    }

    private long randomUint256() {
        return switch (random.nextInt(6)) {
            case 0 -> 0L;
            case 1 -> 1L;
            case 2 -> Long.MAX_VALUE;
            default -> Math.abs(random.nextLong());
        };
    }

    private long randomInt256() {
        return switch (random.nextInt(6)) {
            case 0 -> 0L;
            case 1 -> Long.MAX_VALUE;
            case 2 -> Long.MIN_VALUE;
            default -> random.nextLong();
        };
    }

    private String randomAddress() {
        StringBuilder sb = new StringBuilder("0x");
        for (int i = 0; i < 40; i++) {
            sb.append(Integer.toHexString(random.nextInt(16)));
        }
        return sb.toString();
    }

    private String randomBytes32() {
        StringBuilder sb = new StringBuilder("0x");
        for (int i = 0; i < 64; i++) {
            sb.append(Integer.toHexString(random.nextInt(16)));
        }
        return sb.toString();
    }

    // ── Accessors ────────────────────────────────────────

    public List<FuzzResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public Map<String, FuzzStats> getStats() {
        return Collections.unmodifiableMap(statsMap);
    }

    /** A failure is a genuine bug signal: a spec violation or an internal error. Reverts and skips are not failures. */
    private static boolean isFailure(FuzzResult r) {
        return r.getOutcome() == FuzzOutcome.INVARIANT_VIOLATION
                || r.getOutcome() == FuzzOutcome.EXCEPTION;
    }

    public long totalFailures() {
        return results.stream().filter(ContractFuzzer::isFailure).count();
    }

    public boolean hasFailures() {
        return results.stream().anyMatch(ContractFuzzer::isFailure);
    }

    // ── Report ───────────────────────────────────────────

    /**
     * Format a summary of the fuzzing campaign.
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Fuzzer Report ═══\n\n");
        sb.append("Configuration: ").append(maxRuns).append(" runs per function");
        if (seed >= 0) sb.append(", seed=").append(seed);
        sb.append('\n').append('\n');

        for (var entry : statsMap.entrySet()) {
            FuzzStats s = entry.getValue();
            sb.append(String.format(
                    "  %s::%s — %d runs: %d ok, %d violations, %d reverts, %d skipped, %d errors\n",
                    s.getContractName(), s.getFunctionName(),
                    s.getTotalRuns(), s.getOkCount(),
                    s.getInvariantViolations(), s.getReverts(), s.getSkipped(), s.getExceptions()));
        }

        sb.append('\n');
        long failures = totalFailures();
        sb.append("Failures found: ").append(failures).append('\n');

        if (failures > 0) {
            sb.append("\nFailing inputs:\n");
            results.stream()
                    .filter(ContractFuzzer::isFailure)
                    .limit(20)
                    .forEach(r -> sb.append("  ✗ ").append(r).append('\n'));
        }

        sb.append('\n');
        sb.append(failures == 0
                ? "Result: NO ISSUES FOUND ✓\n"
                : "Result: ISSUES FOUND ✗\n");

        return sb.toString();
    }
}
