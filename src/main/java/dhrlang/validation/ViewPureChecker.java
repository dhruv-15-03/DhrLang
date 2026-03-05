package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.ErrorCode;
import dhrlang.error.ErrorReporter;
import dhrlang.error.SourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Checks that {@code @view} and {@code @pure} annotated functions do not violate
 * their state-access constraints.
 * 
 * <p>Rules enforced:
 * <ul>
 *   <li>{@code @view} functions can read state but cannot write to {@code @storage} fields</li>
 *   <li>{@code @pure} functions cannot read or write {@code @storage} fields, or access
 *       {@code msg.sender}, {@code msg.value}, or {@code block} properties</li>
 * </ul>
 * 
 * <p>Error codes:
 * <ul>
 *   <li>DHR-E530: {@code @view} function modifies state</li>
 *   <li>DHR-E531: {@code @pure} function reads state</li>
 *   <li>DHR-E532: {@code @pure} function modifies state</li>
 *   <li>DHR-E536: {@code @immutable} field assigned outside constructor</li>
 * </ul>
 */
public class ViewPureChecker {
    
    private final ErrorReporter errorReporter;
    private int errorCount;
    
    public ViewPureChecker() {
        this(null);
    }
    
    public ViewPureChecker(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
        this.errorCount = 0;
    }
    
    /**
     * Check all contracts in the program for view/pure violations.
     */
    public void check(Program program) {
        errorCount = 0;
        for (ClassDecl classDecl : program.getClasses()) {
            if (classDecl.isContract()) {
                checkContract(classDecl);
            }
        }
    }
    
    /**
     * Check a single contract class for view/pure violations.
     */
    private void checkContract(ClassDecl contract) {
        // Collect @storage and @immutable field names
        Set<String> storageFields = new HashSet<>();
        Set<String> immutableFields = new HashSet<>();
        
        for (VarDecl field : contract.getVariables()) {
            if (field.isStorage()) {
                storageFields.add(field.getName());
            }
            if (field.isImmutable()) {
                immutableFields.add(field.getName());
            }
        }
        
        // Check each function
        for (FunctionDecl method : contract.getFunctions()) {
            if (method.isView()) {
                checkViewFunction(method, storageFields, contract.getName());
            }
            if (method.isPure()) {
                checkPureFunction(method, storageFields, contract.getName());
            }
            // Check @immutable fields are only assigned in @constructor
            if (!method.isContractConstructor()) {
                checkImmutableAssignments(method, immutableFields, contract.getName());
            }
        }
    }
    
    /**
     * Check that a @view function does not modify @storage fields.
     */
    private void checkViewFunction(FunctionDecl method, Set<String> storageFields, String contractName) {
        if (method.getBody() == null) return;
        
        Set<String> modifications = new HashSet<>();
        collectStateModifications(method.getBody(), storageFields, modifications);
        
        for (String fieldName : modifications) {
            reportError("DHR-E530",
                    "@view method '" + method.getName() + "' modifies @storage field '" + fieldName + "'",
                    "Remove the state modification or remove @view annotation",
                    method.getSourceLocation());
        }
    }
    
    /**
     * Check that a @pure function does not access or modify @storage fields.
     */
    private void checkPureFunction(FunctionDecl method, Set<String> storageFields, String contractName) {
        if (method.getBody() == null) return;
        
        // Check for state modifications
        Set<String> modifications = new HashSet<>();
        collectStateModifications(method.getBody(), storageFields, modifications);
        
        for (String fieldName : modifications) {
            reportError("DHR-E532",
                    "@pure method '" + method.getName() + "' modifies @storage field '" + fieldName + "'",
                    "Remove the state modification or remove @pure annotation",
                    method.getSourceLocation());
        }
        
        // Check for state reads
        Set<String> reads = new HashSet<>();
        collectStateReads(method.getBody(), storageFields, reads);
        
        for (String fieldName : reads) {
            reportError("DHR-E531",
                    "@pure method '" + method.getName() + "' reads @storage field '" + fieldName + "'",
                    "Remove the state access or use @view instead of @pure",
                    method.getSourceLocation());
        }
        
        // Check for msg/block access
        if (accessesContextGlobal(method.getBody())) {
            reportError("DHR-E531",
                    "@pure method '" + method.getName() + "' accesses transaction context (msg/block)",
                    "@pure functions cannot access msg.sender, msg.value, or block properties",
                    method.getSourceLocation());
        }
    }
    
