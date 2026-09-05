package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.lexer.TokenType;

import java.util.*;

/**
 * Shared guard recognition for the contract safety analyzers.
 *
 * <p>Several detectors need to answer the same question: "before this
 * arithmetic ran, did the function already reject the dangerous inputs?"
 * {@link ArithmeticOverflowDetector} and {@link InvariantChecker} previously
 * answered it in different places — the overflow detector implemented it, and
 * the invariant checker did not implement it at all and unconditionally
 * reported every subtraction. This class is the single implementation both
 * use.</p>
 *
 * <h3>Recognised guard forms</h3>
 * <p>A guard is a comparison ({@code >}, {@code >=}, {@code <}, {@code <=},
 * {@code !=}) appearing either as the condition of an {@code if} statement or
 * as the first argument of a {@code require(...)} call:</p>
 * <pre>{@code
 *   if (amount > totalSupply) { throw "Insufficient supply"; }
 *   require(amount <= totalSupply, "Insufficient supply");
 * }</pre>
 *
 * <h3>Two deliberate limitations</h3>
 * <ol>
 *   <li><b>Only direct variable operands count.</b> {@link #operandNames} does
 *       not recurse into sub-expressions, so in
 *       {@code if (amount > maxSupply - totalSupply)} the analyzers see
 *       {@code amount} and nothing else — {@code totalSupply} is buried inside
 *       a nested {@code BinaryExpr} and is <i>not</i> registered. Any guard
 *       written to satisfy these detectors must therefore mention the field as
 *       a direct operand of the comparison. This is a real trap when authoring
 *       contracts, which is why it is stated here rather than left to be
 *       rediscovered.</li>
 *   <li><b>Only statements at the top level of the function body are scanned.</b>
 *       Guards nested inside another block are not collected. The prevailing
 *       contract idiom is a flat sequence of {@code if (...) { throw ...; }}
 *       preambles, which this covers.</li>
 * </ol>
 *
 * <p>Both limitations make the analysis <i>conservative</i>: an unrecognised
 * guard yields a report, never silence. That is the safe direction to err for a
 * security analyzer.</p>
 */
public final class GuardAnalysis {

    private GuardAnalysis() {
    }

    /**
     * Comparison operators that can express a bounds check.
     */
    private static boolean isComparison(TokenType op) {
        return op == TokenType.GREATER || op == TokenType.GEQ
                || op == TokenType.LESS || op == TokenType.LEQ
                || op == TokenType.NEQ;
    }

    /**
     * Collect one operand-name set per recognised guard comparison.
     *
     * <p>Keeping the operands grouped per comparison — rather than flattening
     * them into a single set — is what lets a caller ask whether two specific
     * names were compared <em>against each other</em>, as opposed to merely
     * both appearing somewhere in the function.</p>
     *
     * @param stmts statements of a function body
     * @return one set of directly-referenced variable names per guard
     */
    public static List<Set<String>> collectComparisons(List<Statement> stmts) {
        List<Set<String>> comparisons = new ArrayList<>();
        if (stmts == null) return comparisons;

        for (Statement stmt : stmts) {
            if (stmt instanceof IfStmt ifStmt) {
                addComparison(ifStmt.getCondition(), comparisons);
            } else if (stmt instanceof ExpressionStmt es
                    && es.getExpression() instanceof CallExpr call
                    && call.getCallee() instanceof VariableExpr callee
                    && "require".equals(callee.getName().getLexeme())
                    && !call.getArguments().isEmpty()) {
                addComparison(call.getArguments().get(0), comparisons);
            }
        }
        return comparisons;
    }

    private static void addComparison(Expression cond, List<Set<String>> out) {
        if (cond instanceof BinaryExpr bin && isComparison(bin.getOperator().getType())) {
            Set<String> names = new HashSet<>();
            operandNames(bin.getLeft(), names);
            operandNames(bin.getRight(), names);
            if (!names.isEmpty()) {
                out.add(names);
            }
        }
    }

    /**
     * Every variable mentioned as a direct operand of any guard comparison.
     *
     * <p>This is the flattened view and reproduces the original behaviour of
     * {@code ArithmeticOverflowDetector.collectGuardedVariables}.</p>
     *
     * @param stmts statements of a function body
     * @return names that appear in at least one guard
     */
    public static Set<String> collectGuardedVariables(List<Statement> stmts) {
        Set<String> guarded = new HashSet<>();
        for (Set<String> comparison : collectComparisons(stmts)) {
            guarded.addAll(comparison);
        }
        return guarded;
    }

    /**
     * Report whether {@code a} and {@code b} were compared against each other.
     *
     * <p>This is strictly stronger than asking whether both names are guarded.
     * {@code if (amount <= 0) { throw ...; }} guards {@code amount}, but proves
     * nothing about its relationship to {@code totalSupply}, so it cannot
     * justify {@code totalSupply = totalSupply - amount}. Requiring both names
     * in the <em>same</em> comparison is what distinguishes a real bounds check
     * from an unrelated sanity check.</p>
     *
     * @param stmts statements of a function body
     * @param a first name, typically the field carrying the invariant
     * @param b second name, typically the subtrahend
     * @return true when some single guard compares the two
     */
    public static boolean hasRelationalGuard(List<Statement> stmts, String a, String b) {
        if (a == null || b == null) return false;
        for (Set<String> comparison : collectComparisons(stmts)) {
            if (comparison.contains(a) && comparison.contains(b)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add {@code expr}'s name to {@code names} when it is a bare variable.
     *
     * <p>Intentionally non-recursive; see the class-level note.</p>
     */
    private static void operandNames(Expression expr, Set<String> names) {
        if (expr instanceof VariableExpr ve) {
            names.add(ve.getName().getLexeme());
        }
    }

    /**
     * The variable name of {@code expr}, or {@code null} if it is not a bare
     * variable reference.
     */
    public static String simpleName(Expression expr) {
        return expr instanceof VariableExpr ve ? ve.getName().getLexeme() : null;
    }
}
