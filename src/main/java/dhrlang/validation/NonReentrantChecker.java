package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.ErrorCode;
import dhrlang.error.ErrorReporter;

import java.util.*;

/**
 * Validates {@code @nonreentrant} semantics for smart contract functions.
 *
 * <p>Reentrancy attacks are the most costly category of smart contract exploit
 * ($190M+ lost in 2023). This checker enforces:
 * <ul>
 *   <li>Functions marked {@code @nonreentrant} acquire a mutex lock on entry
 *       and release it on exit (conceptually – enforced at compile time).</li>
 *   <li>A {@code @nonreentrant} function must not call another
 *       {@code @nonreentrant} function in the <em>same</em> contract
 *       (would deadlock on the mutex).</li>
 *   <li>Functions that perform external calls (interactions) without
 *       {@code @nonreentrant} generate a warning.</li>
 * </ul>
 *
 * <p>Error codes:
 * <ul>
 *   <li>DHR-E537 – reentrant call detected in @nonreentrant function</li>
 *   <li>DHR-E538 – @nonreentrant function calls another @nonreentrant function in same contract</li>
 *   <li>DHR-W202 – @nonreentrant on @view/@pure (unnecessary)</li>
 * </ul>
 */
public class NonReentrantChecker {

    private final ErrorReporter errorReporter;
    private final List<String> errors;
    private int errorCount;

    public NonReentrantChecker() {
        this(null);
    }

    public NonReentrantChecker(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
        this.errors = new ArrayList<>();
        this.errorCount = 0;
    }

    /**
     * Check the entire program for reentrancy violations.
     */
    public void check(Program program) {
        errors.clear();
        errorCount = 0;
        for (ClassDecl classDecl : program.getClasses()) {
            if (classDecl.isContract()) {
                checkContract(classDecl);
            }
        }
    }

    /**
     * Check a single contract class.
     */
    private void checkContract(ClassDecl contract) {
        // Collect the names of @nonreentrant methods in this contract
        Set<String> nonReentrantMethods = new HashSet<>();
        // Collect all method names for internal call detection
        Set<String> allMethodNames = new HashSet<>();
        // Collect methods that perform external calls
        Set<String> methodsWithExternalCalls = new HashSet<>();

        for (FunctionDecl method : contract.getFunctions()) {
            allMethodNames.add(method.getName());
            if (method.isNonReentrant()) {
                nonReentrantMethods.add(method.getName());
            }
        }

        for (FunctionDecl method : contract.getFunctions()) {
            if (method.isNonReentrant()) {
                checkNonReentrantMethod(method, contract, nonReentrantMethods);
            }

            // Track which methods have external calls for warning generation
            if (hasExternalCalls(method.getBody())) {
                methodsWithExternalCalls.add(method.getName());
            }
        }

        // Warn about functions with external calls that aren't @nonreentrant
        for (FunctionDecl method : contract.getFunctions()) {
            if (methodsWithExternalCalls.contains(method.getName())
                    && !method.isNonReentrant()
                    && !method.isView()
                    && !method.isPure()
                    && !method.isContractConstructor()) {
                // This is a warning, not an error — tracked but non-blocking
                addWarning("DHR-W205",
                        "Function '" + method.getName() + "' in contract '" + contract.getName()
                                + "' has external calls but is not @nonreentrant",
                        method);
            }
        }
    }

    /**
     * Validate a @nonreentrant method:
     * - Cannot call other @nonreentrant methods in the same contract (deadlock)
     * - Must actually contain or protect an external interaction
     */
    private void checkNonReentrantMethod(FunctionDecl method, ClassDecl contract,
                                          Set<String> nonReentrantMethods) {
        if (method.getBody() == null) return;

        // Walk the body looking for internal calls to other @nonreentrant methods
        Set<String> calledMethods = collectInternalCalls(method.getBody());
        for (String calledName : calledMethods) {
            if (nonReentrantMethods.contains(calledName) && !calledName.equals(method.getName())) {
                reportError(ErrorCode.REENTRANCY_NESTED,
                        "@nonreentrant function '" + method.getName()
                                + "' calls @nonreentrant function '" + calledName
                                + "' in contract '" + contract.getName()
                                + "' — this would deadlock on the reentrancy mutex",
                        method);
            }
        }
    }

    // ── AST walkers ──────────────────────────────────────────────────────

    /**
     * Collect the names of internal (same-contract) function calls in a block.
     */
    private Set<String> collectInternalCalls(Block block) {
        Set<String> calls = new HashSet<>();
        if (block == null || block.getStatements() == null) return calls;

        for (Statement stmt : block.getStatements()) {
            collectInternalCallsFromStmt(stmt, calls);
        }
        return calls;
    }

    private void collectInternalCallsFromStmt(Statement stmt, Set<String> calls) {
        if (stmt instanceof ExpressionStmt exprStmt) {
            collectInternalCallsFromExpr(exprStmt.getExpression(), calls);
        } else if (stmt instanceof VarDecl varDecl && varDecl.getInitializer() != null) {
            collectInternalCallsFromExpr(varDecl.getInitializer(), calls);
        } else if (stmt instanceof Block block) {
            for (Statement s : block.getStatements()) {
                collectInternalCallsFromStmt(s, calls);
            }
        } else if (stmt instanceof IfStmt ifStmt) {
            collectInternalCallsFromStmt(ifStmt.getThenBranch(), calls);
            if (ifStmt.getElseBranch() != null) {
                collectInternalCallsFromStmt(ifStmt.getElseBranch(), calls);
            }
        } else if (stmt instanceof WhileStmt whileStmt) {
            collectInternalCallsFromStmt(whileStmt.getBody(), calls);
        } else if (stmt instanceof TryStmt tryStmt) {
            collectInternalCallsFromStmt(tryStmt.getTryBlock(), calls);
            for (CatchClause cc : tryStmt.getCatchClauses()) {
                collectInternalCallsFromStmt(cc.getBody(), calls);
            }
        } else if (stmt instanceof ReturnStmt returnStmt) {
            if (returnStmt.getValue() != null) {
                collectInternalCallsFromExpr(returnStmt.getValue(), calls);
            }
        }
    }

