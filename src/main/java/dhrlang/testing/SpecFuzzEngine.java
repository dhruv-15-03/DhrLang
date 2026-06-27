package dhrlang.testing;

import dhrlang.ast.*;
import dhrlang.lexer.TokenType;

import java.math.BigInteger;
import java.util.*;

/**
 * Concrete uint256 evaluator that executes a smart-contract function body over a
 * simulated EVM-style state and checks its design-by-contract specifications
 * (the L2a {@code @requires} / {@code @ensures} / {@code @invariant} annotations).
 *
 * <p>This is the execution core behind {@link ContractFuzzer} (provable-safety
 * level L3). Its purpose is to find concrete inputs for which a function's
 * <em>logic</em> falsifies a declared specification — a counterexample — in the
 * spirit of Foundry/Echidna property fuzzing.
 *
 * <h2>Soundness</h2>
 * The engine is deliberately conservative: it only ever reports a
 * {@link Outcome#VIOLATION} after faithfully executing the body and finding a
 * spec predicate that is concretely {@code false}. Anything it cannot model
 * precisely (unsupported statements/expressions, external calls, non-numeric
 * values, unbounded loops) yields {@link Outcome#UNSUPPORTED}, never a false
 * positive. A run whose preconditions are not met yields
 * {@link Outcome#PRECONDITION_SKIP}; a run that reverts (failed
 * {@code require}/{@code revert}, or checked-arithmetic overflow) yields
 * {@link Outcome#REVERT}. Reverts and skips are acceptable behaviour, not bugs.
 *
 * <h2>State model</h2>
 * Each run starts from the canonical post-construction <em>genesis</em> state
 * (all storage scalars {@code 0}, all mappings empty), binds the function's
 * parameters to the fuzzed arguments, and executes a single call. Storage
 * fields are modelled as 256-bit unsigned integers ({@link BigInteger} reduced
 * mod 2<sup>256</sup>); index access on a field is modelled as a sparse mapping
 * defaulting to {@code 0}.
 *
 * <p>The supported statement/expression subset mirrors {@code EvmCodeGen}'s
 * contract subset; arithmetic uses the same unsigned, wrapping (or
 * {@code @checked}-reverting) semantics as the EVM backend.
 */
public final class SpecFuzzEngine {

    /** Result of executing one function call and checking its specs. */
    public enum Outcome { OK, PRECONDITION_SKIP, REVERT, VIOLATION, UNSUPPORTED }

    /** The outcome of a single run together with a human-readable detail. */
    public static final class Result {
        public final Outcome outcome;
        public final String detail;
        public Result(Outcome outcome, String detail) {
            this.outcome = outcome;
            this.detail = detail;
        }
    }

    // ── uint256 constants ────────────────────────────────────────────────
    private static final BigInteger TWO_256 = BigInteger.ONE.shiftLeft(256);
    private static final BigInteger MASK_256 = TWO_256.subtract(BigInteger.ONE);

    /**
     * Mirrors {@code EvmCodeGen.CHECKED_ARITHMETIC_BY_DEFAULT}. While the EVM
     * backend defaults to wrapping arithmetic (flipped to checked for v4.0.0),
     * the engine matches that default so fuzzing reflects emitted bytecode.
     */
    private static final boolean CHECKED_ARITHMETIC_BY_DEFAULT = false;

    private static final int MAX_LOOP_ITERATIONS = 100_000;

    /** A fixed, non-zero caller address used for {@code msg.sender}. */
    private static final BigInteger DEFAULT_SENDER =
            BigInteger.valueOf(0x1111111111111111L);

    // ── Control-flow signals ─────────────────────────────────────────────
    private static final class RevertSignal extends RuntimeException {
        RevertSignal(String m) { super(m, null, false, false); }
    }
    private static final class ReturnSignal extends RuntimeException {
        final BigInteger value; final boolean hasValue;
        ReturnSignal(BigInteger value, boolean hasValue) {
            super(null, null, false, false);
            this.value = value; this.hasValue = hasValue;
        }
    }
    private static final class BreakSignal extends RuntimeException {
        BreakSignal() { super(null, null, false, false); }
    }
    private static final class ContinueSignal extends RuntimeException {
        ContinueSignal() { super(null, null, false, false); }
    }
    /** Thrown when a construct cannot be modelled precisely (→ skip, never a bug). */
    private static final class UnsupportedConstruct extends RuntimeException {
        UnsupportedConstruct(String m) { super(m, null, false, false); }
    }

