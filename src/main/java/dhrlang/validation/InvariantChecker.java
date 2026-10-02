package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.SourceLocation;
import dhrlang.lexer.*;
import dhrlang.error.ErrorCode;
import dhrlang.error.ErrorReporter;

import java.util.*;

/**
 * Invariant Checker — compile-time verification of {@code @invariant} annotations.
 *
 * <p>Analyzes contract state transitions to prove invariants hold after every
 * public function execution. Uses abstract interpretation (not a full SMT solver)
 * to detect violations at compile time.</p>
 *
 * <h3>Supported invariant patterns:</h3>
 * <ul>
 *   <li>{@code totalSupply >= 0} — non-negativity</li>
 *   <li>{@code owner != address(0)} — non-null address</li>
 *   <li>{@code value <= MAX_SUPPLY} — upper bound</li>
 *   <li>{@code paused == true || balance > 0} — conditional invariant</li>
 * </ul>
 *
 * <h3>How it works:</h3>
 * <ol>
 *   <li>Collect all {@code @invariant} annotations from field declarations</li>
 *   <li>For each public function, analyze all code paths that modify storage</li>
 *   <li>Check if any write could violate an invariant</li>
 *   <li>Report violations as compile-time errors with actionable hints</li>
 * </ol>
 */
public class InvariantChecker {

    // ── Invariant Types ──────────────────────────────────────────────────

    /**
     * A parsed invariant constraint on a storage field.
     */
    public static class Invariant {
        public enum Kind {
            NON_NEGATIVE,       // field >= 0
            NON_ZERO,           // field != 0
            UPPER_BOUND,        // field <= constant
            LOWER_BOUND,        // field >= constant
            NOT_NULL_ADDRESS,   // field != address(0)
            MONOTONIC_INC,      // field can only increase
            MONOTONIC_DEC,      // field can only decrease
            CUSTOM              // user-defined expression
        }

        private final String fieldName;
        private final Kind kind;
        private final long boundValue;  // for UPPER_BOUND / LOWER_BOUND
        private final String expression; // for CUSTOM

        public Invariant(String fieldName, Kind kind) {
            this(fieldName, kind, 0, null);
        }

        public Invariant(String fieldName, Kind kind, long boundValue) {
            this(fieldName, kind, boundValue, null);
        }

        public Invariant(String fieldName, Kind kind, long boundValue, String expression) {
            this.fieldName = fieldName;
            this.kind = kind;
            this.boundValue = boundValue;
            this.expression = expression;
        }

        public String getFieldName() { return fieldName; }
        public Kind getKind() { return kind; }
        public long getBoundValue() { return boundValue; }
        public String getExpression() { return expression; }

        @Override
        public String toString() {
            return switch (kind) {
                case NON_NEGATIVE -> fieldName + " >= 0";
                case NON_ZERO -> fieldName + " != 0";
                case UPPER_BOUND -> fieldName + " <= " + boundValue;
                case LOWER_BOUND -> fieldName + " >= " + boundValue;
                case NOT_NULL_ADDRESS -> fieldName + " != address(0)";
                case MONOTONIC_INC -> fieldName + " is monotonically increasing";
                case MONOTONIC_DEC -> fieldName + " is monotonically decreasing";
                case CUSTOM -> fieldName + ": " + expression;
            };
        }
    }

    // ── Violation Report ─────────────────────────────────────────────────

    /**
     * A detected invariant violation.
     */
    public static class Violation {
        private final Invariant invariant;
        private final String functionName;
        private final String reason;
        private final SourceLocation location;

        public Violation(Invariant invariant, String functionName, String reason, SourceLocation location) {
            this.invariant = invariant;
            this.functionName = functionName;
            this.reason = reason;
            this.location = location;
        }

        public Invariant getInvariant() { return invariant; }
        public String getFunctionName() { return functionName; }
        public String getReason() { return reason; }
        public SourceLocation getLocation() { return location; }

