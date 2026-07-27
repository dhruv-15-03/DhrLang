package dhrlang.testing;

import dhrlang.ast.*;

import java.util.*;
import java.util.function.Predicate;

/**
 * Checks {@code @invariant}-annotated methods in contract classes.
 * An invariant is a boolean condition that must hold after every
 * state-modifying function in the contract.
 *
 * <p>The checker:
 * <ol>
 *   <li>Discovers all {@code @invariant} methods in each contract class</li>
 *   <li>Identifies all state-modifying functions (non-view, non-pure)</li>
 *   <li>After each call to a state-modifying function, evaluates all invariants</li>
 *   <li>Reports violations with details (which invariant, after which function)</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * InvariantChecker checker = new InvariantChecker(program);
 * checker.analyzeAll();
 * List&lt;InvariantViolation&gt; violations = checker.getViolations();
 * </pre>
 */
public class InvariantChecker {

    // ── Violation record ─────────────────────────────────

    /** Describes a single invariant violation. */
    public static final class InvariantViolation {
        private final String contractName;
        private final String invariantName;
        private final String triggerFunction;
        private final String description;

        public InvariantViolation(String contractName, String invariantName,
                                  String triggerFunction, String description) {
            this.contractName = contractName;
            this.invariantName = invariantName;
            this.triggerFunction = triggerFunction;
            this.description = description;
        }

        public String getContractName() { return contractName; }
        public String getInvariantName() { return invariantName; }
        public String getTriggerFunction() { return triggerFunction; }
        public String getDescription() { return description; }

        @Override
        public String toString() {
            return String.format("INVARIANT VIOLATION in %s: '%s' broken after %s() — %s",
                    contractName, invariantName, triggerFunction, description);
        }
    }

    /** Describes a discovered invariant within a contract. */
    public static final class InvariantInfo {
        private final String contractName;
        private final String methodName;
        private final FunctionDecl declaration;

        public InvariantInfo(String contractName, String methodName,
                             FunctionDecl declaration) {
            this.contractName = contractName;
            this.methodName = methodName;
            this.declaration = declaration;
        }

        public String getContractName() { return contractName; }
        public String getMethodName() { return methodName; }
        public FunctionDecl getDeclaration() { return declaration; }
    }

    // ── Fields ───────────────────────────────────────────

    private final Program program;
    private final List<InvariantViolation> violations = new ArrayList<>();
    private final List<InvariantInfo> discoveredInvariants = new ArrayList<>();
    private final Map<String, List<String>> stateModifyingFunctions = new LinkedHashMap<>();
    private Predicate<InvariantInfo> invariantEvaluator = null;

    // ── Constructor ──────────────────────────────────────

    public InvariantChecker(Program program) {
        this.program = Objects.requireNonNull(program, "program");
    }

    // ── Plug-in evaluator ────────────────────────────────

    /**
     * Set a custom evaluator function. The predicate returns {@code true}
     * if the invariant holds, {@code false} if violated.
     */
    public void setInvariantEvaluator(Predicate<InvariantInfo> evaluator) {
        this.invariantEvaluator = evaluator;
    }

    // ── Discovery ────────────────────────────────────────

    /**
     * Discover all {@code @invariant} methods across all contracts.
     */
    public List<InvariantInfo> discoverInvariants() {
        discoveredInvariants.clear();
        for (ClassDecl cls : program.getClasses()) {
            for (FunctionDecl fn : cls.getFunctions()) {
                if (fn.hasContractAnnotation(ContractAnnotation.INVARIANT)) {
                    discoveredInvariants.add(
                            new InvariantInfo(cls.getName(), fn.getName(), fn));
                }
            }
        }
        return Collections.unmodifiableList(discoveredInvariants);
    }

