package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.SourceLocation;
import dhrlang.lexer.*;
import dhrlang.error.ErrorReporter;

import java.util.*;

/**
 * Arithmetic overflow/underflow detector for smart contracts.
 *
 * <p>Detects potential integer overflow/underflow at compile time by analyzing
 * arithmetic expressions on storage fields. In blockchain context, overflow
 * can lead to catastrophic fund loss (e.g., the batchOverflow bug in 2018).</p>
 *
 * <h3>Detection patterns:</h3>
 * <ul>
 *   <li>Unchecked addition: {@code a + b} where result could exceed max</li>
 *   <li>Unchecked subtraction: {@code a - b} where b could exceed a</li>
 *   <li>Multiplication overflow: {@code a * b} where result exceeds 256 bits</li>
 *   <li>Division by zero: {@code a / b} where b could be zero</li>
 *   <li>Unguarded decrement: {@code count--} without zero-check</li>
 * </ul>
 *
 * <p>This is an incomplete static analysis, not a replacement for the EVM
 * runtime's checked arithmetic. A recognised bound is attached to the actual
 * write, after preceding control flow and possible invalidating effects.</p>
 */
public class ArithmeticOverflowDetector {

    /**
     * A detected arithmetic risk.
     */
    public static class ArithmeticRisk {
        public enum Kind {
            ADDITION_OVERFLOW,
            SUBTRACTION_UNDERFLOW,
            MULTIPLICATION_OVERFLOW,
            DIVISION_BY_ZERO,
            UNGUARDED_DECREMENT
        }

        private final Kind kind;
        private final String functionName;
        private final String expression;
        private final String hint;
        private final SourceLocation location;
        private final boolean hasGuard;

        public ArithmeticRisk(Kind kind, String functionName, String expression,
                              String hint, SourceLocation location, boolean hasGuard) {
            this.kind = kind;
            this.functionName = functionName;
            this.expression = expression;
            this.hint = hint;
            this.location = location;
            this.hasGuard = hasGuard;
        }

        public Kind getKind() { return kind; }
        public String getFunctionName() { return functionName; }
        public String getExpression() { return expression; }
        public String getHint() { return hint; }
        public SourceLocation getLocation() { return location; }
        public boolean hasGuard() { return hasGuard; }

        @Override
        public String toString() {
            return kind + " in " + functionName + "(): " + expression
                    + (hasGuard ? " (guarded)" : " (UNGUARDED)");
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private final ErrorReporter errorReporter;
    private final List<ArithmeticRisk> risks = new ArrayList<>();

    public ArithmeticOverflowDetector() {
        this(null);
    }

    public ArithmeticOverflowDetector(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Analyze a contract for arithmetic risks.
     */
    public List<ArithmeticRisk> analyze(ClassDecl classDecl) {
        risks.clear();
        if (!classDecl.isContract()) return risks;

        Set<String> storageFields = new HashSet<>();
        for (VarDecl v : classDecl.getVariables()) {
            if (v.hasContractAnnotation(ContractAnnotation.STORAGE)) {
                String type = v.getType();
                if ("num".equals(type) || "uint256".equals(type) || "int256".equals(type)) {
                    storageFields.add(v.getName());
                }
            }
        }

        for (FunctionDecl fn : classDecl.getFunctions()) {
            if (fn.getBody() == null) continue;
            if (fn.hasContractAnnotation(ContractAnnotation.EVENT)) continue;

            for (GuardAnalysis.Write write : GuardAnalysis.analyze(fn, storageFields)) {
                analyzeArithmeticExpr(fn, write);
            }
        }

        // Report unguarded risks
        for (ArithmeticRisk risk : risks) {
            if (!risk.hasGuard() && errorReporter != null) {
                errorReporter.warning(risk.getLocation(),
                        "Potential " + risk.getKind().name().toLowerCase().replace('_', ' ')
                                + " in " + risk.getFunctionName() + "(): " + risk.getExpression(),
                        risk.getHint());
            }
        }

        return risks;
    }

    public List<ArithmeticRisk> getRisks() {
        return Collections.unmodifiableList(risks);
    }

    /**
     * Get only the unguarded (dangerous) risks.
     */
    public List<ArithmeticRisk> getUnguardedRisks() {
        return risks.stream().filter(r -> !r.hasGuard()).toList();
    }

    private void analyzeArithmeticExpr(FunctionDecl fn, GuardAnalysis.Write write) {
        if (!(write.value() instanceof BinaryExpr bin)) return;

        var op = bin.getOperator().getType();
        String target = write.field();
        boolean isGuarded = write.bounds().guards(bin);
        SourceLocation location = write.location() != null ? write.location() : fn.getSourceLocation();

        switch (op) {
            case PLUS -> risks.add(new ArithmeticRisk(
                    ArithmeticRisk.Kind.ADDITION_OVERFLOW, fn.getName(),
                    target + " = ... + ...",
                    "Bound b first, then require(a <= MAX - b) before adding.",
                    location, isGuarded));

            case MINUS -> {
                risks.add(new ArithmeticRisk(
                        ArithmeticRisk.Kind.SUBTRACTION_UNDERFLOW, fn.getName(),
                        target + " = ... - ...",
                        "Subtraction could underflow. Add: if (b > a) { throw \"underflow\"; }",
                        location, isGuarded));
            }

            case STAR -> risks.add(new ArithmeticRisk(
                    ArithmeticRisk.Kind.MULTIPLICATION_OVERFLOW, fn.getName(),
                    target + " = ... * ...",
                    "Require b != 0 and a <= MAX / b before multiplying, or handle zero separately.",
                    location, isGuarded));

            case SLASH, MOD -> {
                risks.add(new ArithmeticRisk(
                            ArithmeticRisk.Kind.DIVISION_BY_ZERO, fn.getName(),
                            target + " = ... / ...",
                            "Division by zero possible. Add: require(divisor != 0, \"div by zero\")",
                            location, isGuarded));
            }

            default -> { /* not an arithmetic op */ }
        }
    }
}