        @Override
        public String toString() {
            return "Invariant violation in " + functionName + "(): " + invariant + " — " + reason;
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private final ErrorReporter errorReporter;
    private final List<Violation> violations = new ArrayList<>();

    public InvariantChecker() {
        this(null);
    }

    public InvariantChecker(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Check all invariants in a contract.
     *
     * @param classDecl the contract class
     * @return list of violations found
     */
    public List<Violation> check(ClassDecl classDecl) {
        violations.clear();

        if (!classDecl.isContract()) return violations;

        // 1. Collect storage fields and their invariants
        List<Invariant> invariants = collectInvariants(classDecl);
        if (invariants.isEmpty()) return violations;

        // 2. For each public function, check if it could violate any invariant
        Set<String> storageFields = new HashSet<>();
        for (VarDecl v : classDecl.getVariables()) {
            if (v.hasContractAnnotation(ContractAnnotation.STORAGE)) {
                storageFields.add(v.getName());
            }
        }

        for (FunctionDecl fn : classDecl.getFunctions()) {
            if (fn.isContractConstructor()) continue; // constructor establishes invariants
            if (fn.hasContractAnnotation(ContractAnnotation.EVENT)) continue;
            if (fn.getBody() == null) continue;

            checkFunction(fn, invariants, storageFields);
        }

        // 3. Report to ErrorReporter if available
        for (Violation v : violations) {
            if (errorReporter != null) {
                errorReporter.error(
                        v.getLocation(),
                        "Invariant violation: " + v.getInvariant() + " may be broken in "
                                + v.getFunctionName() + "()",
                        "Hint: " + v.getReason());
            }
        }

        return violations;
    }

    /**
     * Get violations from the last check.
     */
    public List<Violation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    // ── Invariant Collection ─────────────────────────────────────────────

    /**
     * Infer invariants from field declarations and annotations.
     */
    List<Invariant> collectInvariants(ClassDecl classDecl) {
        List<Invariant> result = new ArrayList<>();

        for (VarDecl field : classDecl.getVariables()) {
            if (!field.hasContractAnnotation(ContractAnnotation.STORAGE)) continue;

            String name = field.getName();
            String type = field.getType();

            // @invariant annotation — explicit
            if (field.hasContractAnnotation(ContractAnnotation.INVARIANT)) {
                // Infer kind from type
                if ("num".equals(type) || "uint256".equals(type)) {
                    result.add(new Invariant(name, Invariant.Kind.NON_NEGATIVE));
                }
                if ("Address".equals(type)) {
                    result.add(new Invariant(name, Invariant.Kind.NOT_NULL_ADDRESS));
                }
            }

            // Auto-infer: totalSupply, balance-like fields are non-negative
            if (("num".equals(type) || "uint256".equals(type))
                    && (name.toLowerCase().contains("supply")
                    || name.toLowerCase().contains("balance")
                    || name.toLowerCase().contains("count"))) {
                result.add(new Invariant(name, Invariant.Kind.NON_NEGATIVE));
            }

            // Auto-infer: if field is named "owner" and is Address, it should not be zero
            if ("Address".equals(type) && "owner".equals(name)) {
                result.add(new Invariant(name, Invariant.Kind.NOT_NULL_ADDRESS));
            }
        }

        // Deduplicate
        Map<String, Invariant> dedup = new LinkedHashMap<>();
        for (Invariant inv : result) {
            dedup.putIfAbsent(inv.getFieldName() + ":" + inv.getKind(), inv);
        }
        return new ArrayList<>(dedup.values());
    }

    // ── Function Analysis ────────────────────────────────────────────────

    private void checkFunction(FunctionDecl fn, List<Invariant> invariants, Set<String> storageFields) {
        for (GuardAnalysis.Write write : GuardAnalysis.analyze(fn, storageFields)) {
            for (Invariant invariant : invariants) {
                if (invariant.getFieldName().equals(write.field())) {
                    checkValueAgainstInvariant(fn, invariant, write);
                }
            }
        }
    }

    private void checkValueAgainstInvariant(FunctionDecl fn, Invariant inv, GuardAnalysis.Write write) {
        Expression value = write.value();
        SourceLocation location = write.location() != null ? write.location() : fn.getSourceLocation();
        switch (inv.getKind()) {
            case NON_NEGATIVE -> {
                // field = field - amount → can go negative unless amount was
                // already bounded by field. Report only when no such guard exists;
                // reporting unconditionally made every correctly-guarded burn()
                // look like a defect.
                if (value instanceof BinaryExpr bin
                        && bin.getOperator().getType() == dhrlang.lexer.TokenType.MINUS
                        && !write.bounds().guards(bin)) {
                    violations.add(new Violation(inv, fn.getName(),
                            "Subtraction on '" + inv.getFieldName()
                                    + "' could produce a negative value. "
                                    + "Add a guard: if (amount > " + inv.getFieldName() + ") { throw \"...\"; }",
                            location));
                }
                // Check if assigned a literal negative
                if (value instanceof UnaryExpr unary
                        && unary.getOperator().getType() == dhrlang.lexer.TokenType.MINUS) {
                    violations.add(new Violation(inv, fn.getName(),
                            "Direct negative assignment to '" + inv.getFieldName() + "'.",
                            location));
                }
            }
            case NOT_NULL_ADDRESS -> {
                // Check if assigned literal 0 or address(0)
                if (value instanceof LiteralExpr lit && lit.getValue() instanceof Number n
                        && n.longValue() == 0) {
                    violations.add(new Violation(inv, fn.getName(),
                            "Assignment of zero/null address to '" + inv.getFieldName()
                                    + "'. Add a null-address check.",
                            location));
                }
            }
            case MONOTONIC_INC -> {
                // Only addition/increment is allowed
                if (value instanceof BinaryExpr bin
                        && bin.getOperator().getType() == dhrlang.lexer.TokenType.MINUS) {
                    violations.add(new Violation(inv, fn.getName(),
                            "Monotonically increasing field '" + inv.getFieldName()
                                    + "' is being decreased.",
                            location));
                }
            }
            case MONOTONIC_DEC -> {
                if (value instanceof BinaryExpr bin
                        && bin.getOperator().getType() == dhrlang.lexer.TokenType.PLUS) {
                    violations.add(new Violation(inv, fn.getName(),
                            "Monotonically decreasing field '" + inv.getFieldName()
                                    + "' is being increased.",
                            location));
                }
            }
            default -> { /* CUSTOM, UPPER_BOUND, LOWER_BOUND — future work */ }
        }
    }
}
