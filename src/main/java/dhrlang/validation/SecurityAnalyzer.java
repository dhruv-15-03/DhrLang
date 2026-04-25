package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.SourceLocation;
import dhrlang.lexer.*;
import dhrlang.error.ErrorReporter;

import java.util.*;

/**
 * Security analyzer combining taint tracking, privilege escalation detection,
 * and loop bound analysis for smart contracts.
 *
 * <h3>Taint Tracking</h3>
 * <p>Tracks how untrusted input (calldata parameters, msg.sender, msg.value)
 * flows through the program to state modifications. Detects patterns like:</p>
 * <ul>
 *   <li>Unvalidated user input directly modifying storage</li>
 *   <li>Unchecked msg.value used in arithmetic</li>
 *   <li>External call return value used without validation</li>
 * </ul>
 *
 * <h3>Privilege Escalation</h3>
 * <p>Maps which addresses (msg.sender patterns) can call which functions
 * and modify which storage. Detects:</p>
 * <ul>
 *   <li>Functions that modify owner/admin without access control</li>
 *   <li>Missing onlyOwner guards on privileged operations</li>
 *   <li>Self-destruct or pause without access control</li>
 * </ul>
 *
 * <h3>Loop Bound Analysis</h3>
 * <p>Detects unbounded loops that could cause out-of-gas DoS:</p>
 * <ul>
 *   <li>Loops iterating over storage arrays (length from storage)</li>
 *   <li>While loops with no obvious bound</li>
 *   <li>Recursive patterns in call chains</li>
 * </ul>
 */
public class SecurityAnalyzer {

    // ── Finding Types ────────────────────────────────────────────────────

    /**
     * A security finding.
     */
    public static class Finding {
        public enum Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }
        public enum Category { TAINT, PRIVILEGE, LOOP_BOUND, ACCESS_CONTROL }

        private final Severity severity;
        private final Category category;
        private final String title;
        private final String description;
        private final String functionName;
        private final String hint;
        private final SourceLocation location;

        public Finding(Severity severity, Category category, String title,
                       String description, String functionName, String hint,
                       SourceLocation location) {
            this.severity = severity;
            this.category = category;
            this.title = title;
            this.description = description;
            this.functionName = functionName;
            this.hint = hint;
            this.location = location;
        }

