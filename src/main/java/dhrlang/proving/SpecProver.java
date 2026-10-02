package dhrlang.proving;

import dhrlang.ast.*;
import dhrlang.lexer.TokenType;
import dhrlang.testing.SpecFuzzEngine;

import java.math.BigInteger;
import java.util.*;

/**
 * Static, universal discharge of design-by-contract specifications — provable
 * safety level <strong>L2b</strong> (experimental). Where {@link SpecFuzzEngine}
 * (L3) <em>samples</em> inputs looking for a counterexample, {@code SpecProver}
 * attempts to <em>prove</em> a function's {@code @ensures} postconditions and a
 * contract's {@code @invariant}s hold for <em>all</em> inputs, by symbolic
 * execution plus a hand-rolled decision procedure for the quantifier-free linear
 * integer-arithmetic fragment DhrLang specs use.
 *
 * <p>The implementation is deliberately dependency-free (no Z3/JavaSMT), matching
 * this codebase's hand-rolled precedent (keccak, RLP, broadcast JSON, the L3
 * concrete evaluator). It is small but <em>sound</em>: it never reports
 * {@code PROVED} or {@code REFUTED} unless it has a genuine proof / a confirmed
 * concrete counterexample.
 *
 * <h2>Model</h2>
 * Each function is analysed from the canonical post-construction <em>genesis</em>
 * state (storage scalars {@code 0}); parameters become fresh non-negative 256-bit
 * symbols. The body is symbolically executed: straight-line code, local
 * declarations/assignments, scalar storage writes, {@code if}/{@code else}
 * (path-forking), and {@code require}/{@code assert}/{@code revert}
 * (assume / prune). Loops, external calls and anything the linear theory cannot
 * model render the affected obligation {@code UNKNOWN} — never a false claim.
 *
 * <p>For every non-reverting terminal path the prover forms the Hoare obligation
 * {@code (requires ∧ path-conditions ∧ atomNonNeg) ⇒ goal} and decides validity
 * by refuting {@code hypotheses ∧ ¬goal} with <em>Fourier–Motzkin elimination</em>
 * over the rationals (rational UNSAT ⇒ integer UNSAT, so the result is sound).
 *
 * <h2>Soundness of {@code PROVED} under checked arithmetic</h2>
 * The linear theory models {@code +,-,*} as <em>mathematical</em> integers. That
 * is sound only when overflow cannot silently wrap: under {@code @checked} (or a
 * future checked-by-default), an overflowing operation reverts, so on every
 * non-reverting path the values equal their exact mathematical results and the
 * proof transfers. For {@code @unchecked} / wrapping functions proving is
 * disabled (obligations become {@code UNKNOWN}); refutation still runs.
 *
 * <h2>{@code REFUTED} is cross-checked</h2>
 * A counterexample is only reported after {@link SpecFuzzEngine} concretely
 * confirms the violation for the discovered arguments, so there are zero false
 * positives — the prover reuses the L3 engine as its ground-truth oracle.
 */
public final class SpecProver {

    /** Status of a single proof obligation. */
    public enum Status { PROVED, REFUTED, UNKNOWN }

    /** Mirrors {@code EvmCodeGen.CHECKED_ARITHMETIC_BY_DEFAULT} / the L3 engine (checked-by-default as of v4.0.0). */
    private static final boolean CHECKED_ARITHMETIC_BY_DEFAULT = true;

