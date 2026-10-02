package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.SourceLocation;
import dhrlang.lexer.Token;
import dhrlang.lexer.TokenType;

import java.math.BigInteger;
import java.util.*;

/**
 * Local, deliberately incomplete bounds analysis for unsigned contract arithmetic.
 * Facts belong to a program point, not a variable name. Branches intersect facts;
 * writes invalidate them; loops and exception handlers restart conservatively.
 * An unrecognised expression or call never counts as evidence of a bound.
 */
public final class GuardAnalysis {
    private GuardAnalysis() {}

    public record Write(String field, Expression value, SourceLocation location, Bounds bounds) {}

    private sealed interface Term permits Name, Constant, Operation {
        boolean uses(String name);
    }

    private record Name(String name) implements Term {
        public boolean uses(String other) {
            return name.equals(other) || name.equals("this." + other);
        }
    }

    private record Constant(BigInteger value) implements Term {
        public boolean uses(String name) { return false; }
    }

    private record Operation(TokenType operator, Term left, Term right) implements Term {
        public boolean uses(String name) { return left.uses(name) || right.uses(name); }
    }

    private record Relation(Term lower, Term upper, boolean strict) {
        boolean uses(String name) { return lower.uses(name) || upper.uses(name); }
    }

    private static final Constant ZERO = new Constant(BigInteger.ZERO);
    private static final Constant ONE = new Constant(BigInteger.ONE);

    public static final class Bounds {
        private final Set<Relation> relations;
        private final Set<Term> nonzero;
        private boolean reachable = true;

        private Bounds() {
            relations = new HashSet<>();
            nonzero = new HashSet<>();
        }

        private Bounds(Bounds source) {
            relations = new HashSet<>(source.relations);
            nonzero = new HashSet<>(source.nonzero);
            reachable = source.reachable;
        }

        public boolean guards(BinaryExpr arithmetic) {
            Term left = term(arithmetic.getLeft());
            Term right = term(arithmetic.getRight());
            if (left == null || right == null || !defined(left) || !defined(right)) return false;
            return switch (arithmetic.getOperator().getType()) {
                case MINUS -> atMost(right, left, false);
                case PLUS -> ZERO.equals(left) || ZERO.equals(right)
                        || boundedResult(left, right, TokenType.MINUS)
                        || boundedResult(right, left, TokenType.MINUS)
                        || (ONE.equals(right) && hasStrictUpperBound(left))
                        || (ONE.equals(left) && hasStrictUpperBound(right));
                case STAR -> ZERO.equals(left) || ZERO.equals(right)
                        || ONE.equals(left) || ONE.equals(right)
                        || boundedResult(left, right, TokenType.SLASH)
                        || boundedResult(right, left, TokenType.SLASH);
                case SLASH, MOD -> isNonzero(right);
                default -> false;
            };
        }

        private boolean hasStrictUpperBound(Term value) {
            return relations.stream().anyMatch(r -> r.strict && r.lower.equals(value) && defined(r.upper));
        }

        private boolean boundedResult(Term value, Term operand, TokenType operation) {
            for (Relation relation : relations) {
                if (relation.lower.equals(value)
                        && relation.upper instanceof Operation bound
                        && bound.operator == operation && bound.right.equals(operand)) {
                    // MAX - operand is meaningful only if the subtraction itself is bounded.
                    if (operation == TokenType.MINUS && atMost(operand, bound.left, false)) return true;
                    if (operation == TokenType.SLASH && isNonzero(operand)) return true;
                }
            }
            return false;
        }

        private boolean defined(Term value) {
            if (value instanceof Operation operation) {
                if (!defined(operation.left) || !defined(operation.right)) return false;
                return operation.operator == TokenType.MINUS
                        ? atMost(operation.right, operation.left, false) : isNonzero(operation.right);
            }
            return true;
        }

        private boolean atMost(Term lower, Term upper, boolean strict) {
            if (!strict && lower.equals(upper)) return true;
            if (lower instanceof Constant a && upper instanceof Constant b) {
                int comparison = a.value.compareTo(b.value);
                return strict ? comparison < 0 : comparison <= 0;
            }
            return relations.stream().anyMatch(r -> r.lower.equals(lower)
                    && r.upper.equals(upper) && (!strict || r.strict));
        }

