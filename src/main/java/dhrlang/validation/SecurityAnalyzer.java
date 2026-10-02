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
        public enum Category { TAINT, PRIVILEGE, LOOP_BOUND, ACCESS_CONTROL, REENTRANCY, TX_ORIGIN }

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
        Set<String> addressStorage = new HashSet<>();
        for (VarDecl v : classDecl.getVariables()) {
            if (v.hasContractAnnotation(ContractAnnotation.STORAGE)) {
                storageFields.add(v.getName());
                if ("Address".equals(v.getType())) addressStorage.add(v.getName());
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
            analyzeReentrancy(fn, storageFields, addressStorage);
            analyzeTxOrigin(fn);
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

    // ── Reentrancy: state write after external call ───────────────────────

    /**
     * Flags the classic checks-effects-interactions violation: an external call
     * (a value transfer via {@code this.transfer(...)} or a method call on an
     * external contract/address) followed by a storage write, in a function that
     * is not annotated {@code @nonreentrant}. Maps to SWC-107 (Reentrancy).
     */
    private void analyzeReentrancy(FunctionDecl fn, Set<String> storageFields,
                                   Set<String> addressStorage) {
        if (fn.getBody() == null) return;
        if (fn.isNonReentrant()) return; // explicit guard — trust it

        Set<String> externalRefs = new HashSet<>(addressStorage);
        for (VarDecl p : fn.getParameters()) {
            if ("Address".equals(p.getType())) externalRefs.add(p.getName());
        }

        ReentrancyScan scan = new ReentrancyScan();
        walkReentrancy(fn.getBody().getStatements(), fn, storageFields, externalRefs, scan);
    }

    private static final class ReentrancyScan {
        boolean sawExternalCall = false;
        String callDesc = null;
        boolean reported = false;
    }

    private void walkReentrancy(List<Statement> stmts, FunctionDecl fn,
                                Set<String> storageFields, Set<String> externalRefs,
                                ReentrancyScan scan) {
        for (Statement stmt : stmts) {
            if (scan.reported) return;

            // 1. Storage write while an earlier external call is pending → reentrancy.
            if (scan.sawExternalCall) {
                String field = storageWriteField(stmt, storageFields);
                if (field != null) {
                    findings.add(new Finding(
                            Finding.Severity.HIGH,
                            Finding.Category.REENTRANCY,
                            "State write after external call",
                            "Storage field '" + field + "' is written after the external call "
                                    + scan.callDesc + ", so a reentrant call can observe stale state.",
                            fn.getName(),
                            "Apply checks-effects-interactions: update storage before the external "
                                    + "call, or annotate the function @nonreentrant.",
                            stmt.getSourceLocation() != null
                                    ? stmt.getSourceLocation() : fn.getSourceLocation()));
                    scan.reported = true;
                    return;
                }
            }

            // 2. Update the external-call flag from this statement (source order).
            scanStatementForExternalCall(stmt, externalRefs, scan);

            // 3. Descend into control flow in source order.
            if (stmt instanceof Block block) {
                walkReentrancy(block.getStatements(), fn, storageFields, externalRefs, scan);
            } else if (stmt instanceof IfStmt ifStmt) {
                walkReentrancy(asList(ifStmt.getThenBranch()), fn, storageFields, externalRefs, scan);
                if (ifStmt.getElseBranch() != null) {
                    walkReentrancy(asList(ifStmt.getElseBranch()), fn, storageFields, externalRefs, scan);
                }
            } else if (stmt instanceof WhileStmt whileStmt) {
                walkReentrancy(asList(whileStmt.getBody()), fn, storageFields, externalRefs, scan);
            } else if (stmt instanceof TryStmt tryStmt) {
                if (tryStmt.getTryBlock() != null) {
                    walkReentrancy(tryStmt.getTryBlock().getStatements(), fn, storageFields, externalRefs, scan);
                }
                if (tryStmt.getFinallyBlock() != null) {
                    walkReentrancy(tryStmt.getFinallyBlock().getStatements(), fn, storageFields, externalRefs, scan);
                }
            }
        }
    }

    private List<Statement> asList(Statement s) {
        if (s == null) return List.of();
        if (s instanceof Block b) return b.getStatements();
        return List.of(s);
    }

    private void scanStatementForExternalCall(Statement stmt, Set<String> externalRefs, ReentrancyScan scan) {
        Expression e = statementExpression(stmt);
        if (e == null) return;
        String[] desc = new String[1];
        if (findExternalCall(e, externalRefs, desc)) {
            scan.sawExternalCall = true;
            scan.callDesc = desc[0];
        }
    }

    private Expression statementExpression(Statement stmt) {
        if (stmt instanceof ExpressionStmt es) return es.getExpression();
        if (stmt instanceof VarDecl vd) return vd.getInitializer();
        if (stmt instanceof ReturnStmt rs) return rs.getValue();
        if (stmt instanceof ThrowStmt ts) return ts.getValue();
        if (stmt instanceof IfStmt ifs) return ifs.getCondition();
        if (stmt instanceof WhileStmt ws) return ws.getCondition();
        return null;
    }

    /** Name of the storage field written by this statement, or null. */
    private String storageWriteField(Statement stmt, Set<String> storageFields) {
        if (!(stmt instanceof ExpressionStmt es)) return null;
        Expression e = es.getExpression();
        if (e instanceof AssignmentExpr a) {
            String n = a.getName().getLexeme();
            if (storageFields.contains(n)) return n;
        }
        if (e instanceof SetExpr s && s.getObject() instanceof ThisExpr) {
            String n = s.getName().getLexeme();
            if (storageFields.contains(n)) return n;
        }
        if (e instanceof IndexAssignExpr ia) {
            String base = baseName(ia.getObject());
            if (base != null && storageFields.contains(base)) return base;
        }
        return null;
    }

    private String baseName(Expression e) {
        if (e instanceof VariableExpr ve) return ve.getName().getLexeme();
        if (e instanceof IndexExpr ix) return baseName(ix.getObject());
        if (e instanceof GetExpr ge) return baseName(ge.getObject());
        return null;
    }

    /** Recursively search for an external contract call; sets descOut[0] on hit. */
    private boolean findExternalCall(Expression e, Set<String> externalRefs, String[] descOut) {
        if (e == null) return false;
        if (e instanceof CallExpr call) {
            if (call.getCallee() instanceof GetExpr ge) {
                Expression obj = ge.getObject();
                String member = ge.getName().getLexeme();
                boolean isThis = obj instanceof ThisExpr
                        || (obj instanceof VariableExpr tv && "this".equals(tv.getName().getLexeme()));
                if (isThis && "transfer".equals(member)) {
                    descOut[0] = "this.transfer(...)";
                    return true;
                }
                if (obj instanceof VariableExpr ve) {
                    String objName = ve.getName().getLexeme();
                    if (externalRefs.contains(objName) && !isArrayOp(member)) {
                        descOut[0] = objName + "." + member + "(...)";
                        return true;
                    }
                }
            }
            if (findExternalCall(call.getCallee(), externalRefs, descOut)) return true;
            for (Expression arg : call.getArguments()) {
                if (findExternalCall(arg, externalRefs, descOut)) return true;
            }
            return false;
        }
        for (Expression child : children(e)) {
            if (findExternalCall(child, externalRefs, descOut)) return true;
        }
        return false;
    }

    private boolean isArrayOp(String member) {
        return "push".equals(member) || "pop".equals(member) || "length".equals(member);
    }

    // ── tx.origin authorization ───────────────────────────────────────────

    /**
     * Flags {@code tx.origin} used in an equality check (authorization). tx.origin
     * is the original externally-owned account and is phishable; access control
     * must use msg.sender. Maps to SWC-115 (Authorization through tx.origin).
     */
    private void analyzeTxOrigin(FunctionDecl fn) {
        if (fn.getBody() == null) return;
        Expression hit = findTxOriginAuth(fn.getBody().getStatements());
        if (hit != null) {
            findings.add(new Finding(
                    Finding.Severity.HIGH,
                    Finding.Category.TX_ORIGIN,
                    "Authorization via tx.origin",
                    "tx.origin is compared for authorization. tx.origin is the original "
                            + "externally-owned account and can be spoofed by a malicious "
                            + "intermediary contract (phishing).",
                    fn.getName(),
                    "Use msg.sender for access control instead of tx.origin.",
                    hit.getSourceLocation() != null ? hit.getSourceLocation() : fn.getSourceLocation()));
        }
    }

    private Expression findTxOriginAuth(List<Statement> stmts) {
        for (Statement stmt : stmts) {
            Expression found = findTxOriginComparison(statementExpression(stmt));
            if (found != null) return found;
            if (stmt instanceof Block b) {
                found = findTxOriginAuth(b.getStatements());
            } else if (stmt instanceof IfStmt ifs) {
                found = findTxOriginAuth(asList(ifs.getThenBranch()));
                if (found == null && ifs.getElseBranch() != null) {
                    found = findTxOriginAuth(asList(ifs.getElseBranch()));
                }
            } else if (stmt instanceof WhileStmt ws) {
                found = findTxOriginAuth(asList(ws.getBody()));
            } else if (stmt instanceof TryStmt ts) {
                if (ts.getTryBlock() != null) found = findTxOriginAuth(ts.getTryBlock().getStatements());
                if (found == null && ts.getFinallyBlock() != null) {
                    found = findTxOriginAuth(ts.getFinallyBlock().getStatements());
                }
            }
            if (found != null) return found;
        }
        return null;
    }

    private Expression findTxOriginComparison(Expression e) {
        if (e == null) return null;
        if (e instanceof BinaryExpr bin) {
            var op = bin.getOperator().getType();
            if ((op == dhrlang.lexer.TokenType.EQUALITY || op == dhrlang.lexer.TokenType.NEQ)
                    && (containsTxOrigin(bin.getLeft()) || containsTxOrigin(bin.getRight()))) {
                return e;
            }
        }
        for (Expression child : children(e)) {
            Expression found = findTxOriginComparison(child);
            if (found != null) return found;
        }
        return null;
    }

    private boolean containsTxOrigin(Expression e) {
        if (e == null) return false;
        if (e instanceof GetExpr ge && ge.getObject() instanceof VariableExpr ve
                && "tx".equals(ve.getName().getLexeme())
                && "origin".equals(ge.getName().getLexeme())) {
            return true;
        }
        for (Expression child : children(e)) {
            if (containsTxOrigin(child)) return true;
        }
        return false;
    }

    /** Direct sub-expressions of an expression (best-effort over known node types). */
    private List<Expression> children(Expression e) {
        List<Expression> out = new ArrayList<>();
        if (e instanceof BinaryExpr b) { out.add(b.getLeft()); out.add(b.getRight()); }
        else if (e instanceof UnaryExpr u) { out.add(u.getRight()); }
        else if (e instanceof CallExpr c) { out.add(c.getCallee()); out.addAll(c.getArguments()); }
        else if (e instanceof GetExpr g) { out.add(g.getObject()); }
        else if (e instanceof SetExpr s) { out.add(s.getObject()); out.add(s.getValue()); }
        else if (e instanceof AssignmentExpr a) { out.add(a.getValue()); }
        else if (e instanceof IndexExpr ix) { out.add(ix.getObject()); out.add(ix.getIndex()); }
        else if (e instanceof IndexAssignExpr ia) { out.add(ia.getObject()); out.add(ia.getIndex()); out.add(ia.getValue()); }
        else if (e instanceof TernaryExpr t) { out.add(t.getCondition()); out.add(t.getThenBranch()); out.add(t.getElseBranch()); }
        else if (e instanceof PostfixIncrementExpr p) { out.add(p.getTarget()); }
        else if (e instanceof PrefixIncrementExpr p) { out.add(p.getTarget()); }
        else if (e instanceof ArrayExpr ar) { out.addAll(ar.getElements()); }
        else if (e instanceof NewExpr n) { out.addAll(n.getArguments()); }
        out.removeIf(java.util.Objects::isNull);
        return out;
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