    private static final BigInteger TWO_128 = BigInteger.ONE.shiftLeft(128);
    private static final BigInteger MAX_256 = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);
    private static final LinearForm ONE = LinearForm.constant(1);

    private static final int MAX_PATHS = 256;
    private static final int FM_ROW_CAP = 6000;
    private static final long REFUTE_COMBO_CAP = 200_000L;

    // ── Public result types ──────────────────────────────────────────────

    /** One proof obligation (a single {@code @ensures} or {@code @invariant}). */
    public static final class Obligation {
        public final String kind;     // "@ensures" | "@invariant"
        public final String spec;     // unparsed predicate
        public final Status status;
        public final String detail;   // counterexample (REFUTED) or reason (UNKNOWN)
        Obligation(String kind, String spec, Status status, String detail) {
            this.kind = kind; this.spec = spec; this.status = status; this.detail = detail;
        }
    }

    /** All obligations proved/refuted for one function. */
    public static final class FunctionProof {
        public final String contract;
        public final String signature;
        public final boolean checked;
        public final List<Obligation> obligations;
        FunctionProof(String contract, String signature, boolean checked, List<Obligation> obs) {
            this.contract = contract; this.signature = signature;
            this.checked = checked; this.obligations = obs;
        }
    }

    private final Program program;
    private final List<FunctionProof> proofs = new ArrayList<>();
    private int proved, refuted, unknown;
    private int bound = 8;

    public SpecProver(Program program) {
        this.program = Objects.requireNonNull(program, "program");
    }

    /** Set the bounded-refutation search radius (per-parameter, default 8). */
    public SpecProver setBound(int bound) {
        if (bound >= 0) this.bound = bound;
        return this;
    }

    public List<FunctionProof> getProofs() { return proofs; }
    public int getProvedCount()  { return proved; }
    public int getRefutedCount() { return refuted; }
    public int getUnknownCount() { return unknown; }

    /** {@code true} if any obligation was refuted (a real bug → CI gate fails). */
    public boolean hasRefutations() { return refuted > 0; }

    /** {@code true} if the program declared no provable obligations at all. */
    public boolean isEmpty() { return proofs.isEmpty(); }

    // ── Driver ───────────────────────────────────────────────────────────

    /** Analyse every contract/function and populate the proof results. */
    public void proveAll() {
        proofs.clear();
        proved = refuted = unknown = 0;
        for (ClassDecl cls : program.getClasses()) {
            for (FunctionDecl fn : discoverProveTargets(cls)) {
                FunctionProof fp = proveFunction(cls, fn);
                if (fp != null) proofs.add(fp);
            }
        }
    }

    /** Functions worth proving: skip lifecycle / test / invariant-marker methods. */
    private List<FunctionDecl> discoverProveTargets(ClassDecl cls) {
        List<FunctionDecl> targets = new ArrayList<>();
        for (FunctionDecl fn : cls.getFunctions()) {
            Set<ContractAnnotation> a = fn.getContractAnnotations();
            boolean skip = a.contains(ContractAnnotation.TEST)
                    || a.contains(ContractAnnotation.BEFORE_EACH)
                    || a.contains(ContractAnnotation.AFTER_EACH)
                    || a.contains(ContractAnnotation.CONSTRUCTOR)
                    || a.contains(ContractAnnotation.EVENT)
                    || a.contains(ContractAnnotation.ERROR)
                    || a.contains(ContractAnnotation.INVARIANT);
            if (!skip) targets.add(fn);
        }
        return targets;
    }

    private boolean resolveChecked(FunctionDecl fn) {
        Set<ContractAnnotation> a = fn.getContractAnnotations();
        if (a.contains(ContractAnnotation.CHECKED)) return true;
        if (a.contains(ContractAnnotation.UNCHECKED)) return false;
        return CHECKED_ARITHMETIC_BY_DEFAULT;
    }

    private FunctionProof proveFunction(ClassDecl cls, FunctionDecl fn) {
        boolean stateMutating = !fn.getContractAnnotations().contains(ContractAnnotation.VIEW)
                && !fn.getContractAnnotations().contains(ContractAnnotation.PURE);

        // Collect obligations: every @ensures, plus contract invariants for
        // state-mutating functions.
        List<Expression> ensures = fn.getEnsures();
        List<Expression> invariants = stateMutating ? cls.getInvariants() : List.of();
        if (ensures.isEmpty() && invariants.isEmpty()) {
            return null; // nothing to prove
        }

        boolean checked = resolveChecked(fn);
        Set<String> storageFields = new HashSet<>();
        for (VarDecl v : cls.getVariables()) storageFields.add(v.getName());

        // Symbolically execute to a set of terminal paths.
        List<Path> paths;
        try {
            paths = explore(fn, storageFields);
        } catch (TooManyPaths e) {
            paths = null; // too branchy to enumerate -> everything UNKNOWN
        }

        // Build obligation descriptors.
        List<Obl> obls = new ArrayList<>();
        for (Expression e : ensures) obls.add(new Obl("@ensures", e));
        for (Expression e : invariants) obls.add(new Obl("@invariant", e));

        // Lazily computed refutation map (label -> counterexample string).
        Map<String, String> refutations = null;

        List<Obligation> results = new ArrayList<>();
        for (Obl obl : obls) {
            String label = SpecFuzzEngine.describe(obl.expr);
            Status status;
            String detail = null;

            boolean provedHere = checked && paths != null && provedOnAllPaths(obl, paths, storageFields);
            if (provedHere) {
                status = Status.PROVED;
            } else {
                if (refutations == null) {
                    refutations = refute(cls, fn);
                }
                String cex = refutations.get(label);
                if (cex != null) {
                    status = Status.REFUTED;
                    detail = cex;
                } else {
                    status = Status.UNKNOWN;
                    detail = !checked
                            ? "proof requires @checked arithmetic"
                            : (paths == null ? "control flow too complex"
                                             : "not provable by linear arithmetic");
                }
            }

            switch (status) {
                case PROVED -> proved++;
                case REFUTED -> refuted++;
                case UNKNOWN -> unknown++;
            }
            results.add(new Obligation(obl.kind, label, status, detail));
        }

        return new FunctionProof(cls.getName(), signatureOf(fn), checked, results);
    }

    private static final class Obl {
        final String kind; final Expression expr;
        Obl(String kind, Expression expr) { this.kind = kind; this.expr = expr; }
    }

    /** An obligation is PROVED only if it holds on every non-reverting path. */
    private boolean provedOnAllPaths(Obl obl, List<Path> paths, Set<String> storageFields) {
        boolean any = false;
        for (Path p : paths) {
            if (p.dead) continue;
            any = true;
            if (p.unknown) return false;
            if (!proveGoal(p, obl.expr, true, storageFields)) return false;
        }
        return any;
    }

    // ── Symbolic execution ───────────────────────────────────────────────

    private static final class TooManyPaths extends RuntimeException {
        TooManyPaths() { super(null, null, false, false); }
    }
    private static final class Unmodelable extends RuntimeException {
        final String reason;
        Unmodelable(String reason) { super(null, null, false, false); this.reason = reason; }
    }

    private static final class MapWrite {
        final String keyCanon; final LinearForm value;
        MapWrite(String keyCanon, LinearForm value) { this.keyCanon = keyCanon; this.value = value; }
    }

    /** Mutable symbolic state for one execution path. */
    private static final class Path {
        Map<String, LinearForm> locals = new HashMap<>();
        Map<String, LinearForm> storage = new HashMap<>();
        Map<String, List<MapWrite>> mapWrites = new HashMap<>();
        List<LinearForm> hyps = new ArrayList<>(); // each meaning ">= 0"
        boolean hasResult; LinearForm result;
        boolean dead;     // reverted -> contributes no obligation
        boolean unknown;  // could not be modelled precisely

        Path copy() {
            Path p = new Path();
            p.locals = new HashMap<>(locals);
            p.storage = new HashMap<>(storage);
            p.mapWrites = new HashMap<>();
            for (Map.Entry<String, List<MapWrite>> e : mapWrites.entrySet()) {
                p.mapWrites.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            p.hyps = new ArrayList<>(hyps);
            p.hasResult = hasResult; p.result = result;
            p.dead = dead; p.unknown = unknown;
            return p;
        }
    }

    private List<Path> explore(FunctionDecl fn, Set<String> storageFields) {
        Path init = new Path();
        for (VarDecl param : fn.getParameters()) {
            init.locals.put(param.getName(), LinearForm.symbol("p$" + param.getName()));
        }
        // Preconditions constrain the input domain (assume-style).
        for (Expression pre : fn.getRequires()) {
            addCondition(init, pre, true, storageFields);
        }
        List<Path> out = new ArrayList<>();
        run(asList(fn.getBody()), 0, init, out, storageFields);
        return out;
    }

    private List<Statement> asList(Statement s) {
        if (s == null) return List.of();
        if (s instanceof Block b) return b.getStatements();
        return List.of(s);
    }

    private void run(List<Statement> stmts, int i, Path p, List<Path> out, Set<String> sf) {
        if (out.size() > MAX_PATHS) throw new TooManyPaths();
        while (i < stmts.size()) {
            if (p.dead || p.unknown) { out.add(p); return; }
            Statement s = stmts.get(i);

            if (s instanceof Block b) {
                List<Statement> seq = new ArrayList<>(b.getStatements());
                seq.addAll(stmts.subList(i + 1, stmts.size()));
                run(seq, 0, p, out, sf);
                return;
            }
            if (s instanceof IfStmt iff) {
                List<Statement> rest = stmts.subList(i + 1, stmts.size());
                Path thenP = p.copy();
                Path elseP = p.copy();
                addCondition(thenP, iff.getCondition(), true, sf);
                addCondition(elseP, iff.getCondition(), false, sf);
                List<Statement> thenSeq = new ArrayList<>(asList(iff.getThenBranch()));
                thenSeq.addAll(rest);
                List<Statement> elseSeq = new ArrayList<>(asList(iff.getElseBranch()));
                elseSeq.addAll(rest);
                run(thenSeq, 0, thenP, out, sf);
                run(elseSeq, 0, elseP, out, sf);
                return;
            }
            if (s instanceof ReturnStmt r) {
                if (r.getValue() != null) {
                    try {
                        p.result = evalExpr(r.getValue(), p, sf);
                        p.hasResult = true;
                    } catch (Unmodelable u) {
                        // The value is unmodelable; result stays unbound (any
                        // result-referencing obligation becomes UNKNOWN).
                        p.hasResult = false;
                    }
                }
                out.add(p);
                return;
            }
            if (s instanceof ExpressionStmt es) {
                if (!processExpressionStmt(es.getExpression(), p, sf)) {
                    out.add(p);
                    return;
                }
                i++;
                continue;
            }
            if (s instanceof VarDecl v) {
                try {
                    LinearForm val = v.getInitializer() != null
                            ? evalExpr(v.getInitializer(), p, sf) : LinearForm.ZERO;
                    p.locals.put(v.getName(), val);
                } catch (Unmodelable u) {
                    p.unknown = true; out.add(p); return;
                }
                i++;
                continue;
            }
            if (s instanceof PrintStmt) { i++; continue; }

            // while / break / continue / anything else: cannot model precisely.
            p.unknown = true;
            out.add(p);
            return;
        }
        out.add(p);
    }

    /**
     * Process a statement-level expression for its side effects. Returns
     * {@code false} if the path should terminate here (revert, or an unmodelable
     * construct), {@code true} to continue.
     */
    private boolean processExpressionStmt(Expression e, Path p, Set<String> sf) {
        if (e instanceof CallExpr ce && ce.getCallee() instanceof VariableExpr ve) {
            String name = ve.getName().getLexeme();
            if ("require".equals(name) || "assert".equals(name)) {
                if (!ce.getArguments().isEmpty()) {
                    addCondition(p, ce.getArguments().get(0), true, sf);
                }
                return true; // the live path assumes the guard held
            }
            if ("revert".equals(name)) {
                p.dead = true;
                return false;
            }
        }
        try {
            evalExpr(e, p, sf); // assignments / increments mutate the path
            return true;
        } catch (Unmodelable u) {
            p.unknown = true;
            return false;
        }
    }

    // ── Hypothesis accumulation (path conditions / preconditions) ─────────

    /**
     * Convert a boolean condition into linear hypotheses ({@code >= 0} rows) under
     * the given polarity and append them to {@code p.hyps}. Anything that cannot
     * be expressed as a conjunction of linear inequalities is soundly dropped
     * (dropping a hypothesis only weakens {@code H}, never producing a false
     * proof).
     */
    private void addCondition(Path p, Expression cond, boolean polarity, Set<String> sf) {
        if (cond == null) return;
        if (cond instanceof UnaryExpr ue && ue.getOperator().getType() == TokenType.NOT) {
            addCondition(p, ue.getRight(), !polarity, sf);
            return;
        }
        if (cond instanceof BinaryExpr be) {
            TokenType op = be.getOperator().getType();
            if (op == TokenType.AND) {
                if (polarity) { addCondition(p, be.getLeft(), true, sf); addCondition(p, be.getRight(), true, sf); }
                return; // !(l && r) is a disjunction -> drop
            }
            if (op == TokenType.OR) {
                if (!polarity) { addCondition(p, be.getLeft(), false, sf); addCondition(p, be.getRight(), false, sf); }
                return; // (l || r) -> drop
            }
            TokenType eff = polarity ? op : negateOp(op);
            if (eff == null) return;
            LinearForm a, b;
            try {
                a = evalExpr(be.getLeft(), p, sf);
                b = evalExpr(be.getRight(), p, sf);
            } catch (Unmodelable u) {
                return; // drop unmodelable conjunct (sound)
            }
            switch (eff) {
                case GEQ     -> p.hyps.add(a.subtract(b));                       // a-b >= 0
                case GREATER -> p.hyps.add(a.subtract(b).subtract(ONE));         // a-b-1 >= 0
                case LEQ     -> p.hyps.add(b.subtract(a));                       // b-a >= 0
                case LESS    -> p.hyps.add(b.subtract(a).subtract(ONE));         // b-a-1 >= 0
                case EQUALITY -> { p.hyps.add(a.subtract(b)); p.hyps.add(b.subtract(a)); }
                case NEQ -> { /* disequality is not a linear conjunct -> drop */ }
                default -> { }
            }
        }
        // Non-comparison truthy conditions carry no usable linear information.
    }

    private static TokenType negateOp(TokenType op) {
        return switch (op) {
            case GEQ -> TokenType.LESS;
            case GREATER -> TokenType.LEQ;
            case LEQ -> TokenType.GREATER;
            case LESS -> TokenType.GEQ;
            case EQUALITY -> TokenType.NEQ;
            case NEQ -> TokenType.EQUALITY;
            default -> null;
        };
    }

    // ── Goal discharge ───────────────────────────────────────────────────

    private boolean proveGoal(Path p, Expression goal, boolean polarity, Set<String> sf) {
        if (goal instanceof UnaryExpr ue && ue.getOperator().getType() == TokenType.NOT) {
            return proveGoal(p, ue.getRight(), !polarity, sf);
        }
        if (goal instanceof LiteralExpr lit && lit.getValue() instanceof Boolean bool) {
            return polarity == bool;
        }
        if (goal instanceof BinaryExpr be) {
            TokenType op = be.getOperator().getType();
            if (op == TokenType.AND) {
                return polarity
                        ? proveGoal(p, be.getLeft(), true, sf) && proveGoal(p, be.getRight(), true, sf)
                        : proveGoal(p, be.getLeft(), false, sf) || proveGoal(p, be.getRight(), false, sf);
            }
            if (op == TokenType.OR) {
                return polarity
                        ? proveGoal(p, be.getLeft(), true, sf) || proveGoal(p, be.getRight(), true, sf)
                        : proveGoal(p, be.getLeft(), false, sf) && proveGoal(p, be.getRight(), false, sf);
            }
            TokenType eff = polarity ? op : negateOp(op);
            if (eff == null) return false;
            LinearForm a, b;
            try {
                a = evalExpr(be.getLeft(), p, sf);
                b = evalExpr(be.getRight(), p, sf);
            } catch (Unmodelable u) {
                return false;
            }
            return switch (eff) {
                case GEQ      -> proveKind(p.hyps, a.subtract(b), Kind.GE0);
                case GREATER  -> proveKind(p.hyps, a.subtract(b), Kind.GE1);
                case LEQ      -> proveKind(p.hyps, b.subtract(a), Kind.GE0);
                case LESS     -> proveKind(p.hyps, b.subtract(a), Kind.GE1);
                case EQUALITY -> proveKind(p.hyps, a.subtract(b), Kind.EQ0);
                case NEQ      -> proveKind(p.hyps, a.subtract(b), Kind.NE0);
                default -> false;
            };
        }
        return false;
    }

    private enum Kind { GE0, GE1, EQ0, NE0 }

    /** Discharge a goal on form {@code G}: prove {@code H ⇒ (G op 0)} by FM-UNSAT. */
    private boolean proveKind(List<LinearForm> hyps, LinearForm g, Kind kind) {
        try {
            return switch (kind) {
                // ¬(G >= 0) = (G <= -1) = (-G-1 >= 0)
                case GE0 -> unsat(extend(hyps, g.negate().subtract(ONE)));
                // ¬(G >= 1) = (G <= 0) = (-G >= 0)
                case GE1 -> unsat(extend(hyps, g.negate()));
                // ¬(G == 0) = (G >= 1) ∨ (G <= -1); prove both impossible
                case EQ0 -> unsat(extend(hyps, g.subtract(ONE)))
                        && unsat(extend(hyps, g.negate().subtract(ONE)));
                // ¬(G != 0) = (G == 0) = {G >= 0, -G >= 0}
                case NE0 -> unsat(extend(extend(hyps, g), g.negate()));
            };
        } catch (TooManyPaths | Unmodelable e) {
            return false;
        }
    }

    private static List<LinearForm> extend(List<LinearForm> base, LinearForm extra) {
        List<LinearForm> out = new ArrayList<>(base);
        out.add(extra);
        return out;
    }

    // ── Fourier–Motzkin satisfiability over the rationals ────────────────

    /** A row {@code Σ coeff·atom + constant ≥ 0}. */
    private static final class Row {
        final TreeMap<String, BigInteger> c;
        final BigInteger k;
        Row(TreeMap<String, BigInteger> c, BigInteger k) { this.c = c; this.k = k; }

        static Row of(LinearForm f) {
            TreeMap<String, BigInteger> m = new TreeMap<>(f.getCoefficients());
            return new Row(m, f.getConstant());
        }

        Row scale(BigInteger s) { // s > 0
            TreeMap<String, BigInteger> m = new TreeMap<>();
            for (Map.Entry<String, BigInteger> e : c.entrySet()) m.put(e.getKey(), e.getValue().multiply(s));
            return new Row(m, k.multiply(s));
        }

        static Row combine(Row pos, Row neg, String v) {
            BigInteger a = pos.c.get(v);                 // > 0
            BigInteger b = neg.c.get(v).negate();        // > 0
            Row p2 = pos.scale(b);
            Row n2 = neg.scale(a);
            TreeMap<String, BigInteger> m = new TreeMap<>(p2.c);
            for (Map.Entry<String, BigInteger> e : n2.c.entrySet()) m.merge(e.getKey(), e.getValue(), BigInteger::add);
            m.values().removeIf(x -> x.signum() == 0);
            m.remove(v);
            return new Row(m, p2.k.add(n2.k));
        }

        String canon() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, BigInteger> e : c.entrySet()) sb.append(e.getKey()).append(':').append(e.getValue()).append(',');
            return sb.append('#').append(k).toString();
        }
    }

    /**
     * Return {@code true} if the conjunction of {@code (form >= 0)} for every
     * form, together with {@code atom >= 0} for every atom (the uint256 domain
     * constraints), is unsatisfiable over the rationals.
     */
    private boolean unsat(List<LinearForm> forms) {
        List<Row> rows = new ArrayList<>();
        Set<String> atoms = new TreeSet<>();
        for (LinearForm f : forms) {
            Row r = Row.of(f);
            if (r.c.isEmpty()) {
                if (r.k.signum() < 0) return true; // 0 + k >= 0 with k < 0
                continue;                          // trivially true
            }
            rows.add(r);
            atoms.addAll(r.c.keySet());
        }
        // uint256 domain: every atom is non-negative.
        for (String a : new ArrayList<>(atoms)) {
            TreeMap<String, BigInteger> m = new TreeMap<>();
            m.put(a, BigInteger.ONE);
            rows.add(new Row(m, BigInteger.ZERO));
        }
        for (String v : atoms) {
            rows = eliminate(rows, v);
            if (rows == null) return true;
            if (rows.size() > FM_ROW_CAP) throw new TooManyPaths();
        }
        for (Row r : rows) {
            if (r.c.isEmpty() && r.k.signum() < 0) return true;
        }
        return false;
    }

    /** Project variable {@code v} out of the system. Returns {@code null} on UNSAT. */
    private List<Row> eliminate(List<Row> rows, String v) {
        List<Row> pos = new ArrayList<>();
        List<Row> neg = new ArrayList<>();
        List<Row> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Row r : rows) {
            BigInteger cv = r.c.get(v);
            if (cv == null || cv.signum() == 0) {
                if (seen.add(r.canon())) out.add(r);
            } else if (cv.signum() > 0) {
                pos.add(r);
            } else {
                neg.add(r);
            }
        }
        for (Row pr : pos) {
            for (Row nr : neg) {
                Row combo = Row.combine(pr, nr, v);
                if (combo.c.isEmpty()) {
                    if (combo.k.signum() < 0) return null; // contradiction
                    continue;                              // trivially true
                }
                if (seen.add(combo.canon())) out.add(combo);
                if (out.size() > FM_ROW_CAP) throw new TooManyPaths();
            }
        }
        return out;
    }

    // ── Symbolic expression evaluation ───────────────────────────────────

    private LinearForm evalExpr(Expression expr, Path p, Set<String> sf) {
        if (expr instanceof LiteralExpr lit) {
            Object v = lit.getValue();
            if (v instanceof Long l) return LinearForm.constant(BigInteger.valueOf(l));
            if (v instanceof Integer i) return LinearForm.constant(BigInteger.valueOf(i));
            if (v instanceof BigInteger bi) return LinearForm.constant(bi);
            if (v instanceof Boolean b) return b ? ONE : LinearForm.ZERO;
            throw new Unmodelable("literal");
        }
        if (expr instanceof VariableExpr ve) {
            return readName(ve.getName().getLexeme(), p, sf);
        }
        if (expr instanceof BinaryExpr be) {
            return evalBinary(be, p, sf);
        }
        if (expr instanceof UnaryExpr ue) {
            return switch (ue.getOperator().getType()) {
                case MINUS -> evalExpr(ue.getRight(), p, sf).negate();
                case BIT_NOT -> opaque("~", evalExpr(ue.getRight(), p, sf), null, false);
                default -> throw new Unmodelable("unary " + ue.getOperator().getLexeme());
            };
        }
        if (expr instanceof AssignmentExpr ae) {
            LinearForm value = evalExpr(ae.getValue(), p, sf);
            assignName(ae.getName().getLexeme(), value, p, sf);
            return value;
        }
        if (expr instanceof SetExpr se) {
            String field = fieldOf(se.getObject(), se.getName().getLexeme());
            LinearForm value = evalExpr(se.getValue(), p, sf);
            p.storage.put(field, value);
            return value;
        }
        if (expr instanceof GetExpr ge) {
            return evalGet(ge, p, sf);
        }
        if (expr instanceof IndexExpr ie) {
            String field = mappingNameOf(ie.getObject(), sf);
            LinearForm key = evalExpr(ie.getIndex(), p, sf);
            return readMap(field, key, p);
        }
        if (expr instanceof IndexAssignExpr ia) {
            String field = mappingNameOf(ia.getObject(), sf);
            LinearForm key = evalExpr(ia.getIndex(), p, sf);
            LinearForm value = evalExpr(ia.getValue(), p, sf);
            writeMap(field, key, value, p);
            return value;
        }
        if (expr instanceof PrefixIncrementExpr pre) {
            LinearForm delta = pre.isIncrement() ? ONE : ONE.negate();
            LinearForm updated = evalLValue(pre.getTarget(), p, sf).add(delta);
            storeLValue(pre.getTarget(), updated, p, sf);
            return updated;
        }
        if (expr instanceof PostfixIncrementExpr post) {
            LinearForm old = evalLValue(post.getTarget(), p, sf);
            LinearForm delta = post.isIncrement() ? ONE : ONE.negate();
            storeLValue(post.getTarget(), old.add(delta), p, sf);
            return old;
        }
        throw new Unmodelable("expression " + expr.getClass().getSimpleName());
    }

    private LinearForm evalBinary(BinaryExpr be, Path p, Set<String> sf) {
        TokenType op = be.getOperator().getType();
        if (op == TokenType.AND || op == TokenType.OR) {
            throw new Unmodelable("logical operator in value position");
        }
        LinearForm a = evalExpr(be.getLeft(), p, sf);
        LinearForm b = evalExpr(be.getRight(), p, sf);
        switch (op) {
            case PLUS:  return a.add(b);
            case MINUS: return a.subtract(b);
            case STAR:
                if (a.isConstant()) return b.scale(a.getConstant());
                if (b.isConstant()) return a.scale(b.getConstant());
                return opaque("*", a, b, true);
            case SLASH:
                if (b.isConstant() && b.getConstant().signum() == 0) return LinearForm.ZERO;
                if (a.isConstant() && b.isConstant()) return LinearForm.constant(a.getConstant().divide(b.getConstant()));
                return opaque("/", a, b, false);
            case MOD:
                if (b.isConstant() && b.getConstant().signum() == 0) return LinearForm.ZERO;
                if (a.isConstant() && b.isConstant()) return LinearForm.constant(a.getConstant().mod(b.getConstant()));
                return opaque("%", a, b, false);
            case LSHIFT:
                if (b.isConstant()) {
                    BigInteger sh = b.getConstant();
                    if (sh.signum() >= 0 && sh.bitLength() < 16) {
                        int s = sh.intValue();
                        if (s < 256) return a.scale(BigInteger.ONE.shiftLeft(s));
                    }
                }
                return opaque("<<", a, b, false);
            case BIT_AND: return constFold(a, b, "&");
            case BIT_OR:  return constFold(a, b, "|");
            case BIT_XOR: return constFold(a, b, "^");
            case RSHIFT:  return opaque(">>", a, b, false);
            default:
                // comparisons in value position evaluate to 0/1 — not linear.
                throw new Unmodelable("operator " + be.getOperator().getLexeme());
        }
    }

    private LinearForm constFold(LinearForm a, LinearForm b, String sym) {
        if (a.isConstant() && b.isConstant()) {
            BigInteger x = a.getConstant(), y = b.getConstant();
            BigInteger r = switch (sym) {
                case "&" -> x.and(y);
                case "|" -> x.or(y);
                case "^" -> x.xor(y);
                default -> null;
            };
            if (r != null) return LinearForm.constant(r);
        }
        return opaque(sym, a, b, true);
    }

    /** Intern an opaque non-negative atom for a sub-term the linear theory cannot decompose. */
    private LinearForm opaque(String op, LinearForm a, LinearForm b, boolean commutative) {
        String left = a.canonical();
        String right = b == null ? "" : b.canonical();
        String key;
        if (commutative && b != null && left.compareTo(right) > 0) {
            key = "@" + op + "(" + right + "," + left + ")";
        } else if (b == null) {
            key = "@" + op + "(" + left + ")";
        } else {
            key = "@" + op + "(" + left + "," + right + ")";
        }
        return LinearForm.symbol(key);
    }

    private LinearForm evalGet(GetExpr ge, Path p, Set<String> sf) {
        if (ge.getObject() instanceof VariableExpr ve) {
            String obj = ve.getName().getLexeme();
            String member = ge.getName().getLexeme();
            if ("msg".equals(obj)) {
                if ("sender".equals(member)) return LinearForm.symbol("@msg.sender");
                if ("value".equals(member)) return LinearForm.symbol("@msg.value");
            }
        }
        if (ge.getObject() instanceof ThisExpr) {
            return readStorage(ge.getName().getLexeme(), p, sf);
        }
        throw new Unmodelable("member access");
    }

    private LinearForm readName(String name, Path p, Set<String> sf) {
        LinearForm local = p.locals.get(name);
        if (local != null) return local;
        if ("result".equals(name)) {
            if (p.hasResult) return p.result;
            throw new Unmodelable("'result' with no scalar return value");
        }
        if (sf.contains(name)) return readStorage(name, p, sf);
        throw new Unmodelable("identifier " + name);
    }

    private LinearForm readStorage(String name, Path p, Set<String> sf) {
        if (!sf.contains(name)) throw new Unmodelable("storage field " + name);
        return p.storage.getOrDefault(name, LinearForm.ZERO); // genesis = 0
    }

    private void assignName(String name, LinearForm value, Path p, Set<String> sf) {
        if (p.locals.containsKey(name)) { p.locals.put(name, value); return; }
        if (sf.contains(name)) { p.storage.put(name, value); return; }
        p.locals.put(name, value); // first assignment to a fresh local
    }

    private String fieldOf(Expression object, String member) {
        if (object instanceof ThisExpr) return member;
        if (object instanceof VariableExpr ve && "this".equals(ve.getName().getLexeme())) return member;
        throw new Unmodelable("field assignment on non-this target");
    }

    private String mappingNameOf(Expression object, Set<String> sf) {
        if (object instanceof VariableExpr ve && sf.contains(ve.getName().getLexeme())) {
            return ve.getName().getLexeme();
        }
        if (object instanceof GetExpr ge && ge.getObject() instanceof ThisExpr) {
            return ge.getName().getLexeme();
        }
        throw new Unmodelable("indexed access on non-storage target");
    }

    /**
     * Conservative mapping read. A slot read after a write to the same syntactic
     * key returns the written value; a more-recent write to a <em>different</em>
     * key may alias, so the read is unmodelable; with no writes the slot is at its
     * genesis value {@code 0}.
     */
    private LinearForm readMap(String field, LinearForm key, Path p) {
        List<MapWrite> ws = p.mapWrites.get(field);
        if (ws == null || ws.isEmpty()) return LinearForm.ZERO;
        String kc = key.canonical();
        for (int i = ws.size() - 1; i >= 0; i--) {
            MapWrite w = ws.get(i);
            if (kc.equals(w.keyCanon)) return w.value;
            throw new Unmodelable("possible mapping aliasing");
        }
        return LinearForm.ZERO;
    }

    private void writeMap(String field, LinearForm key, LinearForm value, Path p) {
        p.mapWrites.computeIfAbsent(field, k -> new ArrayList<>())
                .add(new MapWrite(key.canonical(), value));
    }

    private LinearForm evalLValue(Expression target, Path p, Set<String> sf) {
        if (target instanceof VariableExpr ve) return readName(ve.getName().getLexeme(), p, sf);
        if (target instanceof GetExpr ge && ge.getObject() instanceof ThisExpr) {
            return readStorage(ge.getName().getLexeme(), p, sf);
        }
        if (target instanceof IndexExpr ie) {
            return readMap(mappingNameOf(ie.getObject(), sf), evalExpr(ie.getIndex(), p, sf), p);
        }
        throw new Unmodelable("increment target");
    }

    private void storeLValue(Expression target, LinearForm value, Path p, Set<String> sf) {
        if (target instanceof VariableExpr ve) { assignName(ve.getName().getLexeme(), value, p, sf); return; }
        if (target instanceof GetExpr ge && ge.getObject() instanceof ThisExpr) {
            p.storage.put(ge.getName().getLexeme(), value); return;
        }
        if (target instanceof IndexExpr ie) {
            writeMap(mappingNameOf(ie.getObject(), sf), evalExpr(ie.getIndex(), p, sf), value, p);
            return;
        }
        throw new Unmodelable("increment target");
    }

    // ── Bounded refutation (cross-checked by the L3 engine) ──────────────

    /**
     * Search a small concrete input box for arguments that the {@link SpecFuzzEngine}
     * confirms violate an obligation. Returns a map from obligation label to a
     * formatted counterexample. Only confirmed violations are recorded, so there
     * are no false positives.
     */
    private Map<String, String> refute(ClassDecl cls, FunctionDecl fn) {
        Map<String, String> found = new HashMap<>();
        List<VarDecl> params = fn.getParameters();
        SpecFuzzEngine engine = new SpecFuzzEngine(cls);

        List<BigInteger> domain = new ArrayList<>();
        for (int v = 0; v <= bound; v++) domain.add(BigInteger.valueOf(v));
        domain.add(TWO_128);
        domain.add(MAX_256);

        int n = params.size();
        if (n == 0) {
            recordIfViolation(engine, fn, params, List.of(), found);
            return found;
        }

        int[] idx = new int[n];
        long combos = 0;
        outer:
        while (true) {
            List<Object> args = new ArrayList<>(n);
            for (int k = 0; k < n; k++) args.add(domain.get(idx[k]));
            recordIfViolation(engine, fn, params, args, found);

            // Odometer increment over the domain.
            int pos = n - 1;
            while (pos >= 0) {
                if (++idx[pos] < domain.size()) break;
                idx[pos] = 0;
                pos--;
            }
            if (pos < 0) break;
            if (++combos > REFUTE_COMBO_CAP) break outer;
        }
        return found;
    }

    private void recordIfViolation(SpecFuzzEngine engine, FunctionDecl fn,
                                   List<VarDecl> params, List<Object> args,
                                   Map<String, String> found) {
        SpecFuzzEngine.Result r = engine.run(fn, args);
        if (r.outcome != SpecFuzzEngine.Outcome.VIOLATION) return;
        String label = extractLabel(r.detail);
        if (label == null || found.containsKey(label)) return;
        found.put(label, formatArgs(params, args) + " -> " + r.detail);
    }

    /** Pull the unparsed predicate out of an engine VIOLATION detail string. */
    private static String extractLabel(String detail) {
        if (detail == null) return null;
        int open = detail.indexOf('(');
        int close = detail.lastIndexOf(')');
        if (open < 0 || close <= open) return null;
        return detail.substring(open + 1, close);
    }

    private static String formatArgs(List<VarDecl> params, List<Object> args) {
        if (params.isEmpty()) return "(no args)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getName()).append('=').append(args.get(i));
        }
        return sb.toString();
    }

    private static String signatureOf(FunctionDecl fn) {
        StringBuilder sb = new StringBuilder(fn.getName()).append('(');
        List<VarDecl> ps = fn.getParameters();
        for (int i = 0; i < ps.size(); i++) {
            if (i > 0) sb.append(", ");
            String type = ps.get(i).getType();
            if (type != null && !type.isBlank()) sb.append(type).append(' ');
            sb.append(ps.get(i).getName());
        }
        return sb.append(')').toString();
    }

    // ── Reporting ────────────────────────────────────────────────────────

    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("DhrLang Spec Proof Report (L2b - experimental static proving)\n");
        sb.append("=============================================================\n\n");
        if (proofs.isEmpty()) {
            sb.append("No @ensures / @invariant specifications to prove.\n");
            return sb.toString();
        }
        String currentContract = null;
        for (FunctionProof fp : proofs) {
            if (!fp.contract.equals(currentContract)) {
                currentContract = fp.contract;
                sb.append("Contract ").append(currentContract).append('\n');
            }
            sb.append("  ").append(fp.signature);
            if (!fp.checked) sb.append("   [not @checked - proving disabled]");
            sb.append('\n');
            for (Obligation o : fp.obligations) {
                String line = "    " + o.kind + "(" + o.spec + ")";
                sb.append(pad(line, 56)).append(' ').append(o.status);
                if (o.detail != null && o.status != Status.PROVED) {
                    sb.append("  ").append(o.detail);
                }
                sb.append('\n');
            }
        }
        sb.append('\n');
        sb.append("Summary: ").append(proved).append(" proved, ")
          .append(refuted).append(" refuted, ").append(unknown).append(" unknown")
          .append(" across ").append(proofs.size()).append(" function(s).\n");
        sb.append("Proofs are discharged under checked-arithmetic semantics (overflow reverts);\n");
        sb.append("annotate functions @checked to enable proving where it is currently disabled.\n");
        return sb.toString();
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s).append(' ');
        while (sb.length() < width) sb.append('.');
        return sb.toString();
    }

    public String formatJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"version\":\"l2b\",\"functions\":[");
        for (int i = 0; i < proofs.size(); i++) {
            FunctionProof fp = proofs.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"contract\":\"").append(esc(fp.contract)).append("\",")
              .append("\"signature\":\"").append(esc(fp.signature)).append("\",")
              .append("\"checked\":").append(fp.checked).append(',')
              .append("\"obligations\":[");
            for (int j = 0; j < fp.obligations.size(); j++) {
                Obligation o = fp.obligations.get(j);
                if (j > 0) sb.append(',');
                sb.append("{\"kind\":\"").append(esc(o.kind)).append("\",")
                  .append("\"spec\":\"").append(esc(o.spec)).append("\",")
                  .append("\"status\":\"").append(o.status).append("\",")
                  .append("\"detail\":").append(o.detail == null ? "null" : "\"" + esc(o.detail) + "\"")
                  .append('}');
            }
            sb.append("]}");
        }
        sb.append("],\"summary\":{\"proved\":").append(proved)
          .append(",\"refuted\":").append(refuted)
          .append(",\"unknown\":").append(unknown).append("}}");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
