package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.ErrorCode;
import dhrlang.error.ErrorReporter;

import java.util.*;

/**
 * Validates access control patterns in smart contract functions.
 *
 * <p>Access control bugs caused $320M in losses in 2023. This checker
 * validates that functions with access restrictions are properly guarded:
 * <ul>
 *   <li>{@code @payable} functions should validate {@code msg.value}</li>
 *   <li>Functions accessing {@code msg.value} must be {@code @payable}</li>
 *   <li>General require() pattern analysis</li>
 * </ul>
 *
 * <p>Error codes:
 * <ul>
 *   <li>DHR-E550 – @payable function doesn't validate msg.value</li>
 *   <li>DHR-E551 – msg.value accessed in non-@payable function</li>
 * </ul>
 */
public class AccessControlChecker {

    /**
     * Represents a detected access control issue.
     */
    public static class AccessIssue {
        private final ErrorCode errorCode;
        private final String message;
        private final String functionName;
        private final String contractName;

        public AccessIssue(ErrorCode errorCode, String message,
                           String functionName, String contractName) {
            this.errorCode = errorCode;
            this.message = message;
            this.functionName = functionName;
            this.contractName = contractName;
        }

        public ErrorCode getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public String getFunctionName() { return functionName; }
        public String getContractName() { return contractName; }

        @Override
        public String toString() {
            return "[" + errorCode.getCode() + "] " + message;
        }
    }

    private final ErrorReporter errorReporter;
    private final List<AccessIssue> issues;

    public AccessControlChecker() {
        this(null);
    }

    public AccessControlChecker(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
        this.issues = new ArrayList<>();
    }

    /**
     * Analyze the entire program for access control issues.
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
        for (FunctionDecl method : contract.getFunctions()) {
            if (method.getBody() == null) continue;

            boolean isPayable = method.isPayable();
            boolean accessesMsgValue = findsMsgValue(method.getBody());
            boolean checksMsgValue = checksMsgValueCondition(method.getBody());

                // E550: @payable but neither reads nor checks msg.value
                // Acceptable patterns for @payable functions:
                //  - they read/use msg.value (e.g., assign to local and use)
                //  - they explicitly check msg.value (if / require)
                if (isPayable && !accessesMsgValue && !checksMsgValue && !method.isContractConstructor()) {
                addIssue(ErrorCode.PAYABLE_NO_VALUE_CHECK,
                        "@payable function '" + method.getName() + "' in contract '"
                                + contract.getName()
                                + "' does not validate msg.value — consider adding require(msg.value > 0)",
                        method.getName(), contract.getName());
            }

            // E551: accesses msg.value but not marked @payable
            if (!isPayable && accessesMsgValue) {
                addIssue(ErrorCode.NON_PAYABLE_VALUE_ACCESS,
                        "Function '" + method.getName() + "' in contract '"
                                + contract.getName()
                                + "' accesses msg.value but is not @payable — "
                                + "add @payable annotation or remove msg.value usage",
                        method.getName(), contract.getName());
            }
        }
    }

    // ── AST walkers for msg.value detection ──────────────────────────────

    /**
     * Check if a block contains any reference to msg.value.
     */
    private boolean findsMsgValue(Block block) {
        if (block == null || block.getStatements() == null) return false;
        for (Statement stmt : block.getStatements()) {
            if (findsMsgValueInStmt(stmt)) return true;
        }
        return false;
    }

    private boolean findsMsgValueInStmt(Statement stmt) {
        if (stmt instanceof ExpressionStmt exprStmt) {
            return findsMsgValueInExpr(exprStmt.getExpression());
        }
        if (stmt instanceof VarDecl varDecl && varDecl.getInitializer() != null) {
            return findsMsgValueInExpr(varDecl.getInitializer());
        }
        if (stmt instanceof IfStmt ifStmt) {
            if (findsMsgValueInExpr(ifStmt.getCondition())) return true;
            if (findsMsgValueInStmt(ifStmt.getThenBranch())) return true;
            return ifStmt.getElseBranch() != null && findsMsgValueInStmt(ifStmt.getElseBranch());
        }
        if (stmt instanceof WhileStmt whileStmt) {
            if (findsMsgValueInExpr(whileStmt.getCondition())) return true;
            return findsMsgValueInStmt(whileStmt.getBody());
        }
        if (stmt instanceof Block block) {
            return findsMsgValue(block);
        }
        if (stmt instanceof ReturnStmt returnStmt && returnStmt.getValue() != null) {
            return findsMsgValueInExpr(returnStmt.getValue());
        }
        if (stmt instanceof TryStmt tryStmt) {
            if (findsMsgValueInStmt(tryStmt.getTryBlock())) return true;
            for (CatchClause cc : tryStmt.getCatchClauses()) {
                if (findsMsgValueInStmt(cc.getBody())) return true;
            }
        }
        return false;
    }