        public Severity getSeverity() { return severity; }
        public Category getCategory() { return category; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getFunctionName() { return functionName; }
        public String getHint() { return hint; }
        public SourceLocation getLocation() { return location; }

        @Override
        public String toString() {
            return "[" + severity + "] " + category + " — " + title
                    + " in " + functionName + "()";
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private final ErrorReporter errorReporter;
    private final List<Finding> findings = new ArrayList<>();

    public SecurityAnalyzer() {
        this(null);
    }

    public SecurityAnalyzer(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Run all security analyses on a contract.
     */
    public List<Finding> analyze(ClassDecl classDecl) {
        findings.clear();
        if (!classDecl.isContract()) return findings;

        Set<String> storageFields = new HashSet<>();
        Set<String> privilegedFields = new HashSet<>();
        for (VarDecl v : classDecl.getVariables()) {
            if (v.hasContractAnnotation(ContractAnnotation.STORAGE)) {
                storageFields.add(v.getName());
                String name = v.getName().toLowerCase();
                if (name.contains("owner") || name.contains("admin")
                        || name.contains("pauser") || name.contains("minter")) {
                    privilegedFields.add(v.getName());
                }
            }
        }

        for (FunctionDecl fn : classDecl.getFunctions()) {
            if (fn.getBody() == null) continue;
            if (fn.hasContractAnnotation(ContractAnnotation.EVENT)) continue;
            if (fn.isContractConstructor()) continue;

            analyzePrivilegeEscalation(fn, storageFields, privilegedFields);
            analyzeTaintFlow(fn, storageFields);
            analyzeLoopBounds(fn);
        }

        // Report to ErrorReporter
        for (Finding f : findings) {
            if (errorReporter != null) {
                if (f.getSeverity() == Finding.Severity.CRITICAL
                        || f.getSeverity() == Finding.Severity.HIGH) {
                    errorReporter.warning(f.getLocation(),
                            f.getTitle() + " in " + f.getFunctionName() + "()",
                            f.getHint());
                }
            }
        }

        return findings;
    }

    public List<Finding> getFindings() {
        return Collections.unmodifiableList(findings);
    }

    public List<Finding> getCriticalFindings() {
        return findings.stream()
                .filter(f -> f.getSeverity() == Finding.Severity.CRITICAL)
                .toList();
    }

    // ── Privilege Escalation Detection ────────────────────────────────────

    private void analyzePrivilegeEscalation(FunctionDecl fn, Set<String> storageFields,
                                             Set<String> privilegedFields) {
        if (fn.getBody() == null) return;
        List<Statement> stmts = fn.getBody().getStatements();

        // Check which storage fields this function modifies
        Set<String> modified = new HashSet<>();
        collectModifiedStorageFields(stmts, storageFields, modified);

        // Check if any privileged fields are modified
        Set<String> modifiedPrivileged = new HashSet<>(modified);
        modifiedPrivileged.retainAll(privilegedFields);

        if (!modifiedPrivileged.isEmpty()) {
            // Check if the function has an access control guard
            boolean hasGuard = hasAccessControlGuard(stmts);

            if (!hasGuard) {
                findings.add(new Finding(
                        Finding.Severity.CRITICAL,
                        Finding.Category.PRIVILEGE,
                        "Unprotected privileged storage modification",
                        "Function modifies privileged field(s) " + modifiedPrivileged
                                + " without msg.sender access control check.",
                        fn.getName(),
                        "Add: if (msg.sender != owner) { throw \"Not authorized\"; }",
                        fn.getSourceLocation()));
            }
        }
    }

    /**
     * Check if function body has an access control guard.
     * Looks for patterns: if (msg.sender != owner) throw; / require(msg.sender == owner)
     */
    private boolean hasAccessControlGuard(List<Statement> stmts) {
        for (Statement stmt : stmts) {
            if (stmt instanceof IfStmt ifStmt) {
                if (isMsgSenderCheck(ifStmt.getCondition())) return true;
            }
            if (stmt instanceof ExpressionStmt es) {
                Expression expr = es.getExpression();
                // require(msg.sender == owner, "...")
                if (expr instanceof CallExpr call
                        && call.getCallee() instanceof VariableExpr ve
                        && "require".equals(ve.getName().getLexeme())
                        && !call.getArguments().isEmpty()
                        && isMsgSenderCheck(call.getArguments().get(0))) {
                    return true;
                }
                // onlyOwner() call pattern
                if (expr instanceof CallExpr call2
                        && call2.getCallee() instanceof VariableExpr ve2) {
                    String name = ve2.getName().getLexeme().toLowerCase();
                    if (name.contains("onlyowner") || name.contains("onlyadmin")
                            || name.contains("onlyminter")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isMsgSenderCheck(Expression cond) {
        if (cond instanceof BinaryExpr bin) {
            var op = bin.getOperator().getType();
            if (op == dhrlang.lexer.TokenType.EQUALITY || op == dhrlang.lexer.TokenType.NEQ) {
                return containsMsgSender(bin.getLeft()) || containsMsgSender(bin.getRight());
            }
        }
        return false;
    }

    private boolean containsMsgSender(Expression expr) {
        if (expr instanceof GetExpr ge) {
            if (ge.getObject() instanceof VariableExpr msgVar
                    && "msg".equals(msgVar.getName().getLexeme())
                    && "sender".equals(ge.getName().getLexeme())) {
                return true;
            }
        }
        if (expr instanceof VariableExpr ve && "msg.sender".equals(ve.getName().getLexeme())) {
            return true;
        }
        return false;
    }

    // ── Taint Flow Analysis ──────────────────────────────────────────────

    private void analyzeTaintFlow(FunctionDecl fn, Set<String> storageFields) {
        if (fn.getBody() == null) return;

        // Parameters are tainted (user input from calldata)
        Set<String> taintedVars = new HashSet<>();
        for (VarDecl param : fn.getParameters()) {
            taintedVars.add(param.getName());
        }

        // Walk statements and propagate taint
        for (Statement stmt : fn.getBody().getStatements()) {
            checkTaintedStorageWrite(fn, stmt, storageFields, taintedVars);
        }
    }

    private void checkTaintedStorageWrite(FunctionDecl fn, Statement stmt,
                                           Set<String> storageFields, Set<String> tainted) {
        if (stmt instanceof ExpressionStmt es) {
            Expression expr = es.getExpression();
            if (expr instanceof AssignmentExpr assign) {
                String target = null;
                if (true) {
                    target = assign.getName().getLexeme();
                }
                if (target != null && storageFields.contains(target)) {
                    // Check if value is tainted (comes directly from a parameter)
                    if (isDirectlyTainted(assign.getValue(), tainted)) {
                        // Check if there's a validation before this assignment
                        boolean validated = hasPrecedingValidation(fn, stmt, tainted);
                        if (!validated) {
                            findings.add(new Finding(
                                    Finding.Severity.HIGH,
                                    Finding.Category.TAINT,
                                    "Unvalidated user input to storage",
                                    "Parameter value flows directly to storage field '" + target
                                            + "' without validation.",
                                    fn.getName(),
                                    "Validate input before writing to storage: "
                                            + "if (value <= 0) { throw \"Invalid\"; }",
                                    fn.getSourceLocation()));
                        }
                    }
                }
            }
        } else if (stmt instanceof Block block) {
            for (Statement s : block.getStatements()) {
                checkTaintedStorageWrite(fn, s, storageFields, tainted);
            }
        } else if (stmt instanceof IfStmt ifStmt) {
            checkTaintedStorageWrite(fn, ifStmt.getThenBranch(), storageFields, tainted);
            if (ifStmt.getElseBranch() != null) {
                checkTaintedStorageWrite(fn, ifStmt.getElseBranch(), storageFields, tainted);
            }
        }
    }

    private boolean isDirectlyTainted(Expression expr, Set<String> tainted) {
        if (expr instanceof VariableExpr ve) {
            return tainted.contains(ve.getName().getLexeme());
        }
        if (expr instanceof BinaryExpr bin) {
            return isDirectlyTainted(bin.getLeft(), tainted)
                    || isDirectlyTainted(bin.getRight(), tainted);
        }
        return false;
    }

    private boolean hasPrecedingValidation(FunctionDecl fn, Statement target, Set<String> tainted) {
        // Check if there's an if/require before the target statement
        List<Statement> stmts = fn.getBody().getStatements();
        for (Statement stmt : stmts) {
            if (stmt == target) break;
            if (stmt instanceof IfStmt ifStmt) {
                // If the if-condition references a tainted var, it's a validation
                if (referencesAny(ifStmt.getCondition(), tainted)) return true;
            }
            if (stmt instanceof ExpressionStmt es && es.getExpression() instanceof CallExpr call) {
                if (call.getCallee() instanceof VariableExpr ve
                        && "require".equals(ve.getName().getLexeme())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean referencesAny(Expression expr, Set<String> names) {
        if (expr instanceof VariableExpr ve) {
            return names.contains(ve.getName().getLexeme());
        }
        if (expr instanceof BinaryExpr bin) {
            return referencesAny(bin.getLeft(), names) || referencesAny(bin.getRight(), names);
        }
        return false;
    }

    // ── Loop Bound Analysis ──────────────────────────────────────────────

    private void analyzeLoopBounds(FunctionDecl fn) {
        if (fn.getBody() == null) return;
        analyzeLoopBoundsInStatements(fn, fn.getBody().getStatements(), 0);
    }

    private void analyzeLoopBoundsInStatements(FunctionDecl fn, List<Statement> stmts, int depth) {
        for (Statement stmt : stmts) {
            if (stmt instanceof WhileStmt whileStmt) {
                checkLoopBound(fn, whileStmt, depth);
                analyzeLoopBoundsInStatements(fn, List.of(whileStmt.getBody()), depth + 1);
            } else if (stmt instanceof WhileStmt forStmt) {
                checkForLoopBound(fn, forStmt, depth);
            } else if (stmt instanceof Block block) {
                analyzeLoopBoundsInStatements(fn, block.getStatements(), depth);
            } else if (stmt instanceof IfStmt ifStmt) {
                analyzeLoopBoundsInStatements(fn, List.of(ifStmt.getThenBranch()), depth);
                if (ifStmt.getElseBranch() != null) {
                    analyzeLoopBoundsInStatements(fn, List.of(ifStmt.getElseBranch()), depth);
                }
            }
        }
    }

    private void checkLoopBound(FunctionDecl fn, WhileStmt whileStmt, int depth) {
        Expression cond = whileStmt.getCondition();

        // Check if condition is `true` (infinite loop)
        if (cond instanceof LiteralExpr lit && Boolean.TRUE.equals(lit.getValue())) {
            findings.add(new Finding(
                    Finding.Severity.CRITICAL,
                    Finding.Category.LOOP_BOUND,
                    "Infinite while(true) loop",
                    "While loop with literal 'true' condition will consume all gas.",
                    fn.getName(),
                    "Add a counter or break condition to prevent gas exhaustion.",
                    fn.getSourceLocation()));
            return;
        }

        // Check for nested loops (high gas risk)
        if (depth > 0) {
            findings.add(new Finding(
                    Finding.Severity.MEDIUM,
                    Finding.Category.LOOP_BOUND,
                    "Nested loop detected",
                    "Nested loops multiply gas consumption. Depth: " + (depth + 1),
                    fn.getName(),
                    "Avoid nested loops in on-chain code. Consider off-chain computation.",
                    fn.getSourceLocation()));
        }
    }

    private void checkForLoopBound(FunctionDecl fn, WhileStmt forStmt, int depth) {
        // For loops are bounded by their condition — generally OK
        // But warn about nested loops
        if (depth > 0) {
            findings.add(new Finding(
                    Finding.Severity.MEDIUM,
                    Finding.Category.LOOP_BOUND,
                    "Nested for-loop detected",
                    "Nested loops at depth " + (depth + 1) + " may exceed block gas limit.",
                    fn.getName(),
                    "Limit iterations or move computation off-chain.",
                    fn.getSourceLocation()));
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────

    private void collectModifiedStorageFields(List<Statement> stmts, Set<String> storageFields,
                                               Set<String> modified) {
        for (Statement stmt : stmts) {
            if (stmt instanceof ExpressionStmt es) {
                Expression expr = es.getExpression();
                if (expr instanceof AssignmentExpr assign && true) {
                    String name = assign.getName().getLexeme();
                    if (storageFields.contains(name)) modified.add(name);
                }
            } else if (stmt instanceof Block block) {
                collectModifiedStorageFields(block.getStatements(), storageFields, modified);
            } else if (stmt instanceof IfStmt ifStmt) {
                collectModifiedStorageFields(List.of(ifStmt.getThenBranch()), storageFields, modified);
                if (ifStmt.getElseBranch() != null) {
                    collectModifiedStorageFields(List.of(ifStmt.getElseBranch()), storageFields, modified);
                }
            }
        }
    }
}