    // ── Immutable contract context ───────────────────────────────────────
    private final ClassDecl cls;
    private final Set<String> storageFields = new HashSet<>();

    // ── Per-run mutable state ────────────────────────────────────────────
    private Map<String, BigInteger> storage;
    private Map<String, Map<BigInteger, BigInteger>> mappings;
    private final Deque<Map<String, BigInteger>> scopes = new ArrayDeque<>();
    private BigInteger msgSender = DEFAULT_SENDER;
    private BigInteger msgValue = BigInteger.ZERO;
    private BigInteger resultValue;
    private boolean hasResultValue;
    private boolean checkedArithmetic;

    public SpecFuzzEngine(ClassDecl cls) {
        this.cls = Objects.requireNonNull(cls, "cls");
        for (VarDecl v : cls.getVariables()) {
            storageFields.add(v.getName());
        }
    }

    /**
     * Execute {@code fn} once with the supplied arguments from the genesis
     * state and check all applicable specifications.
     *
     * @param fn   the function under test
     * @param args fuzzed arguments, positionally matching {@code fn.getParameters()}
     * @return the run outcome and a descriptive detail
     */
    public Result run(FunctionDecl fn, List<Object> args) {
        // Fresh genesis state for every run.
        storage = new HashMap<>();
        mappings = new HashMap<>();
        scopes.clear();
        msgSender = DEFAULT_SENDER;
        msgValue = BigInteger.ZERO;
        resultValue = null;
        hasResultValue = false;
        checkedArithmetic = resolveChecked(fn);

        try {
            // The genesis state must satisfy the contract invariants, otherwise
            // we cannot attribute a later violation to this function call.
            for (Expression inv : cls.getInvariants()) {
                if (!truth(eval(inv))) {
                    return new Result(Outcome.PRECONDITION_SKIP,
                            "invariant not established at genesis: " + Unparser.of(inv));
                }
            }

            // Bind parameters to fuzzed arguments.
            Map<String, BigInteger> frame = new HashMap<>();
            List<VarDecl> params = fn.getParameters();
            if (args.size() != params.size()) {
                return new Result(Outcome.UNSUPPORTED, "argument count mismatch");
            }
            for (int i = 0; i < params.size(); i++) {
                frame.put(params.get(i).getName(), toUint256(args.get(i)));
            }
            scopes.push(frame);

            // Preconditions filter the input domain (assume-style): an unmet
            // precondition means the input is simply out of scope, not a bug.
            for (Expression pre : fn.getRequires()) {
                if (!truth(eval(pre))) {
                    return new Result(Outcome.PRECONDITION_SKIP,
                            "precondition not met: " + Unparser.of(pre));
                }
            }

            // Execute the body (pure logic — the L2a revert guards live in the
            // EVM lowering, not in the AST, so we observe the raw computation).
            try {
                execute(fn.getBody());
            } catch (ReturnSignal ret) {
                if (ret.hasValue) {
                    resultValue = ret.value;
                    hasResultValue = true;
                }
            }

            // Postconditions may reference the bound `result`.
            for (Expression post : fn.getEnsures()) {
                if (!truth(eval(post))) {
                    return new Result(Outcome.VIOLATION,
                            "postcondition violated: @ensures(" + Unparser.of(post) + ")");
                }
            }

            // Contract invariants must hold after any state-mutating call.
            for (Expression inv : cls.getInvariants()) {
                if (!truth(eval(inv))) {
                    return new Result(Outcome.VIOLATION,
                            "invariant violated: @invariant(" + Unparser.of(inv) + ")");
                }
            }

            return new Result(Outcome.OK, "specifications hold");

        } catch (RevertSignal r) {
            return new Result(Outcome.REVERT, r.getMessage() == null ? "revert" : r.getMessage());
        } catch (UnsupportedConstruct u) {
            return new Result(Outcome.UNSUPPORTED, u.getMessage());
        } catch (ArithmeticException e) {
            // e.g. a degenerate BigInteger op we did not anticipate — stay sound.
            return new Result(Outcome.UNSUPPORTED, "arithmetic: " + e.getMessage());
        }
    }