        private boolean isNonzero(Term value) {
            return value instanceof Constant c ? c.value.signum() != 0
                    : nonzero.contains(value) || atMost(ZERO, value, true) || atMost(ONE, value, false);
        }

        private void forget(String name) {
            relations.removeIf(r -> r.uses(name));
            nonzero.removeIf(t -> t.uses(name));
        }

        private void clear() {
            relations.clear();
            nonzero.clear();
        }

        private void assume(Expression condition, boolean truth) {
            if (!pure(condition)) return;
            if (condition instanceof UnaryExpr unary && unary.getOperator().getType() == TokenType.NOT) {
                assume(unary.getRight(), !truth);
                return;
            }
            if (!(condition instanceof BinaryExpr binary)) return;
            TokenType op = binary.getOperator().getType();
            if ((op == TokenType.AND && truth) || (op == TokenType.OR && !truth)) {
                assume(binary.getLeft(), truth);
                assume(binary.getRight(), truth);
                return;
            }
            Term left = term(binary.getLeft());
            Term right = term(binary.getRight());
            if (left == null || right == null || !defined(left) || !defined(right)) return;
            if (!truth) {
                op = switch (op) {
                    case LESS -> TokenType.GEQ;
                    case LEQ -> TokenType.GREATER;
                    case GREATER -> TokenType.LEQ;
                    case GEQ -> TokenType.LESS;
                    case EQUALITY -> TokenType.NEQ;
                    case NEQ -> TokenType.EQUALITY;
                    default -> op;
                };
            }
            switch (op) {
                case LESS -> relations.add(new Relation(left, right, true));
                case LEQ -> relations.add(new Relation(left, right, false));
                case GREATER -> relations.add(new Relation(right, left, true));
                case GEQ -> relations.add(new Relation(right, left, false));
                case EQUALITY -> {
                    relations.add(new Relation(left, right, false));
                    relations.add(new Relation(right, left, false));
                }
                case NEQ -> {
                    if (ZERO.equals(left)) nonzero.add(right);
                    if (ZERO.equals(right)) nonzero.add(left);
                }
                default -> { }
            }
        }
    }

    public static List<Write> analyze(FunctionDecl function, Set<String> storageFields) {
        if (function.getBody() == null) return List.of();
        Walker walker = new Walker(storageFields);
        Set<String> locals = new HashSet<>();
        for (VarDecl parameter : function.getParameters()) locals.add(parameter.getName());
        Bounds bounds = new Bounds();
        for (Expression precondition : function.getRequires()) bounds.assume(precondition, true);
        walker.block(function.getBody(), bounds, locals);
        return List.copyOf(walker.writes);
    }

    private static Bounds join(Bounds left, Bounds right) {
        if (!left.reachable) return new Bounds(right);
        if (!right.reachable) return new Bounds(left);
        Bounds result = new Bounds(left);
        result.relations.retainAll(right.relations);
        result.nonzero.retainAll(right.nonzero);
        return result;
    }

    private static Term term(Expression expression) {
        if (expression instanceof VariableExpr variable) return new Name(variable.getName().getLexeme());
        if (expression instanceof GetExpr get) {
            if (get.getObject() instanceof ThisExpr) return new Name("this." + get.getName().getLexeme());
            if (get.getObject() instanceof VariableExpr variable
                    && Set.of("msg", "block", "tx").contains(variable.getName().getLexeme())) {
                return new Name(variable.getName().getLexeme() + "." + get.getName().getLexeme());
            }
        }
        if (expression instanceof LiteralExpr literal
                && (literal.getValue() instanceof Long || literal.getValue() instanceof Integer)) {
            long value = ((Number) literal.getValue()).longValue();
            return value >= 0 ? new Constant(BigInteger.valueOf(value)) : null;
        }
        if (expression instanceof BinaryExpr binary) {
            TokenType op = binary.getOperator().getType();
            if (op == TokenType.MINUS || op == TokenType.SLASH) {
                Term left = term(binary.getLeft());
                Term right = term(binary.getRight());
                if (left != null && right != null) return new Operation(op, left, right);
            }
        }
        return null;
    }

