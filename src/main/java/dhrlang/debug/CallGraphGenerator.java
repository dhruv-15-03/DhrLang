package dhrlang.debug;

import dhrlang.ast.*;

import java.util.*;

/**
 * Static-analysis pass that builds a call graph from a DhrLang {@link Program}.
 *
 * <p>Walks every function body in every class, finds {@link CallExpr} nodes,
 * and records caller→callee edges. The resulting graph supports queries for
 * callers, callees, recursive cycles, and export to DOT or ASCII format.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 *   var generator = new CallGraphGenerator();
 *   generator.analyze(program);
 *   System.out.println(generator.toAsciiFormat());
 *   System.out.println(generator.toDotFormat());
 * </pre>
 */
public class CallGraphGenerator {

    // ── Inner types ──────────────────────────────────────────────────────

    /**
     * A directed edge from caller to callee.
     */
    public static class CallEdge {
        private final String caller;
        private final String callee;
        private final int callCount;

        public CallEdge(String caller, String callee, int callCount) {
            this.caller = caller;
            this.callee = callee;
            this.callCount = callCount;
        }

        public String getCaller() { return caller; }
        public String getCallee() { return callee; }
        public int getCallCount() { return callCount; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CallEdge ce)) return false;
            return caller.equals(ce.caller) && callee.equals(ce.callee);
        }

        @Override
        public int hashCode() {
            return Objects.hash(caller, callee);
        }

        @Override
        public String toString() {
            return caller + " → " + callee + " (×" + callCount + ")";
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    /** caller → set of callees. */
    private final Map<String, Set<String>> callGraph = new LinkedHashMap<>();

    /** caller → (callee → count). */
    private final Map<String, Map<String, Integer>> edgeWeights = new LinkedHashMap<>();

    /** All known function names (including those with no calls). */
    private final Set<String> allFunctions = new LinkedHashSet<>();

    // ── Analysis ─────────────────────────────────────────────────────────

    /**
     * Analyse a whole program and build the call graph.
     *
     * <p>Clears any previously accumulated data before analysing.</p>
     */
    public void analyze(Program program) {
        clear();
        if (program == null) return;

        for (ClassDecl classDecl : program.getClasses()) {
            String className = classDecl.getName();

            for (FunctionDecl fn : classDecl.getFunctions()) {
                String qualifiedName = qualifyName(className, fn.getName());
                allFunctions.add(qualifiedName);

                // Walk the function body to find call expressions
                if (fn.getBody() != null) {
                    List<String> callees = new ArrayList<>();
                    collectCalls(fn.getBody(), className, callees);

                    for (String callee : callees) {
                        addEdge(qualifiedName, callee);
                    }
                }
            }
        }
    }

    /**
     * Manually add a call edge (for programmatic graph construction / testing).
     */
    public void addEdge(String caller, String callee) {
        allFunctions.add(caller);
        allFunctions.add(callee);

        callGraph.computeIfAbsent(caller, k -> new LinkedHashSet<>()).add(callee);
        edgeWeights
            .computeIfAbsent(caller, k -> new LinkedHashMap<>())
            .merge(callee, 1, Integer::sum);
    }

    /** Clear all graph data. */
    public void clear() {
        callGraph.clear();
        edgeWeights.clear();
        allFunctions.clear();
    }

    // ── Query methods ────────────────────────────────────────────────────

    /** The full call graph as caller→{callees}. */
    public Map<String, Set<String>> getCallGraph() {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : callGraph.entrySet()) {
            copy.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
        }
        return copy;
    }

    /** All edges as {@link CallEdge} objects. */
    public List<CallEdge> getAllEdges() {
        List<CallEdge> edges = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> entry : edgeWeights.entrySet()) {
            String caller = entry.getKey();
            for (Map.Entry<String, Integer> callee : entry.getValue().entrySet()) {
                edges.add(new CallEdge(caller, callee.getKey(), callee.getValue()));
            }
        }
        return edges;
    }

    /** Functions that call the given function. */
    public Set<String> getCallersOf(String function) {
        Set<String> callers = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : callGraph.entrySet()) {
            if (entry.getValue().contains(function)) {
                callers.add(entry.getKey());
            }
        }
        return callers;
    }

    /** Functions called by the given function. */
    public Set<String> getCalleesOf(String function) {
        Set<String> callees = callGraph.get(function);
        return callees != null ? new LinkedHashSet<>(callees) : Set.of();
    }

    /** All known function names. */
    public Set<String> getAllFunctions() {
        return new LinkedHashSet<>(allFunctions);
    }

    /** Number of unique edges in the graph. */
    public int getEdgeCount() {
        int count = 0;
        for (Set<String> callees : callGraph.values()) {
            count += callees.size();
        }
        return count;
    }

    /** Functions with no callers (entry points / roots). */
    public Set<String> getRootFunctions() {
        Set<String> roots = new LinkedHashSet<>(allFunctions);
        for (Set<String> callees : callGraph.values()) {
            roots.removeAll(callees);
        }
        return roots;
    }

    /** Functions with no callees (leaf functions). */
    public Set<String> getLeafFunctions() {
        Set<String> leaves = new LinkedHashSet<>();
        for (String fn : allFunctions) {
            Set<String> callees = callGraph.get(fn);
            if (callees == null || callees.isEmpty()) {
                leaves.add(fn);
            }
        }
        return leaves;
    }

    // ── Cycle detection (recursive calls) ────────────────────────────────

    /**
     * Find all simple cycles (recursive call paths) in the graph.
     *
     * <p>Returns a list of cycles, where each cycle is a list of
     * function names forming a loop.</p>
     */
    public List<List<String>> findRecursiveCycles() {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (String fn : allFunctions) {
            if (!visited.contains(fn)) {
                List<String> path = new ArrayList<>();
                Set<String> inStack = new HashSet<>();
                dfsFindCycles(fn, path, inStack, visited, cycles);
            }
        }
        return cycles;
    }

    private void dfsFindCycles(String node, List<String> path, Set<String> inStack,
                                Set<String> visited, List<List<String>> cycles) {
        visited.add(node);
        inStack.add(node);
        path.add(node);

        Set<String> callees = callGraph.getOrDefault(node, Set.of());
        for (String callee : callees) {
            if (inStack.contains(callee)) {
                // Found a cycle: extract from the callee's position in path to end
                int cycleStart = path.indexOf(callee);
                if (cycleStart >= 0) {
                    List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                    cycle.add(callee); // close the cycle
                    // Only add if not already detected (normalize by starting from smallest)
                    if (!containsCycle(cycles, cycle)) {
                        cycles.add(cycle);
                    }
                }
            } else if (!visited.contains(callee)) {
                dfsFindCycles(callee, path, inStack, visited, cycles);
            }
        }

        path.remove(path.size() - 1);
        inStack.remove(node);
    }

    private boolean containsCycle(List<List<String>> cycles, List<String> newCycle) {
        Set<String> newSet = new HashSet<>(newCycle);
        for (List<String> existing : cycles) {
            if (new HashSet<>(existing).equals(newSet)) return true;
        }
        return false;
    }

    // ── Export formats ───────────────────────────────────────────────────

    /**
     * Export the call graph in Graphviz DOT format.
     */
    public String toDotFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CallGraph {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  node [shape=box, style=rounded];\n\n");

        // Nodes
        for (String fn : allFunctions) {
            sb.append("  \"").append(dotEscape(fn)).append("\";\n");
        }
        sb.append("\n");

        // Edges
        for (Map.Entry<String, Map<String, Integer>> entry : edgeWeights.entrySet()) {
            String caller = entry.getKey();
            for (Map.Entry<String, Integer> callee : entry.getValue().entrySet()) {
                sb.append("  \"").append(dotEscape(caller)).append("\" -> \"")
                  .append(dotEscape(callee.getKey())).append("\"");
                if (callee.getValue() > 1) {
                    sb.append(" [label=\"×").append(callee.getValue()).append("\"]");
                }
                // Self-edge = recursive
                if (caller.equals(callee.getKey())) {
                    sb.append(" [color=red, style=bold]");
                }
                sb.append(";\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Export the call graph as ASCII text.
     */
    public String toAsciiFormat() {
        if (allFunctions.isEmpty()) {
            return "Call Graph: (empty)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═══ Call Graph (").append(allFunctions.size()).append(" functions, ")
          .append(getEdgeCount()).append(" edges) ═══\n");

        for (String fn : allFunctions) {
            Set<String> callees = callGraph.getOrDefault(fn, Set.of());
            sb.append("\n  ").append(fn);

            if (callees.isEmpty()) {
                sb.append("  (leaf)");
            } else {
                for (String callee : callees) {
                    int count = edgeWeights.getOrDefault(fn, Map.of()).getOrDefault(callee, 1);
                    sb.append("\n    → ").append(callee);
                    if (count > 1) sb.append(" (×").append(count).append(")");
                    if (fn.equals(callee)) sb.append(" [RECURSIVE]");
                }
            }
        }

        // Cycles
        List<List<String>> cycles = findRecursiveCycles();
        if (!cycles.isEmpty()) {
            sb.append("\n\n  ⚠ Recursive cycles detected:");
            for (List<String> cycle : cycles) {
                sb.append("\n    ").append(String.join(" → ", cycle));
            }
        }

        sb.append("\n═══ End Call Graph ═══");
        return sb.toString();
    }

    // ── AST walking helpers ──────────────────────────────────────────────

    /**
     * Recursively collect function call targets from a statement/block.
     */
    private void collectCalls(Statement stmt, String contextClass, List<String> callees) {
        if (stmt == null) return;

        if (stmt instanceof Block block) {
            for (Statement s : block.getStatements()) {
                collectCalls(s, contextClass, callees);
            }
        } else if (stmt instanceof ExpressionStmt exprStmt) {
            collectCallsFromExpr(exprStmt.getExpression(), contextClass, callees);
        } else if (stmt instanceof VarDecl varDecl) {
            if (varDecl.getInitializer() != null) {
                collectCallsFromExpr(varDecl.getInitializer(), contextClass, callees);
            }
        } else if (stmt instanceof ReturnStmt returnStmt) {
            if (returnStmt.getValue() != null) {
                collectCallsFromExpr(returnStmt.getValue(), contextClass, callees);
            }
        } else if (stmt instanceof IfStmt ifStmt) {
            collectCallsFromExpr(ifStmt.getCondition(), contextClass, callees);
            collectCalls(ifStmt.getThenBranch(), contextClass, callees);
            if (ifStmt.getElseBranch() != null) {
                collectCalls(ifStmt.getElseBranch(), contextClass, callees);
            }
        } else if (stmt instanceof WhileStmt whileStmt) {
            collectCallsFromExpr(whileStmt.getCondition(), contextClass, callees);
            collectCalls(whileStmt.getBody(), contextClass, callees);
        } else if (stmt instanceof TryStmt tryStmt) {
            collectCalls(tryStmt.getTryBlock(), contextClass, callees);
            for (CatchClause cc : tryStmt.getCatchClauses()) {
                collectCalls(cc.getBody(), contextClass, callees);
            }
        } else if (stmt instanceof PrintStmt printStmt) {
            collectCallsFromExpr(printStmt.getExpression(), contextClass, callees);
        } else if (stmt instanceof ThrowStmt throwStmt) {
            collectCallsFromExpr(throwStmt.getValue(), contextClass, callees);
        }
        // BreakStmt, ContinueStmt, FunctionDecl — no calls to collect at this level
    }

    /**
     * Recursively collect function call targets from an expression.
     */
    private void collectCallsFromExpr(Expression expr, String contextClass, List<String> callees) {
        if (expr == null) return;

        if (expr instanceof CallExpr call) {
            String calleeName = resolveCalleeName(call, contextClass);
            if (calleeName != null) {
                callees.add(calleeName);
            }
            // Also walk arguments
            for (Expression arg : call.getArguments()) {
                collectCallsFromExpr(arg, contextClass, callees);
            }
        } else if (expr instanceof BinaryExpr binary) {
            collectCallsFromExpr(binary.getLeft(), contextClass, callees);
            collectCallsFromExpr(binary.getRight(), contextClass, callees);
        } else if (expr instanceof UnaryExpr unary) {
            collectCallsFromExpr(unary.getRight(), contextClass, callees);
        } else if (expr instanceof GetExpr get) {
            collectCallsFromExpr(get.getObject(), contextClass, callees);
        } else if (expr instanceof SetExpr set) {
            collectCallsFromExpr(set.getObject(), contextClass, callees);
            collectCallsFromExpr(set.getValue(), contextClass, callees);
        } else if (expr instanceof IndexExpr index) {
            collectCallsFromExpr(index.getObject(), contextClass, callees);
            collectCallsFromExpr(index.getIndex(), contextClass, callees);
        } else if (expr instanceof AssignmentExpr assignment) {
            collectCallsFromExpr(assignment.getValue(), contextClass, callees);
        } else if (expr instanceof ArrayExpr array) {
            for (Expression element : array.getElements()) {
                collectCallsFromExpr(element, contextClass, callees);
            }
        } else if (expr instanceof NewExpr newExpr) {
            for (Expression arg : newExpr.getArguments()) {
                collectCallsFromExpr(arg, contextClass, callees);
            }
        }
        // VariableExpr, LiteralExpr, ThisExpr, SuperExpr — no calls
    }

    /**
     * Resolve a CallExpr's callee to a qualified name.
     */
    private String resolveCalleeName(CallExpr call, String contextClass) {
        Expression callee = call.getCallee();

        if (callee instanceof VariableExpr varExpr) {
            // Simple function call: foo()
            String name = varExpr.getName().getLexeme();
            // Try to qualify with the owning class if it looks like a method
            // But for global functions, just use the name as-is
            return name;
        } else if (callee instanceof GetExpr getExpr) {
            // Method call: obj.method()
            String methodName = getExpr.getName().getLexeme();
            Expression obj = getExpr.getObject();
            if (obj instanceof VariableExpr objVar) {
                String objName = objVar.getName().getLexeme();
                if ("this".equals(objName) || "super".equals(objName)) {
                    return qualifyName(contextClass, methodName);
                }
                return objName + "." + methodName;
            } else if (obj instanceof ThisExpr) {
                return qualifyName(contextClass, methodName);
            }
            return methodName;
        }

        return null; // Can't resolve (e.g., computed function references)
    }

    private static String qualifyName(String className, String funcName) {
        if (className == null || className.isEmpty()) return funcName;
        return className + "." + funcName;
    }

    private static String dotEscape(String s) {
        return s.replace("\"", "\\\"");
    }
}