    /**
     * Check that @immutable fields are not assigned outside @constructor.
     */
    private void checkImmutableAssignments(FunctionDecl method, Set<String> immutableFields, String contractName) {
        if (method.getBody() == null || immutableFields.isEmpty()) return;
        
        Set<String> modifications = new HashSet<>();
        collectStateModifications(method.getBody(), immutableFields, modifications);
        
        for (String fieldName : modifications) {
            reportError("DHR-E536",
                    "@immutable field '" + fieldName + "' cannot be assigned in method '" + method.getName() + "'",
                    "@immutable fields can only be set in the @constructor",
                    method.getSourceLocation());
        }
    }
    
    /**
     * Collect field names from the target set that are modified (assigned to) in the given block.
     * Walks the AST recursively.
     */
    private void collectStateModifications(Statement stmt, Set<String> targetFields, Set<String> modifications) {
        if (stmt instanceof Block block) {
            for (Statement s : block.getStatements()) {
                collectStateModifications(s, targetFields, modifications);
            }
        } else if (stmt instanceof ExpressionStmt exprStmt) {
            collectExprModifications(exprStmt.getExpression(), targetFields, modifications);
        } else if (stmt instanceof IfStmt ifStmt) {
            if (ifStmt.getThenBranch() != null) {
                collectStateModifications(ifStmt.getThenBranch(), targetFields, modifications);
            }
            if (ifStmt.getElseBranch() != null) {
                collectStateModifications(ifStmt.getElseBranch(), targetFields, modifications);
            }
        } else if (stmt instanceof WhileStmt whileStmt) {
            if (whileStmt.getBody() != null) {
                collectStateModifications(whileStmt.getBody(), targetFields, modifications);
            }
        } else if (stmt instanceof TryStmt tryStmt) {
            if (tryStmt.getTryBlock() != null) {
                collectStateModifications(tryStmt.getTryBlock(), targetFields, modifications);
            }
            for (CatchClause cc : tryStmt.getCatchClauses()) {
                if (cc.getBody() != null) {
                    collectStateModifications(cc.getBody(), targetFields, modifications);
                }
            }
        }
    }
    
    /**
     * Check expression trees for assignments to target fields.
     */
    private void collectExprModifications(Expression expr, Set<String> targetFields, Set<String> modifications) {
        if (expr instanceof AssignmentExpr assign) {
            String name = assign.getName().getLexeme();
            if (targetFields.contains(name)) {
                modifications.add(name);
            }
        } else if (expr instanceof SetExpr set) {
            // this.field = value
            String fieldName = set.getName().getLexeme();
            if (targetFields.contains(fieldName) && set.getObject() instanceof ThisExpr) {
                modifications.add(fieldName);
            }
        }
    }
    
    /**
     * Collect field names from the target set that are read in the given block.
     */
    private void collectStateReads(Statement stmt, Set<String> targetFields, Set<String> reads) {
        if (stmt instanceof Block block) {
            for (Statement s : block.getStatements()) {
                collectStateReads(s, targetFields, reads);
            }
        } else if (stmt instanceof ExpressionStmt exprStmt) {
            collectExprReads(exprStmt.getExpression(), targetFields, reads);
        } else if (stmt instanceof ReturnStmt returnStmt) {
            if (returnStmt.getValue() != null) {
                collectExprReads(returnStmt.getValue(), targetFields, reads);
            }
        } else if (stmt instanceof IfStmt ifStmt) {
            collectExprReads(ifStmt.getCondition(), targetFields, reads);
            if (ifStmt.getThenBranch() != null) {
                collectStateReads(ifStmt.getThenBranch(), targetFields, reads);
            }
            if (ifStmt.getElseBranch() != null) {
                collectStateReads(ifStmt.getElseBranch(), targetFields, reads);
            }
        } else if (stmt instanceof WhileStmt whileStmt) {
            collectExprReads(whileStmt.getCondition(), targetFields, reads);
            if (whileStmt.getBody() != null) {
                collectStateReads(whileStmt.getBody(), targetFields, reads);
            }
        } else if (stmt instanceof TryStmt tryStmt) {
            if (tryStmt.getTryBlock() != null) {
                collectStateReads(tryStmt.getTryBlock(), targetFields, reads);
            }
            for (CatchClause cc : tryStmt.getCatchClauses()) {
                if (cc.getBody() != null) {
                    collectStateReads(cc.getBody(), targetFields, reads);
                }
            }
        } else if (stmt instanceof VarDecl varDecl) {
            if (varDecl.getInitializer() != null) {
                collectExprReads(varDecl.getInitializer(), targetFields, reads);
            }
        }
    }
    
