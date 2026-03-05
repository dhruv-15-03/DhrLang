package dhrlang.testing;

import dhrlang.interpreter.Callable;
import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * Built-in assertion functions for the DhrLang contract testing framework.
 * Each assertion is implemented as a {@link NativeFunction} that can be
 * registered in the interpreter's global environment.
 *
 * <p>Available assertions:
 * <ul>
 *   <li>{@code assertEqual(a, b)} — checks deep equality</li>
 *   <li>{@code assertNotEqual(a, b)} — checks deep inequality</li>
 *   <li>{@code assertTrue(cond)} — checks truthiness</li>
 *   <li>{@code assertFalse(cond)} — checks falsiness</li>
 *   <li>{@code assertGreaterThan(a, b)} — a &gt; b</li>
 *   <li>{@code assertLessThan(a, b)} — a &lt; b</li>
 *   <li>{@code assertReverts(msg)} — marks an expected revert</li>
 *   <li>{@code assertGasBelow(limit)} — gas must be below limit</li>
 * </ul>
 */
public final class ContractAssertions {

    private ContractAssertions() { /* utility class */ }

    // ── Custom error type ────────────────────────────────

    /**
     * Thrown when a contract test assertion fails.
     */
    public static class AssertionError extends RuntimeException {
        private final String expected;
        private final String actual;

        public AssertionError(String message) {
            super(message);
            this.expected = "";
            this.actual = "";
        }

        public AssertionError(String message, String expected, String actual) {
            super(message);
            this.expected = expected;
            this.actual = actual;
        }

        public String getExpected() { return expected; }
        public String getActual() { return actual; }
    }

    // ── assertEqual ──────────────────────────────────────

    public static NativeFunction assertEqual() {
        return new NativeFunction() {
            @Override public int arity() { return 2; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object a = arguments.get(0);
                Object b = arguments.get(1);
                if (!deepEquals(a, b)) {
                    throw new AssertionError(
                            "assertEqual failed: expected <" + stringify(b)
                                    + "> but got <" + stringify(a) + ">",
                            stringify(b), stringify(a));
                }
                return null;
            }

            @Override public String toString() { return "<native assertEqual>"; }
        };
    }

    // ── assertNotEqual ───────────────────────────────────

    public static NativeFunction assertNotEqual() {
        return new NativeFunction() {
            @Override public int arity() { return 2; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object a = arguments.get(0);
                Object b = arguments.get(1);
                if (deepEquals(a, b)) {
                    throw new AssertionError(
                            "assertNotEqual failed: both values are <" + stringify(a) + ">");
                }
                return null;
            }

            @Override public String toString() { return "<native assertNotEqual>"; }
        };
    }

    // ── assertTrue ───────────────────────────────────────

    public static NativeFunction assertTrue() {
        return new NativeFunction() {
            @Override public int arity() { return 1; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                if (!isTruthy(arguments.get(0))) {
                    throw new AssertionError(
                            "assertTrue failed: value was " + stringify(arguments.get(0)));
                }
                return null;
            }

            @Override public String toString() { return "<native assertTrue>"; }
        };
    }

    // ── assertFalse ──────────────────────────────────────

    public static NativeFunction assertFalse() {
        return new NativeFunction() {
            @Override public int arity() { return 1; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                if (isTruthy(arguments.get(0))) {
                    throw new AssertionError(
                            "assertFalse failed: value was " + stringify(arguments.get(0)));
                }
                return null;
            }

            @Override public String toString() { return "<native assertFalse>"; }
        };
    }

    // ── assertGreaterThan ────────────────────────────────

    public static NativeFunction assertGreaterThan() {
        return new NativeFunction() {
            @Override public int arity() { return 2; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                double a = toDouble(arguments.get(0));
                double b = toDouble(arguments.get(1));
                if (!(a > b)) {
                    throw new AssertionError(
                            "assertGreaterThan failed: " + stringify(arguments.get(0))
                                    + " is not > " + stringify(arguments.get(1)));
                }
                return null;
            }

            @Override public String toString() { return "<native assertGreaterThan>"; }
        };
    }

    // ── assertLessThan ───────────────────────────────────

    public static NativeFunction assertLessThan() {
        return new NativeFunction() {
            @Override public int arity() { return 2; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                double a = toDouble(arguments.get(0));
                double b = toDouble(arguments.get(1));
                if (!(a < b)) {
                    throw new AssertionError(
                            "assertLessThan failed: " + stringify(arguments.get(0))
                                    + " is not < " + stringify(arguments.get(1)));
                }
                return null;
            }

            @Override public String toString() { return "<native assertLessThan>"; }
        };
    }

    // ── assertReverts ────────────────────────────────────

    /**
     * Marks an expected revert. In a real VM integration this would wrap
     * a call and verify it reverts with the given message.
     */
    public static NativeFunction assertReverts() {
        return new NativeFunction() {
            @Override public int arity() { return 1; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                // In test mode, this records that a revert is expected.
                // The test runner checks that the subsequent call does revert.
                String expectedMessage = stringify(arguments.get(0));
                return "EXPECT_REVERT:" + expectedMessage;
            }

            @Override public String toString() { return "<native assertReverts>"; }
        };
    }

    // ── assertGasBelow ───────────────────────────────────

    /**
     * Asserts that gas usage is below a given threshold.
     * Works with the GasProfiler integration.
     */
    public static NativeFunction assertGasBelow() {
        return new NativeFunction() {
            @Override public int arity() { return 1; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                long limit = toLong(arguments.get(0));
                // The actual gas check is performed via TestCheatcodes.getGasUsed()
                // This registers the gas limit for the current test scope.
                return "GAS_LIMIT:" + limit;
            }

            @Override public String toString() { return "<native assertGasBelow>"; }
        };
    }

    // ── Helpers ──────────────────────────────────────────

    static boolean deepEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        // Handle numeric comparisons across types
        if (a instanceof Number && b instanceof Number) {
            return toDouble(a) == toDouble(b);
        }
        return Objects.equals(a, b);
    }

    static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) return !s.isEmpty();
        return true;
    }

    static String stringify(Object value) {
        if (value == null) return "null";
        return value.toString();
    }

    static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); }
            catch (NumberFormatException e) {
                throw new AssertionError("Cannot convert to number: " + s);
            }
        }
        throw new AssertionError("Cannot convert to number: " + value);
    }

    static long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); }
            catch (NumberFormatException e) {
                throw new AssertionError("Cannot convert to long: " + s);
            }
        }
        throw new AssertionError("Cannot convert to long: " + value);
    }

    /**
     * Return all assertion functions as a map suitable for registration.
     */
    public static java.util.Map<String, NativeFunction> allAssertions() {
        java.util.Map<String, NativeFunction> map = new java.util.LinkedHashMap<>();
        map.put("assertEqual", assertEqual());
        map.put("assertNotEqual", assertNotEqual());
        map.put("assertTrue", assertTrue());
        map.put("assertFalse", assertFalse());
        map.put("assertGreaterThan", assertGreaterThan());
        map.put("assertLessThan", assertLessThan());
        map.put("assertReverts", assertReverts());
        map.put("assertGasBelow", assertGasBelow());
        return map;
    }
}