    private boolean resolveChecked(FunctionDecl fn) {
        Set<ContractAnnotation> ann = fn.getContractAnnotations();
        if (ann.contains(ContractAnnotation.CHECKED)) return true;
        if (ann.contains(ContractAnnotation.UNCHECKED)) return false;
        return CHECKED_ARITHMETIC_BY_DEFAULT;
    }

    // ── Statement execution ──────────────────────────────────────────────

    private void execute(Statement stmt) {
        if (stmt == null) return;
        if (stmt instanceof Block b) {
            scopes.push(new HashMap<>());
            try {
                for (Statement s : b.getStatements()) execute(s);
            } finally {
                scopes.pop();
            }
        } else if (stmt instanceof VarDecl v) {
            BigInteger value = v.getInitializer() != null
                    ? eval(v.getInitializer()) : BigInteger.ZERO;
            scopes.peek().put(v.getName(), value);
        } else if (stmt instanceof ExpressionStmt es) {
            eval(es.getExpression());
        } else if (stmt instanceof IfStmt iff) {
            if (truth(eval(iff.getCondition()))) {
                execute(iff.getThenBranch());
            } else if (iff.getElseBranch() != null) {
                execute(iff.getElseBranch());
            }
        } else if (stmt instanceof WhileStmt w) {
            int guard = 0;
            while (truth(eval(w.getCondition()))) {
                if (++guard > MAX_LOOP_ITERATIONS) {
                    throw new UnsupportedConstruct("loop exceeded iteration bound");
                }
                try {
                    execute(w.getBody());
                } catch (BreakSignal br) {
                    break;
                } catch (ContinueSignal ct) {
                    // continue
                }
            }
        } else if (stmt instanceof ReturnStmt r) {
            if (r.getValue() != null) {
                throw new ReturnSignal(eval(r.getValue()), true);
            }
            throw new ReturnSignal(null, false);
        } else if (stmt instanceof BreakStmt) {
            throw new BreakSignal();
        } else if (stmt instanceof ContinueStmt) {
            throw new ContinueSignal();
        } else if (stmt instanceof PrintStmt) {
            // No observable effect on contract state — ignore.
        } else {
            throw new UnsupportedConstruct(
                    "statement: " + stmt.getClass().getSimpleName());
        }
    }

    // ── Expression evaluation (booleans are 0/1) ─────────────────────────