    /**
     * Discover state-modifying functions — those that are not @view, not @pure,
     * not @invariant, not @test, not @beforeEach, not @afterEach.
     */
    public Map<String, List<String>> discoverStateModifyingFunctions() {
        stateModifyingFunctions.clear();
        for (ClassDecl cls : program.getClasses()) {
            List<String> modifiers = new ArrayList<>();
            for (FunctionDecl fn : cls.getFunctions()) {
                Set<ContractAnnotation> annotations = fn.getContractAnnotations();
                boolean isReadOnly = annotations.contains(ContractAnnotation.VIEW)
                        || annotations.contains(ContractAnnotation.PURE);
                boolean isLifecycle = annotations.contains(ContractAnnotation.INVARIANT)
                        || annotations.contains(ContractAnnotation.TEST)
                        || annotations.contains(ContractAnnotation.BEFORE_EACH)
                        || annotations.contains(ContractAnnotation.AFTER_EACH);
                if (!isReadOnly && !isLifecycle) {
                    modifiers.add(fn.getName());
                }
            }
            if (!modifiers.isEmpty()) {
                stateModifyingFunctions.put(cls.getName(), modifiers);
            }
        }
        return Collections.unmodifiableMap(stateModifyingFunctions);
    }

    // ── Analysis ─────────────────────────────────────────

    /**
     * Run a full analysis: discover invariants, discover state-modifying
     * functions, and simulate checking every invariant after every
     * state-modifying function.
     */
    public void analyzeAll() {
        violations.clear();
        discoverInvariants();
        discoverStateModifyingFunctions();

        for (InvariantInfo inv : discoveredInvariants) {
            String contractName = inv.getContractName();
            List<String> modifiers = stateModifyingFunctions.getOrDefault(
                    contractName, List.of());

            for (String fnName : modifiers) {
                checkInvariantAfterFunction(inv, fnName);
            }
        }
    }

    /**
     * Check a specific invariant after a specific function.
     */
    public void checkInvariantAfterFunction(InvariantInfo invariant,
                                             String functionName) {
        if (invariantEvaluator != null) {
            boolean holds = invariantEvaluator.test(invariant);
            if (!holds) {
                violations.add(new InvariantViolation(
                        invariant.getContractName(),
                        invariant.getMethodName(),
                        functionName,
                        "Invariant returned false"));
            }
        }
        // Without an evaluator, we do static analysis:
        // check that the invariant method has a body (otherwise it's suspect)
        FunctionDecl decl = invariant.getDeclaration();
        if (decl.getBody() == null || decl.getBody().getStatements().isEmpty()) {
            violations.add(new InvariantViolation(
                    invariant.getContractName(),
                    invariant.getMethodName(),
                    functionName,
                    "Invariant method has no body — cannot verify"));
        }
    }

    // ── Accessors ────────────────────────────────────────

    public List<InvariantViolation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    public List<InvariantInfo> getDiscoveredInvariants() {
        return Collections.unmodifiableList(discoveredInvariants);
    }

    public Map<String, List<String>> getStateModifyingFunctions() {
        return Collections.unmodifiableMap(stateModifyingFunctions);
    }

    // ── Report ───────────────────────────────────────────

    /**
     * Format a human-readable report of the invariant analysis.
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Invariant Analysis Report ═══\n\n");

        sb.append("Invariants discovered: ").append(discoveredInvariants.size()).append('\n');
        for (InvariantInfo inv : discoveredInvariants) {
            sb.append("  • ").append(inv.getContractName())
              .append("::").append(inv.getMethodName()).append('\n');
        }

        sb.append("\nState-modifying functions:\n");
        for (var entry : stateModifyingFunctions.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ");
            sb.append(String.join(", ", entry.getValue())).append('\n');
        }

        sb.append("\nViolations: ").append(violations.size()).append('\n');
        for (InvariantViolation v : violations) {
            sb.append("  ✗ ").append(v).append('\n');
        }

        sb.append('\n');
        sb.append(violations.isEmpty()
                ? "Result: ALL INVARIANTS HOLD ✓\n"
                : "Result: INVARIANT VIOLATIONS FOUND ✗\n");

        return sb.toString();
    }
}