    /**
     * Check expression trees for reads of target fields.
     */
    private void collectExprReads(Expression expr, Set<String> targetFields, Set<String> reads) {
        if (expr instanceof VariableExpr varExpr) {
            String name = varExpr.getName().getLexeme();
            if (targetFields.contains(name)) {
                reads.add(name);
            }
        } else if (expr instanceof GetExpr get) {
            // this.field
            String fieldName = get.getName().getLexeme();
            if (targetFields.contains(fieldName) && get.getObject() instanceof ThisExpr) {
                reads.add(fieldName);
            }
            collectExprReads(get.getObject(), targetFields, reads);
        } else if (expr instanceof BinaryExpr binary) {
            collectExprReads(binary.getLeft(), targetFields, reads);
            collectExprReads(binary.getRight(), targetFields, reads);
        } else if (expr instanceof UnaryExpr unary) {
            collectExprReads(unary.getRight(), targetFields, reads);
        } else if (expr instanceof CallExpr call) {
            collectExprReads(call.getCallee(), targetFields, reads);
            for (Expression arg : call.getArguments()) {
                collectExprReads(arg, targetFields, reads);
            }
        } else if (expr instanceof AssignmentExpr assign) {
            collectExprReads(assign.getValue(), targetFields, reads);
        } else if (expr instanceof SetExpr set) {
            collectExprReads(set.getValue(), targetFields, reads);
        }
    }
    
    /**
     * Check if a statement block accesses msg or block globals.
     */
    private boolean accessesContextGlobal(Statement stmt) {
        if (stmt instanceof Block block) {
            for (Statement s : block.getStatements()) {
                if (accessesContextGlobal(s)) return true;
            }
        } else if (stmt instanceof ExpressionStmt exprStmt) {
            return exprAccessesContextGlobal(exprStmt.getExpression());
        } else if (stmt instanceof ReturnStmt returnStmt) {
            if (returnStmt.getValue() != null) {
                return exprAccessesContextGlobal(returnStmt.getValue());
            }
        } else if (stmt instanceof IfStmt ifStmt) {
            if (exprAccessesContextGlobal(ifStmt.getCondition())) return true;
            if (ifStmt.getThenBranch() != null && accessesContextGlobal(ifStmt.getThenBranch())) return true;
            if (ifStmt.getElseBranch() != null && accessesContextGlobal(ifStmt.getElseBranch())) return true;
        } else if (stmt instanceof WhileStmt whileStmt) {
            if (exprAccessesContextGlobal(whileStmt.getCondition())) return true;
            if (whileStmt.getBody() != null && accessesContextGlobal(whileStmt.getBody())) return true;
        }
        return false;
    }
    
    /**
     * Check if an expression accesses msg or block variables.
     */
    private boolean exprAccessesContextGlobal(Expression expr) {
        if (expr instanceof VariableExpr varExpr) {
            String name = varExpr.getName().getLexeme();
            return MsgContext.isContractGlobal(name);
        } else if (expr instanceof GetExpr get) {
            return exprAccessesContextGlobal(get.getObject());
        } else if (expr instanceof BinaryExpr binary) {
            return exprAccessesContextGlobal(binary.getLeft()) || exprAccessesContextGlobal(binary.getRight());
        } else if (expr instanceof UnaryExpr unary) {
            return exprAccessesContextGlobal(unary.getRight());
        } else if (expr instanceof CallExpr call) {
            if (exprAccessesContextGlobal(call.getCallee())) return true;
            for (Expression arg : call.getArguments()) {
                if (exprAccessesContextGlobal(arg)) return true;
            }
        } else if (expr instanceof AssignmentExpr assign) {
            return exprAccessesContextGlobal(assign.getValue());
        }
        return false;
    }
    
    private void reportError(String code, String message, String suggestion, SourceLocation location) {
        errorCount++;
        if (errorReporter != null && location != null) {
            ErrorCode errorCode = ErrorCode.fromCode(code);
            if (errorCode != null) {
                errorReporter.error(location, "[" + code + "] " + message, suggestion, errorCode);
            } else {
                errorReporter.error(location, "[" + code + "] " + message, suggestion);
            }
        }
    }
    
    /**
     * Get the number of errors found.
     */
    public int getErrorCount() {
        return errorCount;
    }
}