    private BigInteger eval(Expression expr) {
        if (expr instanceof LiteralExpr lit) {
            Object v = lit.getValue();
            if (v instanceof Long l) return wrap(BigInteger.valueOf(l));
            if (v instanceof Integer i) return wrap(BigInteger.valueOf(i));
            if (v instanceof Boolean b) return b ? BigInteger.ONE : BigInteger.ZERO;
            if (v instanceof BigInteger bi) return wrap(bi);
            throw new UnsupportedConstruct("literal: " + v);
        }
        if (expr instanceof VariableExpr ve) {
            return readVariable(ve.getName().getLexeme());
        }
        if (expr instanceof BinaryExpr be) {
            return evalBinary(be);
        }
        if (expr instanceof UnaryExpr ue) {
            BigInteger r = eval(ue.getRight());
            return switch (ue.getOperator().getType()) {
                case MINUS -> wrap(r.negate());
                case NOT -> truth(r) ? BigInteger.ZERO : BigInteger.ONE;
                case BIT_NOT -> wrap(r.xor(MASK_256));
                default -> throw new UnsupportedConstruct(
                        "unary operator: " + ue.getOperator().getLexeme());
            };
        }
        if (expr instanceof TernaryExpr te) {
            return truth(eval(te.getCondition()))
                    ? eval(te.getThenBranch()) : eval(te.getElseBranch());
        }
        if (expr instanceof AssignmentExpr ae) {
            BigInteger value = eval(ae.getValue());
            assignName(ae.getName().getLexeme(), value);
            return value;
        }
        if (expr instanceof SetExpr se) {
            String field = fieldOf(se.getObject(), se.getName().getLexeme());
            BigInteger value = eval(se.getValue());
            storage.put(field, value);
            return value;
        }
        if (expr instanceof GetExpr ge) {
            return evalGet(ge);
        }
        if (expr instanceof IndexExpr ie) {
            String field = mappingNameOf(ie.getObject());
            BigInteger key = eval(ie.getIndex());
            return mappings.getOrDefault(field, Collections.emptyMap())
                    .getOrDefault(key, BigInteger.ZERO);
        }
        if (expr instanceof IndexAssignExpr ia) {
            String field = mappingNameOf(ia.getObject());
            BigInteger key = eval(ia.getIndex());
            BigInteger value = eval(ia.getValue());
            mappings.computeIfAbsent(field, k -> new HashMap<>()).put(key, value);
            return value;
        }
        if (expr instanceof PrefixIncrementExpr pre) {
            BigInteger delta = pre.isIncrement() ? BigInteger.ONE : BigInteger.ONE.negate();
            BigInteger updated = wrap(evalLValue(pre.getTarget()).add(delta));
            storeLValue(pre.getTarget(), updated);
            return updated;
        }
        if (expr instanceof PostfixIncrementExpr post) {
            BigInteger old = evalLValue(post.getTarget());
            BigInteger delta = post.isIncrement() ? BigInteger.ONE : BigInteger.ONE.negate();
            storeLValue(post.getTarget(), wrap(old.add(delta)));
            return old;
        }
        if (expr instanceof CallExpr ce) {
            return evalCall(ce);
        }
        if (expr instanceof ThisExpr) {
            throw new UnsupportedConstruct("bare 'this'");
        }
        throw new UnsupportedConstruct("expression: " + expr.getClass().getSimpleName());
    }

    private BigInteger evalBinary(BinaryExpr be) {
        TokenType op = be.getOperator().getType();
        // Short-circuit logical operators.
        if (op == TokenType.AND) {
            return truth(eval(be.getLeft())) && truth(eval(be.getRight()))
                    ? BigInteger.ONE : BigInteger.ZERO;
        }
        if (op == TokenType.OR) {
            return truth(eval(be.getLeft())) || truth(eval(be.getRight()))
                    ? BigInteger.ONE : BigInteger.ZERO;
        }
        BigInteger a = eval(be.getLeft());
        BigInteger b = eval(be.getRight());
        return switch (op) {
            case PLUS -> {
                BigInteger sum = a.add(b);
                if (checkedArithmetic && sum.compareTo(TWO_256) >= 0) {
                    throw new RevertSignal("arithmetic overflow");
                }
                yield wrap(sum);
            }
            case MINUS -> {
                if (checkedArithmetic && b.compareTo(a) > 0) {
                    throw new RevertSignal("arithmetic underflow");
                }
                yield wrap(a.subtract(b));
            }
            case STAR -> {
                BigInteger prod = a.multiply(b);
                if (checkedArithmetic && prod.compareTo(TWO_256) >= 0) {
                    throw new RevertSignal("arithmetic overflow");
                }
                yield wrap(prod);
            }
            case SLASH -> b.signum() == 0 ? BigInteger.ZERO : a.divide(b);
            case MOD -> b.signum() == 0 ? BigInteger.ZERO : a.mod(b);
            case EQUALITY -> a.equals(b) ? BigInteger.ONE : BigInteger.ZERO;
            case NEQ -> a.equals(b) ? BigInteger.ZERO : BigInteger.ONE;
            case LESS -> a.compareTo(b) < 0 ? BigInteger.ONE : BigInteger.ZERO;
            case GREATER -> a.compareTo(b) > 0 ? BigInteger.ONE : BigInteger.ZERO;
            case LEQ -> a.compareTo(b) <= 0 ? BigInteger.ONE : BigInteger.ZERO;
            case GEQ -> a.compareTo(b) >= 0 ? BigInteger.ONE : BigInteger.ZERO;
            case BIT_AND -> a.and(b);
            case BIT_OR -> a.or(b);
            case BIT_XOR -> a.xor(b);
            case LSHIFT -> b.compareTo(BigInteger.valueOf(256)) >= 0
                    ? BigInteger.ZERO : wrap(a.shiftLeft(b.intValue()));
            case RSHIFT -> b.compareTo(BigInteger.valueOf(256)) >= 0
                    ? BigInteger.ZERO : a.shiftRight(b.intValue());
            default -> throw new UnsupportedConstruct(
                    "binary operator: " + be.getOperator().getLexeme());
        };
    }

