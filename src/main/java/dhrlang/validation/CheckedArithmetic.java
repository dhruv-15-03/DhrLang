package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.ErrorCode;
import dhrlang.error.ErrorReporter;
import dhrlang.types.BlockchainTypes;

import java.util.*;

/**
 * Validates arithmetic operations on blockchain integer types for potential
 * overflow and underflow conditions.
 *
 * <p>By default, all arithmetic on {@code uint256} and {@code int256} types
 * is checked. Operations that could overflow or underflow are flagged.
 *
 * <p>Error codes:
 * <ul>
 *   <li>DHR-E542 – potential arithmetic overflow on uint256/int256</li>
 *   <li>DHR-E543 – potential arithmetic underflow on uint256/int256</li>
 *   <li>DHR-E544 – potential division by zero</li>
 * </ul>
 */
public class CheckedArithmetic {

    /**
     * Represents a detected arithmetic issue.
     */
    public static class ArithmeticIssue {
        private final ErrorCode errorCode;
        private final String message;
        private final String operator;
        private final String typeName;
        private final String functionName;

        public ArithmeticIssue(ErrorCode errorCode, String message,
                               String operator, String typeName, String functionName) {
            this.errorCode = errorCode;
            this.message = message;
            this.operator = operator;
            this.typeName = typeName;
            this.functionName = functionName;
        }

        public ErrorCode getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public String getOperator() { return operator; }
        public String getTypeName() { return typeName; }
        public String getFunctionName() { return functionName; }

        @Override
        public String toString() {
            return "[" + errorCode.getCode() + "] " + message;
        }
    }

    private final ErrorReporter errorReporter;
    private final List<ArithmeticIssue> issues;

    /** Set of type names that require checked arithmetic. */
    private static final Set<String> CHECKED_TYPES = Set.of(
            BlockchainTypes.UINT256,
            BlockchainTypes.INT256,
            BlockchainTypes.WEI
    );

    /** Operators that can overflow. */
    private static final Set<String> OVERFLOW_OPS = Set.of("+", "*", "**");

    /** Operators that can underflow. */
    private static final Set<String> UNDERFLOW_OPS = Set.of("-");

    /** Operators that can divide by zero. */
    private static final Set<String> DIVISION_OPS = Set.of("/", "%");

    public CheckedArithmetic() {
        this(null);
    }

    public CheckedArithmetic(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
        this.issues = new ArrayList<>();
    }

    /**
     * Analyze the entire program for unchecked arithmetic.
     */
    public void analyze(Program program) {
        issues.clear();
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
        // Build a map of field name → type for blockchain-typed fields
        Map<String, String> fieldTypes = new HashMap<>();
        for (VarDecl field : contract.getVariables()) {
            String type = field.getType();
            if (isCheckedType(type)) {
                fieldTypes.put(field.getName(), type);
            }
        }

        // Analyze each function
        for (FunctionDecl method : contract.getFunctions()) {
            if (method.getBody() == null) continue;
            analyzeFunction(method, fieldTypes);
        }
    }

    /**
     * Analyze a single function for arithmetic on checked types.
     */
    private void analyzeFunction(FunctionDecl method, Map<String, String> fieldTypes) {
        // Also collect parameter types
        Map<String, String> localTypes = new HashMap<>(fieldTypes);
        for (VarDecl param : method.getParameters()) {
            if (isCheckedType(param.getType())) {
                localTypes.put(param.getName(), param.getType());
            }
        }

        // Walk the body
        analyzeBlock(method.getBody(), localTypes, method.getName());
    }

    private void analyzeBlock(Block block, Map<String, String> types, String functionName) {
        if (block == null || block.getStatements() == null) return;
        for (Statement stmt : block.getStatements()) {
            analyzeStatement(stmt, types, functionName);
        }
    }

    private void analyzeStatement(Statement stmt, Map<String, String> types, String functionName) {
        if (stmt instanceof ExpressionStmt exprStmt) {
            analyzeExpression(exprStmt.getExpression(), types, functionName);
        } else if (stmt instanceof VarDecl varDecl) {
            if (isCheckedType(varDecl.getType())) {
                types.put(varDecl.getName(), varDecl.getType());
            }
            if (varDecl.getInitializer() != null) {
                analyzeExpression(varDecl.getInitializer(), types, functionName);
            }
        } else if (stmt instanceof IfStmt ifStmt) {
            analyzeExpression(ifStmt.getCondition(), types, functionName);
            analyzeStatement(ifStmt.getThenBranch(), types, functionName);
            if (ifStmt.getElseBranch() != null) {
                analyzeStatement(ifStmt.getElseBranch(), types, functionName);
            }
        } else if (stmt instanceof WhileStmt whileStmt) {
            analyzeExpression(whileStmt.getCondition(), types, functionName);
            analyzeStatement(whileStmt.getBody(), types, functionName);
        } else if (stmt instanceof Block block) {
            analyzeBlock(block, types, functionName);
        } else if (stmt instanceof ReturnStmt returnStmt) {
            if (returnStmt.getValue() != null) {
                analyzeExpression(returnStmt.getValue(), types, functionName);
            }
        } else if (stmt instanceof TryStmt tryStmt) {
            analyzeStatement(tryStmt.getTryBlock(), types, functionName);
            for (CatchClause cc : tryStmt.getCatchClauses()) {
                analyzeStatement(cc.getBody(), types, functionName);
            }
        }
    }

