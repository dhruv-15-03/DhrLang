package dhrlang.proving;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LinearForm} — the {@code constant + Σ coeff·atom}
 * linear-arithmetic IR the L2b prover normalizes expressions into.
 *
 * <p>The invariants that matter for soundness: arithmetic is exact over
 * {@link BigInteger}, atoms with a zero coefficient are pruned (so structurally
 * cancelling terms collapse to a constant), and {@code equals}/{@code canonical}
 * agree for forms that are mathematically identical regardless of construction
 * order.
 */
@DisplayName("L2b LinearForm IR")
class LinearFormTest {

    @Test
    @DisplayName("constant carries its value and is recognised as constant")
    void constantBasics() {
        LinearForm five = LinearForm.constant(5);
        assertTrue(five.isConstant());
        assertFalse(five.isZero());
        assertEquals(BigInteger.valueOf(5), five.getConstant());
        assertTrue(five.getCoefficients().isEmpty());
    }

    @Test
    @DisplayName("a symbol is non-constant with a unit coefficient")
    void symbolBasics() {
        LinearForm x = LinearForm.symbol("x");
        assertFalse(x.isConstant());
        assertEquals(BigInteger.ZERO, x.getConstant());
        assertEquals(BigInteger.ONE, x.getCoefficients().get("x"));
    }

    @Test
    @DisplayName("adding like atoms accumulates coefficients")
    void addLikeAtoms() {
        LinearForm x = LinearForm.symbol("x");
        LinearForm twoX = x.add(x);
        assertEquals(BigInteger.TWO, twoX.getCoefficients().get("x"));
        assertEquals(twoX, x.scale(BigInteger.TWO));
    }

    @Test
    @DisplayName("constants fold under addition")
    void addConstants() {
        assertEquals(LinearForm.constant(5),
                LinearForm.constant(2).add(LinearForm.constant(3)));
    }

    @Test
    @DisplayName("x - x prunes the atom and collapses to zero")
    void subtractCancels() {
        LinearForm x = LinearForm.symbol("x");
        LinearForm zero = x.subtract(x);
        assertTrue(zero.isZero());
        assertTrue(zero.isConstant());
        assertTrue(zero.getCoefficients().isEmpty());
        assertEquals(LinearForm.ZERO, zero);
    }

    @Test
    @DisplayName("negate flips constant and coefficient signs")
    void negate() {
        assertEquals(LinearForm.constant(-5), LinearForm.constant(5).negate());
        LinearForm negX = LinearForm.symbol("x").negate();
        assertEquals(BigInteger.valueOf(-1), negX.getCoefficients().get("x"));
    }

    @Test
    @DisplayName("scaling by zero prunes every atom to ZERO")
    void scaleByZero() {
        LinearForm x = LinearForm.symbol("x");
        assertEquals(LinearForm.ZERO, x.scale(BigInteger.ZERO));
        assertTrue(x.scale(BigInteger.ZERO).isZero());
    }

    @Test
    @DisplayName("equal forms share a canonical key regardless of build order")
    void canonicalAgreesWithEquals() {
        LinearForm a = LinearForm.symbol("x").add(LinearForm.symbol("y")).add(LinearForm.constant(3));
        LinearForm b = LinearForm.constant(3).add(LinearForm.symbol("y")).add(LinearForm.symbol("x"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.canonical(), b.canonical());
    }

    @Test
    @DisplayName("getCoefficients is a read-only view")
    void coefficientsImmutable() {
        LinearForm x = LinearForm.symbol("x");
        assertThrows(UnsupportedOperationException.class,
                () -> x.getCoefficients().put("y", BigInteger.ONE));
    }
}
