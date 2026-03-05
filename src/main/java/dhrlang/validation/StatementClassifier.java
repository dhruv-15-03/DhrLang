package dhrlang.validation;

import dhrlang.ast.*;

import java.util.*;

/**
 * Classifies statements in smart contract functions according to the
 * Checks-Effects-Interactions (CEI) pattern.
 *
 * <p>In secure contract design, function bodies should follow this order:
 * <ol>
 *   <li><b>Checks</b> – validate inputs and preconditions (require, if-guards, comparisons)</li>
 *   <li><b>Effects</b> – modify contract state (@storage field assignments)</li>
 *   <li><b>Interactions</b> – call external contracts or transfer funds</li>
 * </ol>
 *
 * <p>This classifier inspects each statement and labels it so that
 * {@link EffectOrderingAnalyzer} can verify correct ordering.
 */
public class StatementClassifier {

    /**
     * The three CEI categories plus a neutral "NONE" for non-classifiable statements.
     */
    public enum Category {
        /** Precondition validation: require(), if-guards, comparisons */
        CHECK,
        /** State modification: @storage field assignment, field increment */
        EFFECT,
        /** External call or value transfer: call on non-this object */
        INTERACTION,
        /** Neutral: local variable declarations, return, break, etc. */
        NONE
    }

    /**
     * A classified statement: pairs a statement with its CEI category.
     */
    public static class ClassifiedStatement {
        private final Statement statement;
        private final Category category;
        private final String reason;

        public ClassifiedStatement(Statement statement, Category category, String reason) {
            this.statement = statement;
            this.category = category;
            this.reason = reason;
        }

        public Statement getStatement() { return statement; }
        public Category getCategory() { return category; }
        public String getReason() { return reason; }

        @Override
        public String toString() {
            return category + ": " + reason;
        }
    }

    private final Set<String> storageFieldNames;
    private final Set<String> contractMethodNames;

    /**
     * Create a classifier with knowledge of which fields are @storage
     * and which methods belong to the current contract.
     *
     * @param storageFieldNames names of @storage fields in the contract
     * @param contractMethodNames names of methods declared on the contract
     */
    public StatementClassifier(Set<String> storageFieldNames, Set<String> contractMethodNames) {
        this.storageFieldNames = storageFieldNames != null ? storageFieldNames : Collections.emptySet();
        this.contractMethodNames = contractMethodNames != null ? contractMethodNames : Collections.emptySet();
    }

    /**
     * Classify a list of statements (typically a function body).
     */
    public List<ClassifiedStatement> classify(List<Statement> statements) {
        List<ClassifiedStatement> result = new ArrayList<>();
        for (Statement stmt : statements) {
            result.add(classifyStatement(stmt));
        }
        return result;
    }

    /**
     * Classify a single statement.
     */
    public ClassifiedStatement classifyStatement(Statement stmt) {
        if (stmt instanceof ExpressionStmt exprStmt) {
            return classifyExpression(exprStmt, exprStmt.getExpression());
        }
        if (stmt instanceof IfStmt) {
            return new ClassifiedStatement(stmt, Category.CHECK, "if-guard / condition check");
        }
        if (stmt instanceof VarDecl varDecl) {
            return classifyVarDecl(varDecl);
        }
        if (stmt instanceof Block block) {
            // A bare block inherits the "worst" category from its children
            return classifyBlock(stmt, block.getStatements());
        }
        if (stmt instanceof ReturnStmt || stmt instanceof BreakStmt || stmt instanceof ContinueStmt) {
            return new ClassifiedStatement(stmt, Category.NONE, "control flow");
        }
        if (stmt instanceof WhileStmt) {
            return new ClassifiedStatement(stmt, Category.CHECK, "loop guard");
        }
        if (stmt instanceof ThrowStmt) {
            return new ClassifiedStatement(stmt, Category.CHECK, "throw / revert");
        }
        if (stmt instanceof TryStmt) {
            return new ClassifiedStatement(stmt, Category.INTERACTION, "try-catch wraps possible interaction");
        }
        return new ClassifiedStatement(stmt, Category.NONE, "unclassified statement");
    }

    // ── Expression-based classification ──────────────────────────────────

