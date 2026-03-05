package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.ErrorCode;
import dhrlang.error.ErrorReporter;
import dhrlang.validation.StatementClassifier.Category;
import dhrlang.validation.StatementClassifier.ClassifiedStatement;

import java.util.*;

/**
 * Enforces the Checks-Effects-Interactions (CEI) pattern in smart contract functions.
 *
 * <p>The CEI pattern is the primary defense against reentrancy attacks:
 * <ol>
 *   <li><b>Checks</b> – validate all preconditions first</li>
 *   <li><b>Effects</b> – modify state second</li>
 *   <li><b>Interactions</b> – call external contracts last</li>
 * </ol>
 *
 * <p>Violations detected:
 * <ul>
 *   <li>DHR-E539 – generic CEI pattern violation</li>
 *   <li>DHR-E540 – interaction (external call) before effect (state change)</li>
 *   <li>DHR-E541 – check after effect</li>
 * </ul>
 *
 * <p>This analyzer uses {@link StatementClassifier} to categorize each
 * statement, then verifies they appear in the correct order.
 */
public class EffectOrderingAnalyzer {

    /**
     * Records a single ordering violation.
     */
    public static class Violation {
        private final ErrorCode errorCode;
        private final String message;
        private final String functionName;
        private final Category offendingCategory;
        private final Category expectedAfter;

        public Violation(ErrorCode errorCode, String message, String functionName,
                         Category offendingCategory, Category expectedAfter) {
            this.errorCode = errorCode;
            this.message = message;
            this.functionName = functionName;
            this.offendingCategory = offendingCategory;
            this.expectedAfter = expectedAfter;
        }

        public ErrorCode getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public String getFunctionName() { return functionName; }
        public Category getOffendingCategory() { return offendingCategory; }
        public Category getExpectedAfter() { return expectedAfter; }

        @Override
        public String toString() {
            return "[" + errorCode.getCode() + "] " + message;
        }
    }

    private final ErrorReporter errorReporter;
    private final List<Violation> violations;

    public EffectOrderingAnalyzer() {
        this(null);
    }

    public EffectOrderingAnalyzer(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
        this.violations = new ArrayList<>();
    }

    /**
     * Analyze the entire program for CEI violations.
     */
    public void analyze(Program program) {
        violations.clear();
        for (ClassDecl classDecl : program.getClasses()) {
            if (classDecl.isContract()) {
                analyzeContract(classDecl);
            }
        }
    }

    /**
     * Analyze a single contract.
     */
    private void analyzeContract(ClassDecl contract) {
        // Collect @storage field names
        Set<String> storageFields = new HashSet<>();
        for (VarDecl field : contract.getVariables()) {
            if (field.isStorage()) {
                storageFields.add(field.getName());
            }
        }

        // Collect method names
        Set<String> methodNames = new HashSet<>();
        for (FunctionDecl method : contract.getFunctions()) {
            methodNames.add(method.getName());
        }

        StatementClassifier classifier = new StatementClassifier(storageFields, methodNames);

        // Analyze each function (skip @pure and @view, they have no state effects)
        for (FunctionDecl method : contract.getFunctions()) {
            if (method.isPure() || method.isView()) continue;
            if (method.getBody() == null) continue;
            analyzeFunction(method, classifier, contract.getName());
        }
    }

    /**
     * Analyze a single function for CEI ordering.
     *
     * <p>The algorithm:
     * <ol>
     *   <li>Classify each top-level statement</li>
     *   <li>Track the "phase" (CHECK → EFFECT → INTERACTION)</li>
     *   <li>Report violations when a statement appears out of order</li>
     * </ol>
     */
    private void analyzeFunction(FunctionDecl method, StatementClassifier classifier,
                                  String contractName) {
        if (method.getBody().getStatements() == null) return;

        List<ClassifiedStatement> classified =
                classifier.classify(method.getBody().getStatements());

        // Track the highest phase reached so far
        // Phase order: CHECK(0) < EFFECT(1) < INTERACTION(2)
        Category highestPhase = Category.NONE;

        for (ClassifiedStatement cs : classified) {
            Category current = cs.getCategory();
            if (current == Category.NONE) continue;

            switch (current) {
                case CHECK:
                    // A CHECK after an EFFECT → violation (E541)
                    if (highestPhase == Category.EFFECT || highestPhase == Category.INTERACTION) {
                        addViolation(ErrorCode.CHECK_AFTER_EFFECT,
                                "In function '" + method.getName() + "' of contract '"
                                        + contractName + "': condition check after state modification — "
                                        + "move all checks before effects. " + cs.getReason(),
                                method.getName(), Category.CHECK, highestPhase);
                    }
                    break;

                case EFFECT:
                    // An EFFECT after an INTERACTION → violation (E540, but inverted)
                    if (highestPhase == Category.INTERACTION) {
                        addViolation(ErrorCode.INTERACTION_BEFORE_EFFECT,
                                "In function '" + method.getName() + "' of contract '"
                                        + contractName + "': state modification after external call — "
                                        + "move all effects before interactions. " + cs.getReason(),
                                method.getName(), Category.EFFECT, Category.INTERACTION);
                    }
                    if (highestPhase.ordinal() < Category.EFFECT.ordinal()) {
                        highestPhase = Category.EFFECT;
                    }
                    break;

                case INTERACTION:
                    highestPhase = Category.INTERACTION;
                    break;
                    
                default:
                    break;
            }
        }
    }

    private void addViolation(ErrorCode code, String message, String functionName,
                               Category offending, Category expected) {
        Violation v = new Violation(code, message, functionName, offending, expected);
        violations.add(v);
        if (errorReporter != null) {
            errorReporter.error(null, "[" + code.getCode() + "] " + message, null);
        }
    }

    // ── Query methods ────────────────────────────────────────────────────

    public List<Violation> getViolations() { return new ArrayList<>(violations); }
    public int getViolationCount() { return violations.size(); }
    public boolean hasViolations() { return !violations.isEmpty(); }

    public boolean hasViolation(String errorCode) {
        return violations.stream().anyMatch(v -> v.getErrorCode().getCode().equals(errorCode));
    }

    public boolean hasViolation(ErrorCode errorCode) {
        return violations.stream().anyMatch(v -> v.getErrorCode() == errorCode);
    }
}