    private BigInteger evalGet(GetExpr ge) {
        if (ge.getObject() instanceof VariableExpr ve) {
            String obj = ve.getName().getLexeme();
            String member = ge.getName().getLexeme();
            if ("msg".equals(obj)) {
                if ("sender".equals(member)) return msgSender;
                if ("value".equals(member)) return msgValue;
            }
        }
        if (ge.getObject() instanceof ThisExpr) {
            return readStorage(ge.getName().getLexeme());
        }
        throw new UnsupportedConstruct("member access: " + Unparser.of(ge));
    }

    private BigInteger evalCall(CallExpr ce) {
        if (ce.getCallee() instanceof VariableExpr ve) {
            String name = ve.getName().getLexeme();
            List<Expression> a = ce.getArguments();
            switch (name) {
                case "require", "assert" -> {
                    boolean ok = !a.isEmpty() && truth(eval(a.get(0)));
                    if (!ok) {
                        throw new RevertSignal(name + " failed");
                    }
                    return BigInteger.ONE;
                }
                case "revert" -> throw new RevertSignal("revert");
                default -> throw new UnsupportedConstruct("call: " + name + "(...)");
            }
        }
        throw new UnsupportedConstruct("call expression");
    }

    // ── l-value helpers (for ++/-- targets) ──────────────────────────────

    private BigInteger evalLValue(Expression target) {
        if (target instanceof VariableExpr ve) return readVariable(ve.getName().getLexeme());
        if (target instanceof GetExpr ge && ge.getObject() instanceof ThisExpr) {
            return readStorage(ge.getName().getLexeme());
        }
        if (target instanceof IndexExpr ie) {
            String field = mappingNameOf(ie.getObject());
            BigInteger key = eval(ie.getIndex());
            return mappings.getOrDefault(field, Collections.emptyMap())
                    .getOrDefault(key, BigInteger.ZERO);
        }
        throw new UnsupportedConstruct("increment target");
    }

    private void storeLValue(Expression target, BigInteger value) {
        if (target instanceof VariableExpr ve) {
            assignName(ve.getName().getLexeme(), value);
            return;
        }
        if (target instanceof GetExpr ge && ge.getObject() instanceof ThisExpr) {
            storage.put(ge.getName().getLexeme(), value);
            return;
        }
        if (target instanceof IndexExpr ie) {
            String field = mappingNameOf(ie.getObject());
            BigInteger key = eval(ie.getIndex());
            mappings.computeIfAbsent(field, k -> new HashMap<>()).put(key, value);
            return;
        }
        throw new UnsupportedConstruct("increment target");
    }

    // ── name / storage resolution ────────────────────────────────────────

    private BigInteger readVariable(String name) {
        for (Map<String, BigInteger> scope : scopes) {
            BigInteger v = scope.get(name);
            if (v != null) return v;
        }
        if ("result".equals(name)) {
            if (hasResultValue) return resultValue;
            throw new UnsupportedConstruct("'result' referenced with no scalar return value");
        }
        if (storageFields.contains(name)) {
            return readStorage(name);
        }
        throw new UnsupportedConstruct("unknown identifier: " + name);
    }

    private BigInteger readStorage(String name) {
        if (!storageFields.contains(name)) {
            throw new UnsupportedConstruct("unknown storage field: " + name);
        }
        return storage.getOrDefault(name, BigInteger.ZERO);
    }

    private void assignName(String name, BigInteger value) {
        for (Map<String, BigInteger> scope : scopes) {
            if (scope.containsKey(name)) {
                scope.put(name, value);
                return;
            }
        }
        if (storageFields.contains(name)) {
            storage.put(name, value);
            return;
        }
        // A first assignment to a bare local that was never declared with a type.
        Map<String, BigInteger> top = scopes.peek();
        if (top != null) {
            top.put(name, value);
            return;
        }
        throw new UnsupportedConstruct("assignment to unknown name: " + name);
    }