    private boolean findsMsgValueInExpr(Expression expr) {
        if (isMsgValueExpr(expr)) return true;

        if (expr instanceof BinaryExpr bin) {
            return findsMsgValueInExpr(bin.getLeft()) || findsMsgValueInExpr(bin.getRight());
        }
        if (expr instanceof UnaryExpr unary) {
            return findsMsgValueInExpr(unary.getRight());
        }
        if (expr instanceof CallExpr call) {
            if (findsMsgValueInExpr(call.getCallee())) return true;
            for (Expression arg : call.getArguments()) {
                if (findsMsgValueInExpr(arg)) return true;
            }
        }
        if (expr instanceof AssignmentExpr assign) {
            return findsMsgValueInExpr(assign.getValue());
        }
        if (expr instanceof SetExpr set) {
            return findsMsgValueInExpr(set.getObject()) || findsMsgValueInExpr(set.getValue());
        }
        if (expr instanceof GetExpr get) {
            return isMsgValueExpr(expr) || findsMsgValueInExpr(get.getObject());
        }
        return false;
    }

    /**
     * Check if expr is specifically "msg.value".
     */
    private boolean isMsgValueExpr(Expression expr) {
        if (expr instanceof GetExpr get) {
            if ("value".equals(get.getName().getLexeme()) && get.getObject() instanceof VariableExpr var) {
                return "msg".equals(var.getName().getLexeme());
            }
        }
        return false;
    }

    /**
     * Check if a block contains a condition check on msg.value
     * (e.g., if (msg.value > 0) or require(msg.value > 0)).
     */
    private boolean checksMsgValueCondition(Block block) {
        if (block == null || block.getStatements() == null) return false;
        for (Statement stmt : block.getStatements()) {
            if (checksMsgValueInStmt(stmt)) return true;
        }
        return false;
    }

    private boolean checksMsgValueInStmt(Statement stmt) {
        // if (msg.value ...) { } 
        if (stmt instanceof IfStmt ifStmt) {
            if (findsMsgValueInExpr(ifStmt.getCondition())) return true;
        }
        // require(msg.value > 0)
        if (stmt instanceof ExpressionStmt exprStmt) {
            Expression expr = exprStmt.getExpression();
            if (expr instanceof CallExpr call) {
                Expression callee = call.getCallee();
                if (callee instanceof VariableExpr var && "require".equals(var.getName().getLexeme())) {
                    for (Expression arg : call.getArguments()) {
                        if (findsMsgValueInExpr(arg)) return true;
                    }
                }
            }
        }
        // Recurse into blocks
        if (stmt instanceof Block block) {
            return checksMsgValueCondition(block);
        }
        return false;
    }

    // ── Error reporting ──────────────────────────────────────────────────

    private void addIssue(ErrorCode code, String message, String functionName, String contractName) {
        AccessIssue issue = new AccessIssue(code, message, functionName, contractName);
        issues.add(issue);
        if (errorReporter != null) {
            errorReporter.error(null, "[" + code.getCode() + "] " + message, null);
        }
    }

    // ── Query methods ────────────────────────────────────────────────────

    public List<AccessIssue> getIssues() { return new ArrayList<>(issues); }
    public int getIssueCount() { return issues.size(); }
    public boolean hasIssues() { return !issues.isEmpty(); }

    public boolean hasIssue(ErrorCode code) {
        return issues.stream().anyMatch(i -> i.getErrorCode() == code);
    }

    public boolean hasIssue(String errorCode) {
        return issues.stream().anyMatch(i -> i.getErrorCode().getCode().equals(errorCode));
    }
}
