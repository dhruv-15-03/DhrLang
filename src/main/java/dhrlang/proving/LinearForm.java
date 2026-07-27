package dhrlang.proving;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * An immutable linear form over integer-valued <em>atoms</em>:
 * {@code constant + Σ coeff_i · atom_i}.
 *
 * <p>This is the term representation used by {@link SpecProver} (provable-safety
 * level L2b). Each atom is an opaque {@link String} identifier standing for a
 * non-negative 256-bit integer — a function parameter, a transaction-context
 * value ({@code msg.sender}/{@code msg.value}), or an <em>interned opaque atom</em>
 * representing a sub-term the linear theory cannot decompose (a product of two
 * non-constants, a division, a remainder, a bitwise/shift result, or a mapping
 * read). Two structurally identical opaque sub-terms are interned under the same
 * key, so they cancel — that is what lets the prover discharge e.g.
 * {@code result == a * b} by linear reasoning even though {@code a * b} itself is
 * non-linear.
 *
 * <p>The form stays linear under the only operations the decision procedure
 * relies on: addition, subtraction, negation, and multiplication/division by a
 * <em>constant</em>. Genuinely non-linear combinations are folded into a fresh
 * opaque atom by the caller ({@link SpecProver}), not here.
 *
 * <p>Coefficients are stored in a sorted map with zero coefficients pruned, so
 * {@link #equals(Object)}, {@link #hashCode()} and {@link #canonical()} give a
 * canonical identity independent of construction order.
 */
public final class LinearForm {

    /** atom id → non-zero coefficient (sorted, immutable). */
    private final TreeMap<String, BigInteger> coeffs;
    private final BigInteger constant;

    private LinearForm(TreeMap<String, BigInteger> coeffs, BigInteger constant) {
        // Prune zero coefficients so the representation is canonical.
        coeffs.values().removeIf(c -> c.signum() == 0);
        this.coeffs = coeffs;
        this.constant = constant;
    }

    /** The zero form. */
    public static final LinearForm ZERO = constant(BigInteger.ZERO);

    /** A pure constant {@code k}. */
    public static LinearForm constant(BigInteger k) {
        return new LinearForm(new TreeMap<>(), k);
    }

    /** A pure constant {@code k}. */
    public static LinearForm constant(long k) {
        return constant(BigInteger.valueOf(k));
    }

    /** A single atom with coefficient {@code 1}: {@code 0 + 1·atom}. */
    public static LinearForm symbol(String atomId) {
        TreeMap<String, BigInteger> m = new TreeMap<>();
        m.put(atomId, BigInteger.ONE);
        return new LinearForm(m, BigInteger.ZERO);
    }

    /** {@code this + other}. */
    public LinearForm add(LinearForm other) {
        TreeMap<String, BigInteger> m = new TreeMap<>(this.coeffs);
        for (Map.Entry<String, BigInteger> e : other.coeffs.entrySet()) {
            m.merge(e.getKey(), e.getValue(), BigInteger::add);
        }
        return new LinearForm(m, this.constant.add(other.constant));
    }

    /** {@code this - other}. */
    public LinearForm subtract(LinearForm other) {
        return this.add(other.negate());
    }

    /** {@code -this}. */
    public LinearForm negate() {
        return scale(BigInteger.valueOf(-1));
    }

    /** {@code k · this} for a constant {@code k} (keeps the form linear). */
    public LinearForm scale(BigInteger k) {
        if (k.signum() == 0) return ZERO;
        TreeMap<String, BigInteger> m = new TreeMap<>();
        for (Map.Entry<String, BigInteger> e : this.coeffs.entrySet()) {
            m.put(e.getKey(), e.getValue().multiply(k));
        }
        return new LinearForm(m, this.constant.multiply(k));
    }

    /** {@code true} if this form has no atoms (a pure constant). */
    public boolean isConstant() {
        return coeffs.isEmpty();
    }

    /** {@code true} if this form is exactly {@code 0}. */
    public boolean isZero() {
        return coeffs.isEmpty() && constant.signum() == 0;
    }

    /** The constant term (the whole value iff {@link #isConstant()}). */
    public BigInteger getConstant() {
        return constant;
    }

    /** The atom coefficients (unmodifiable, zero coefficients already pruned). */
    public Map<String, BigInteger> getCoefficients() {
        return Collections.unmodifiableMap(coeffs);
    }

    /**
     * A canonical string identity for this form, used both as the interning key
     * for opaque atoms and for {@code equals}-consistent hashing. Independent of
     * construction order because the coefficient map is sorted.
     */
    public String canonical() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, BigInteger> e : coeffs.entrySet()) {
            sb.append(e.getValue()).append('*').append(e.getKey()).append('+');
        }
        sb.append('#').append(constant);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LinearForm other)) return false;
        return constant.equals(other.constant) && coeffs.equals(other.coeffs);
    }

    @Override
    public int hashCode() {
        return 31 * coeffs.hashCode() + constant.hashCode();
    }

    @Override
    public String toString() {
        if (coeffs.isEmpty()) return constant.toString();
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, BigInteger> e : coeffs.entrySet()) {
            BigInteger c = e.getValue();
            if (!first) sb.append(c.signum() < 0 ? " - " : " + ");
            else if (c.signum() < 0) sb.append('-');
            BigInteger abs = c.abs();
            if (!abs.equals(BigInteger.ONE)) sb.append(abs).append('*');
            sb.append(e.getKey());
            first = false;
        }
        if (constant.signum() != 0) {
            sb.append(constant.signum() < 0 ? " - " : " + ").append(constant.abs());
        }
        return sb.toString();
    }
}