    private String fieldOf(Expression object, String member) {
        if (object instanceof ThisExpr) return member;
        if (object instanceof VariableExpr ve && "this".equals(ve.getName().getLexeme())) {
            return member;
        }
        throw new UnsupportedConstruct("field assignment on non-this target");
    }

    private String mappingNameOf(Expression object) {
        if (object instanceof VariableExpr ve) {
            String name = ve.getName().getLexeme();
            if (storageFields.contains(name)) return name;
        }
        if (object instanceof GetExpr ge && ge.getObject() instanceof ThisExpr) {
            return ge.getName().getLexeme();
        }
        throw new UnsupportedConstruct("indexed access on non-storage target");
    }

    // ── uint256 helpers ──────────────────────────────────────────────────

    /** Reduce any integer into the unsigned 256-bit range [0, 2^256). */
    private static BigInteger wrap(BigInteger x) {
        BigInteger m = x.mod(TWO_256);   // BigInteger.mod always returns >= 0
        return m;
    }

    private static boolean truth(BigInteger v) {
        return v.signum() != 0;
    }

    private static BigInteger toUint256(Object arg) {
        if (arg instanceof BigInteger bi) return wrap(bi);
        if (arg instanceof Long l) return wrap(BigInteger.valueOf(l));
        if (arg instanceof Integer i) return wrap(BigInteger.valueOf(i));
        if (arg instanceof Boolean b) return b ? BigInteger.ONE : BigInteger.ZERO;
        if (arg instanceof String s) {
            String t = s.trim();
            if (t.startsWith("0x") || t.startsWith("0X")) {
                try {
                    return wrap(new BigInteger(t.substring(2), 16));
                } catch (NumberFormatException nfe) {
                    throw new UnsupportedConstruct("non-hex address/bytes argument");
                }
            }
            throw new UnsupportedConstruct("non-numeric string argument");
        }
        throw new UnsupportedConstruct(
                "argument type: " + (arg == null ? "null" : arg.getClass().getSimpleName()));
    }

    // ── Minimal expression pretty-printer (for counterexample messages) ──

    /**
     * Render a spec expression the same way this engine labels its
     * {@link Outcome#VIOLATION} details, so a static prover (the L2b
     * {@code SpecProver}) can match its proof obligations against the concrete
     * counterexamples this engine reports. Returns the canonical unparse used
     * inside {@code @ensures(...)} / {@code @invariant(...)} violation messages.
     */
    public static String describe(Expression e) {
        return Unparser.of(e);
    }

    private static final class Unparser {
        static String of(Expression e) {
            if (e == null) return "?";
            if (e instanceof LiteralExpr l) return String.valueOf(l.getValue());
            if (e instanceof VariableExpr v) return v.getName().getLexeme();
            if (e instanceof BinaryExpr b) {
                return "(" + of(b.getLeft()) + " " + b.getOperator().getLexeme()
                        + " " + of(b.getRight()) + ")";
            }
            if (e instanceof UnaryExpr u) {
                return u.getOperator().getLexeme() + of(u.getRight());
            }
            if (e instanceof GetExpr g) {
                return of(g.getObject()) + "." + g.getName().getLexeme();
            }
            if (e instanceof IndexExpr ix) {
                return of(ix.getObject()) + "[" + of(ix.getIndex()) + "]";
            }
            if (e instanceof ThisExpr) return "this";
            if (e instanceof TernaryExpr t) {
                return "(" + of(t.getCondition()) + " ? " + of(t.getThenBranch())
                        + " : " + of(t.getElseBranch()) + ")";
            }
            if (e instanceof CallExpr c) {
                StringBuilder sb = new StringBuilder(of(c.getCallee())).append('(');
                for (int i = 0; i < c.getArguments().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(of(c.getArguments().get(i)));
                }
                return sb.append(')').toString();
            }
            return e.getClass().getSimpleName();
        }
    }
}