    /**
     * The core: check BinaryExpr nodes for checked-type arithmetic.
     */
    private void analyzeExpression(Expression expr, Map<String, String> types, String functionName) {
        if (expr instanceof BinaryExpr bin) {
            String op = bin.getOperator().getLexeme();
            String leftType = inferType(bin.getLeft(), types);
            String rightType = inferType(bin.getRight(), types);

            // If either operand is a checked type and the operator is arithmetic
            if (leftType != null && isCheckedType(leftType)) {
                checkArithmeticOp(op, leftType, functionName);
            } else if (rightType != null && isCheckedType(rightType)) {
                checkArithmeticOp(op, rightType, functionName);
            }

            // Recurse into sub-expressions
            analyzeExpression(bin.getLeft(), types, functionName);
            analyzeExpression(bin.getRight(), types, functionName);

        } else if (expr instanceof AssignmentExpr assign) {
            analyzeExpression(assign.getValue(), types, functionName);
        } else if (expr instanceof SetExpr set) {
            analyzeExpression(set.getValue(), types, functionName);
        } else if (expr instanceof CallExpr call) {
            for (Expression arg : call.getArguments()) {
                analyzeExpression(arg, types, functionName);
            }
        } else if (expr instanceof UnaryExpr unary) {
            analyzeExpression(unary.getRight(), types, functionName);
        } else if (expr instanceof PostfixIncrementExpr postfix) {
            String type = inferType(postfix.getTarget(), types);
            if (type != null && isCheckedType(type)) {
                if (postfix.isIncrement()) {
                    addIssue(ErrorCode.ARITHMETIC_OVERFLOW,
                            "Increment (++) on " + type + " in function '" + functionName + "' may overflow",
                            "++", type, functionName);
                } else {
                    addIssue(ErrorCode.ARITHMETIC_UNDERFLOW,
                            "Decrement (--) on " + type + " in function '" + functionName + "' may underflow",
                            "--", type, functionName);
                }
            }
        } else if (expr instanceof PrefixIncrementExpr prefix) {
            String type = inferType(prefix.getTarget(), types);
            if (type != null && isCheckedType(type)) {
                if (prefix.isIncrement()) {
                    addIssue(ErrorCode.ARITHMETIC_OVERFLOW,
                            "Increment (++) on " + type + " in function '" + functionName + "' may overflow",
                            "++", type, functionName);
                } else {
                    addIssue(ErrorCode.ARITHMETIC_UNDERFLOW,
                            "Decrement (--) on " + type + " in function '" + functionName + "' may underflow",
                            "--", type, functionName);
                }
            }
        }
    }

    /**
     * Flag the operator if it's an arithmetic operation.
     */
    private void checkArithmeticOp(String op, String type, String functionName) {
        if (OVERFLOW_OPS.contains(op)) {
            addIssue(ErrorCode.ARITHMETIC_OVERFLOW,
                    "Operator '" + op + "' on " + type + " in function '" + functionName
                            + "' may overflow — use checked arithmetic",
                    op, type, functionName);
        }
        if (UNDERFLOW_OPS.contains(op)) {
            addIssue(ErrorCode.ARITHMETIC_UNDERFLOW,
                    "Operator '" + op + "' on " + type + " in function '" + functionName
                            + "' may underflow — use checked arithmetic",
                    op, type, functionName);
        }
        if (DIVISION_OPS.contains(op)) {
            addIssue(ErrorCode.UNCHECKED_DIVISION,
                    "Operator '" + op + "' on " + type + " in function '" + functionName
                            + "' may divide by zero — add a check",
                    op, type, functionName);
        }
    }

    // ── Type inference (simple, from known field/variable names) ─────────

    /**
     * Infer the type of an expression from known variable/field types.
     */
    private String inferType(Expression expr, Map<String, String> types) {
        if (expr instanceof VariableExpr varExpr) {
            return types.get(varExpr.getName().getLexeme());
        }
        if (expr instanceof GetExpr get && get.getObject() instanceof ThisExpr) {
            return types.get(get.getName().getLexeme());
        }
        return null;
    }

    /**
     * Check if a type name requires checked arithmetic.
     */
    public static boolean isCheckedType(String type) {
        return type != null && CHECKED_TYPES.contains(type);
    }

    private void addIssue(ErrorCode code, String message, String op, String type, String fn) {
        ArithmeticIssue issue = new ArithmeticIssue(code, message, op, type, fn);
        issues.add(issue);
        if (errorReporter != null) {
            errorReporter.error(null, "[" + code.getCode() + "] " + message, null);
        }
    }

    // ── Query methods ────────────────────────────────────────────────────

    public List<ArithmeticIssue> getIssues() { return new ArrayList<>(issues); }
    public int getIssueCount() { return issues.size(); }
    public boolean hasIssues() { return !issues.isEmpty(); }

    public boolean hasIssue(ErrorCode code) {
        return issues.stream().anyMatch(i -> i.getErrorCode() == code);
    }

    public boolean hasIssue(String errorCode) {
        return issues.stream().anyMatch(i -> i.getErrorCode().getCode().equals(errorCode));
    }
}