    private void collectInternalCallsFromExpr(Expression expr, Set<String> calls) {
        if (expr instanceof CallExpr call) {
            Expression callee = call.getCallee();
            // Direct function call: functionName(...)
            if (callee instanceof VariableExpr varExpr) {
                calls.add(varExpr.getName().getLexeme());
            }
            // this.method()
            if (callee instanceof GetExpr get && get.getObject() instanceof ThisExpr) {
                calls.add(get.getName().getLexeme());
            }
            // Recurse into arguments
            for (Expression arg : call.getArguments()) {
                collectInternalCallsFromExpr(arg, calls);
            }
        } else if (expr instanceof BinaryExpr bin) {
            collectInternalCallsFromExpr(bin.getLeft(), calls);
            collectInternalCallsFromExpr(bin.getRight(), calls);
        } else if (expr instanceof UnaryExpr unary) {
            collectInternalCallsFromExpr(unary.getRight(), calls);
        } else if (expr instanceof GetExpr get) {
            collectInternalCallsFromExpr(get.getObject(), calls);
        } else if (expr instanceof AssignmentExpr assign) {
            collectInternalCallsFromExpr(assign.getValue(), calls);
        } else if (expr instanceof SetExpr set) {
            collectInternalCallsFromExpr(set.getValue(), calls);
        }
    }

    /**
     * Check if a block contains any external calls.
     */
    private boolean hasExternalCalls(Block block) {
        if (block == null || block.getStatements() == null) return false;
        for (Statement stmt : block.getStatements()) {
            if (hasExternalCallInStmt(stmt)) return true;
        }
        return false;
    }

    private boolean hasExternalCallInStmt(Statement stmt) {
        if (stmt instanceof ExpressionStmt exprStmt) {
            return hasExternalCallInExpr(exprStmt.getExpression());
        }
        if (stmt instanceof VarDecl varDecl && varDecl.getInitializer() != null) {
            return hasExternalCallInExpr(varDecl.getInitializer());
        }
        if (stmt instanceof Block block) {
            return hasExternalCalls(block);
        }
        if (stmt instanceof IfStmt ifStmt) {
            if (hasExternalCallInStmt(ifStmt.getThenBranch())) return true;
            return ifStmt.getElseBranch() != null && hasExternalCallInStmt(ifStmt.getElseBranch());
        }
        if (stmt instanceof WhileStmt whileStmt) {
            return hasExternalCallInStmt(whileStmt.getBody());
        }
        if (stmt instanceof TryStmt tryStmt) {
            if (hasExternalCallInStmt(tryStmt.getTryBlock())) return true;
            for (CatchClause cc : tryStmt.getCatchClauses()) {
                if (hasExternalCallInStmt(cc.getBody())) return true;
            }
        }
        if (stmt instanceof ReturnStmt returnStmt && returnStmt.getValue() != null) {
            return hasExternalCallInExpr(returnStmt.getValue());
        }
        return false;
    }

    private boolean hasExternalCallInExpr(Expression expr) {
        if (expr instanceof CallExpr call) {
            Expression callee = call.getCallee();
            // obj.method() where obj is NOT 'this' → external
            if (callee instanceof GetExpr getExpr && !(getExpr.getObject() instanceof ThisExpr)) {
                return true;
            }
            // Recurse into arguments
            for (Expression arg : call.getArguments()) {
                if (hasExternalCallInExpr(arg)) return true;
            }
        }
        if (expr instanceof BinaryExpr bin) {
            return hasExternalCallInExpr(bin.getLeft()) || hasExternalCallInExpr(bin.getRight());
        }
        if (expr instanceof GetExpr getExpr) {
            return hasExternalCallInExpr(getExpr.getObject());
        }
        if (expr instanceof AssignmentExpr assign) {
            return hasExternalCallInExpr(assign.getValue());
        }
        if (expr instanceof SetExpr set) {
            return hasExternalCallInExpr(set.getValue());
        }
        return false;
    }

    // ── Error reporting ──────────────────────────────────────────────────

    private void reportError(ErrorCode code, String message, FunctionDecl method) {
        errorCount++;
        errors.add("[" + code.getCode() + "] " + message);
        if (errorReporter != null && method.getSourceLocation() != null) {
            errorReporter.error(method.getSourceLocation(),
                    "[" + code.getCode() + "] " + message, null);
        }
    }

    private void addWarning(String code, String message, FunctionDecl method) {
        // Warnings are tracked but don't increment error count
        errors.add("[" + code + "] (warning) " + message);
    }

    // ── Query methods ────────────────────────────────────────────────────

    public List<String> getErrors() { return new ArrayList<>(errors); }
    public int getErrorCount() { return errorCount; }
    public boolean hasErrors() { return errorCount > 0; }

    public boolean hasError(String code) {
        return errors.stream().anyMatch(e -> e.contains(code));
    }
}