    private ClassifiedStatement classifyExpression(Statement stmt, Expression expr) {
        // Assignment to a @storage field → EFFECT
        if (expr instanceof AssignmentExpr assign) {
            String name = assign.getName().getLexeme();
            if (isStorageTarget(name)) {
                return new ClassifiedStatement(stmt, Category.EFFECT,
                        "assignment to @storage field '" + name + "'");
            }
        }

        // SetExpr: this.field = ... or object.field = ... 
        if (expr instanceof SetExpr set) {
            if (isStorageFieldSet(set)) {
                return new ClassifiedStatement(stmt, Category.EFFECT,
                        "assignment to @storage field via set expression");
            }
        }

        // Postfix/Prefix increment on @storage
        if (expr instanceof PostfixIncrementExpr postfix) {
            if (isStorageExpression(postfix.getTarget())) {
                return new ClassifiedStatement(stmt, Category.EFFECT,
                        "increment/decrement of @storage field");
            }
        }
        if (expr instanceof PrefixIncrementExpr prefix) {
            if (isStorageExpression(prefix.getTarget())) {
                return new ClassifiedStatement(stmt, Category.EFFECT,
                        "increment/decrement of @storage field");
            }
        }

        // Call expression – the most important for interaction detection
        if (expr instanceof CallExpr call) {
            return classifyCall(stmt, call);
        }

        // Default: expression with no side-effect category
        return new ClassifiedStatement(stmt, Category.NONE, "expression");
    }

    /**
     * Classify a function call as INTERACTION (external) or NONE (internal/local).
     */
    private ClassifiedStatement classifyCall(Statement stmt, CallExpr call) {
        Expression callee = call.getCallee();

        // obj.method() where obj is NOT 'this' → external INTERACTION
        if (callee instanceof GetExpr get) {
            Expression object = get.getObject();

            // this.method() → internal call, not an interaction
            if (object instanceof ThisExpr) {
                String methodName = get.getName().getLexeme();
                // Check if it's a known internal method
                if (contractMethodNames.contains(methodName)) {
                    return new ClassifiedStatement(stmt, Category.NONE,
                            "internal call to '" + methodName + "'");
                }
            }

            // external_contract.transfer(), token.send(), etc.
            return new ClassifiedStatement(stmt, Category.INTERACTION,
                    "external call via '" + get.getName().getLexeme() + "'");
        }

        // Direct function call – could be internal or a built-in
        if (callee instanceof VariableExpr varExpr) {
            String name = varExpr.getName().getLexeme();
            // "require" is a CHECK
            if ("require".equals(name)) {
                return new ClassifiedStatement(stmt, Category.CHECK, "require() precondition");
            }
            // Known contract method → internal
            if (contractMethodNames.contains(name)) {
                return new ClassifiedStatement(stmt, Category.NONE,
                        "internal call to '" + name + "'");
            }
        }

        return new ClassifiedStatement(stmt, Category.NONE, "function call");
    }

    // ── VarDecl classification ───────────────────────────────────────────

    private ClassifiedStatement classifyVarDecl(VarDecl varDecl) {
        // If it has a @storage annotation, this is an EFFECT (initialization)
        if (varDecl.isStorage()) {
            return new ClassifiedStatement(varDecl, Category.EFFECT,
                    "@storage variable declaration");
        }
        // If the initializer is an external call, mark as INTERACTION
        if (varDecl.getInitializer() != null && isExternalCall(varDecl.getInitializer())) {
            return new ClassifiedStatement(varDecl, Category.INTERACTION,
                    "variable initialized from external call");
        }
        return new ClassifiedStatement(varDecl, Category.NONE, "local variable declaration");
    }

    // ── Block classification ─────────────────────────────────────────────

    private ClassifiedStatement classifyBlock(Statement original, List<Statement> stmts) {
        Category worst = Category.NONE;
        for (Statement s : stmts) {
            ClassifiedStatement cs = classifyStatement(s);
            if (cs.getCategory().ordinal() > worst.ordinal()) {
                worst = cs.getCategory();
            }
        }
        return new ClassifiedStatement(original, worst, "block with " + worst + " content");
    }

    // ── Helper methods ───────────────────────────────────────────────────

    private boolean isStorageTarget(String name) {
        return storageFieldNames.contains(name);
    }

    private boolean isStorageFieldSet(SetExpr set) {
        Expression obj = set.getObject();
        // this.storageField = ...
        if (obj instanceof ThisExpr) {
            return storageFieldNames.contains(set.getName().getLexeme());
        }
        // Direct name reference that's a storage field
        if (obj instanceof VariableExpr varExpr) {
            return storageFieldNames.contains(varExpr.getName().getLexeme());
        }
        return false;
    }

    private boolean isStorageExpression(Expression expr) {
        if (expr instanceof VariableExpr varExpr) {
            return storageFieldNames.contains(varExpr.getName().getLexeme());
        }
        if (expr instanceof GetExpr get && get.getObject() instanceof ThisExpr) {
            return storageFieldNames.contains(get.getName().getLexeme());
        }
        return false;
    }

    private boolean isExternalCall(Expression expr) {
        if (expr instanceof CallExpr call) {
            Expression callee = call.getCallee();
            if (callee instanceof GetExpr get) {
                return !(get.getObject() instanceof ThisExpr);
            }
        }
        return false;
    }
}
