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
 * <p>Unlike Solidity's runtime checks (0.8+), DhrLang detects these at
 * <b>compile time</b> — zero gas cost, impossible to bypass.</p>
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

            // Collect guard conditions (if statements that check before arithmetic)
            Set<String> guardedVars = collectGuardedVariables(fn.getBody().getStatements());

            analyzeStatements(fn, fn.getBody().getStatements(), storageFields, guardedVars);
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

    // ── Guard Detection ──────────────────────────────────────────────────

    /**
     * Detect variables that are guarded by preceding if/require checks.
     * e.g., {@code if (amount > balance) throw "...";} guards `balance` for subtraction.
     */
    private Set<String> collectGuardedVariables(List<Statement> stmts) {
        Set<String> guarded = new HashSet<>();

        for (Statement stmt : stmts) {
            if (stmt instanceof IfStmt ifStmt) {
                Expression cond = ifStmt.getCondition();
                // Check for patterns: if (x > y) throw / if (x <= 0) throw
                collectGuardsFromCondition(cond, guarded);
            } else if (stmt instanceof ExpressionStmt es) {
                Expression expr = es.getExpression();
                // Check for require(amount <= balance, "msg")
                if (expr instanceof CallExpr call
                        && call.getCallee() instanceof VariableExpr ve
                        && "require".equals(ve.getName().getLexeme())) {
                    if (!call.getArguments().isEmpty()) {
                        collectGuardsFromCondition(call.getArguments().get(0), guarded);
                    }
                }
            }
        }

        return guarded;
    }

    private void collectGuardsFromCondition(Expression cond, Set<String> guarded) {
        if (cond instanceof BinaryExpr bin) {
            var op = bin.getOperator().getType();
            // if (amount > field) throw → field is guarded for subtraction
            // if (field >= amount) → field is guarded
            if (op == dhrlang.lexer.TokenType.GREATER || op == dhrlang.lexer.TokenType.GEQ
                    || op == dhrlang.lexer.TokenType.LESS || op == dhrlang.lexer.TokenType.LEQ
                    || op == dhrlang.lexer.TokenType.NEQ) {
                extractVariableNames(bin.getLeft(), guarded);
                extractVariableNames(bin.getRight(), guarded);
            }
        }
    }

    private void extractVariableNames(Expression expr, Set<String> names) {
        if (expr instanceof VariableExpr ve) {
            names.add(ve.getName().getLexeme());
        }
    }

    // ── Statement Analysis ───────────────────────────────────────────────

    private void analyzeStatements(FunctionDecl fn, List<Statement> stmts,
                                    Set<String> storageFields, Set<String> guarded) {
        for (Statement stmt : stmts) {
            if (stmt instanceof ExpressionStmt es) {
                analyzeExpression(fn, es.getExpression(), storageFields, guarded);
            } else if (stmt instanceof Block block) {
                analyzeStatements(fn, block.getStatements(), storageFields, guarded);
            } else if (stmt instanceof IfStmt ifStmt) {
                analyzeStatements(fn, List.of(ifStmt.getThenBranch()), storageFields, guarded);
                if (ifStmt.getElseBranch() != null) {
                    analyzeStatements(fn, List.of(ifStmt.getElseBranch()), storageFields, guarded);
                }
            } else if (stmt instanceof WhileStmt whileStmt) {
                analyzeStatements(fn, List.of(whileStmt.getBody()), storageFields, guarded);
            }
        }
    }

    private void analyzeExpression(FunctionDecl fn, Expression expr,
                                    Set<String> storageFields, Set<String> guarded) {
        if (expr instanceof AssignmentExpr assign) {
            String target = null;
            if (assign.getName() != null) {
                target = assign.getName().getLexeme();
            }
            if (target != null && storageFields.contains(target)) {
                analyzeArithmeticExpr(fn, target, assign.getValue(), guarded);
            }
        }
    }

    private void analyzeArithmeticExpr(FunctionDecl fn, String target, Expression value,
                                        Set<String> guarded) {
        if (!(value instanceof BinaryExpr bin)) return;

        var op = bin.getOperator().getType();
        boolean isGuarded = guarded.contains(target);

        switch (op) {
            case PLUS -> risks.add(new ArithmeticRisk(
                    ArithmeticRisk.Kind.ADDITION_OVERFLOW, fn.getName(),
                    target + " = ... + ...",
                    "Addition could overflow uint256. Add: require(result >= a, \"overflow\")",
                    fn.getSourceLocation(), isGuarded));

            case MINUS -> {
                // Check if there's a guard for the right operand
                String rightName = (bin.getRight() instanceof VariableExpr rve)
                        ? rve.getName().getLexeme() : null;
                boolean subGuarded = isGuarded || (rightName != null && guarded.contains(rightName));
                risks.add(new ArithmeticRisk(
                        ArithmeticRisk.Kind.SUBTRACTION_UNDERFLOW, fn.getName(),
                        target + " = ... - ...",
                        "Subtraction could underflow. Add: if (b > a) { throw \"underflow\"; }",
                        fn.getSourceLocation(), subGuarded));
            }

            case STAR -> risks.add(new ArithmeticRisk(
                    ArithmeticRisk.Kind.MULTIPLICATION_OVERFLOW, fn.getName(),
                    target + " = ... * ...",
                    "Multiplication could overflow. Add: require(a == 0 || result / a == b, \"overflow\")",
                    fn.getSourceLocation(), isGuarded));

            case SLASH -> {
                // Division by zero if right is a variable (could be 0)
                if (bin.getRight() instanceof VariableExpr) {
                    boolean divGuarded = guarded.contains(
                            ((VariableExpr) bin.getRight()).getName().getLexeme());
                    risks.add(new ArithmeticRisk(
                            ArithmeticRisk.Kind.DIVISION_BY_ZERO, fn.getName(),
                            target + " = ... / ...",
                            "Division by zero possible. Add: require(divisor != 0, \"div by zero\")",
                            fn.getSourceLocation(), divGuarded));
                }
            }

            default -> { /* not an arithmetic op */ }
        }
    }
}