    private static boolean pure(Expression expression) {
        if (expression instanceof LiteralExpr || expression instanceof VariableExpr || expression instanceof ThisExpr) {
            return true;
        }
        if (expression instanceof GetExpr) return term(expression) != null;
        if (expression instanceof BinaryExpr binary) return pure(binary.getLeft()) && pure(binary.getRight());
        if (expression instanceof UnaryExpr unary) return pure(unary.getRight());
        return false;
    }

    private static final class Walker {
        private final Set<String> storage;
        private final List<Write> writes = new ArrayList<>();

        private Walker(Set<String> storage) { this.storage = storage; }

        private Bounds block(Block block, Bounds bounds, Set<String> outerLocals) {
            Set<String> locals = new HashSet<>(outerLocals);
            for (Statement statement : block.getStatements()) {
                if (!bounds.reachable) break;
                bounds = statement(statement, bounds, locals);
            }
            // A shadowing declaration must not lend its bounds to the enclosing binding.
            for (Statement statement : block.getStatements()) {
                if (statement instanceof VarDecl declared) bounds.forget(declared.getName());
            }
            for (String name : locals) if (!outerLocals.contains(name)) bounds.forget(name);
            return bounds;
        }

        private Bounds statement(Statement statement, Bounds bounds, Set<String> locals) {
            if (statement instanceof Block block) return block(block, bounds, locals);
            if (statement instanceof VarDecl variable) {
                bounds = expression(variable.getInitializer(), bounds, locals);
                bounds.forget(variable.getName());
                locals.add(variable.getName());
            } else if (statement instanceof ExpressionStmt es) {
                Expression expr = es.getExpression();
                if (expr instanceof CallExpr call && call.getCallee() instanceof VariableExpr callee
                        && Set.of("require", "assert").contains(callee.getName().getLexeme())
                        && !call.getArguments().isEmpty()
                        && call.getArguments().stream().allMatch(GuardAnalysis::pure)) {
                    bounds.assume(call.getArguments().get(0), true);
                } else {
                    bounds = expression(expr, bounds, locals);
                    if (expr instanceof CallExpr call && call.getCallee() instanceof VariableExpr callee
                            && "revert".equals(callee.getName().getLexeme())) bounds.reachable = false;
                }
            } else if (statement instanceof IfStmt conditional) {
                bounds = expression(conditional.getCondition(), bounds, locals);
                Bounds yes = new Bounds(bounds);
                Bounds no = new Bounds(bounds);
                yes.assume(conditional.getCondition(), true);
                no.assume(conditional.getCondition(), false);
                yes = statement(conditional.getThenBranch(), yes, new HashSet<>(locals));
                if (conditional.getElseBranch() != null) {
                    no = statement(conditional.getElseBranch(), no, new HashSet<>(locals));
                }
                return join(yes, no);
            } else if (statement instanceof WhileStmt loop) {
                // The next iteration may have different values from the first.
                Bounds iteration = expression(loop.getCondition(), new Bounds(), locals);
                iteration.assume(loop.getCondition(), true);
                statement(loop.getBody(), iteration, new HashSet<>(locals));
                bounds.clear();
            } else if (statement instanceof TryStmt attempt) {
                block(attempt.getTryBlock(), new Bounds(), locals);
                for (CatchClause handler : attempt.getCatchClauses()) {
                    Set<String> handlerLocals = new HashSet<>(locals);
                    handlerLocals.add(handler.getParameter());
                    block(handler.getBody(), new Bounds(), handlerLocals);
                }
                if (attempt.getFinallyBlock() != null) block(attempt.getFinallyBlock(), new Bounds(), locals);
                bounds.clear();
            } else if (statement instanceof ReturnStmt returned) {
                bounds = expression(returned.getValue(), bounds, locals);
                bounds.reachable = false;
            } else if (statement instanceof ThrowStmt thrown) {
                bounds = expression(thrown.getValue(), bounds, locals);
                bounds.reachable = false;
            } else if (statement instanceof BreakStmt || statement instanceof ContinueStmt) {
                bounds.reachable = false;
            } else {
                bounds.clear();
            }
            return bounds;
        }

        private Bounds expression(Expression expr, Bounds bounds, Set<String> locals) {
            if (expr == null || expr instanceof LiteralExpr || expr instanceof VariableExpr || expr instanceof ThisExpr) {
                return bounds;
            }
            if (expr instanceof AssignmentExpr assign) {
                bounds = expression(assign.getValue(), bounds, locals);
                String name = assign.getName().getLexeme();
                if (storage.contains(name) && !locals.contains(name)) {
                    writes.add(new Write(name, assign.getValue(), assign.getSourceLocation(), new Bounds(bounds)));
                }
                bounds.forget(name);
            } else if (expr instanceof SetExpr set) {
                bounds = expression(set.getObject(), bounds, locals);
                bounds = expression(set.getValue(), bounds, locals);
                String name = set.getName().getLexeme();
                if (set.getObject() instanceof ThisExpr && storage.contains(name)) {
                    writes.add(new Write(name, set.getValue(), set.getSourceLocation(), new Bounds(bounds)));
                    bounds.forget(name);
                } else {
                    bounds.clear();
                }
            } else if (expr instanceof PrefixIncrementExpr increment) {
                increment(increment.getTarget(), increment.isIncrement(), increment.getSourceLocation(), bounds, locals);
            } else if (expr instanceof PostfixIncrementExpr increment) {
                increment(increment.getTarget(), increment.isIncrement(), increment.getSourceLocation(), bounds, locals);
            } else if (expr instanceof BinaryExpr binary) {
                bounds = expression(binary.getLeft(), bounds, locals);
                bounds = expression(binary.getRight(), bounds, locals);
            } else if (expr instanceof UnaryExpr unary) {
                bounds = expression(unary.getRight(), bounds, locals);
            } else if (expr instanceof GetExpr get) {
                bounds = expression(get.getObject(), bounds, locals);
            } else if (expr instanceof CallExpr call) {
                bounds = expression(call.getCallee(), bounds, locals);
                for (Expression argument : call.getArguments()) bounds = expression(argument, bounds, locals);
                bounds.clear();
            } else if (expr instanceof TernaryExpr conditional) {
                bounds = expression(conditional.getCondition(), bounds, locals);
                Bounds yes = new Bounds(bounds);
                Bounds no = new Bounds(bounds);
                yes.assume(conditional.getCondition(), true);
                no.assume(conditional.getCondition(), false);
                return join(expression(conditional.getThenBranch(), yes, locals),
                        expression(conditional.getElseBranch(), no, locals));
            } else if (expr instanceof IndexAssignExpr index) {
                bounds = expression(index.getObject(), bounds, locals);
                bounds = expression(index.getIndex(), bounds, locals);
                bounds = expression(index.getValue(), bounds, locals);
                bounds.clear();
            } else if (expr instanceof IndexExpr index) {
                bounds = expression(index.getObject(), bounds, locals);
                bounds = expression(index.getIndex(), bounds, locals);
            } else {
                bounds.clear();
            }
            return bounds;
        }

        private void increment(Expression target, boolean addition, SourceLocation location,
                               Bounds bounds, Set<String> locals) {
            String name = null;
            if (target instanceof VariableExpr variable && !locals.contains(variable.getName().getLexeme())) {
                name = variable.getName().getLexeme();
            } else if (target instanceof GetExpr get && get.getObject() instanceof ThisExpr) {
                name = get.getName().getLexeme();
            }
            if (name != null && storage.contains(name)) {
                BinaryExpr value = new BinaryExpr(target,
                        new Token(addition ? TokenType.PLUS : TokenType.MINUS, addition ? "+" : "-", 0),
                        new LiteralExpr(1L));
                writes.add(new Write(name, value, location, new Bounds(bounds)));
            }
            if (target instanceof VariableExpr variable) bounds.forget(variable.getName().getLexeme());
            else if (name != null) bounds.forget(name);
            else bounds.clear();
        }
    }
}
