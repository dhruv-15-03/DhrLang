package dhrlang.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for numeric {@code as} casts / {@code toNum}/{@code toDuo}.
 *
 * <p>Historically {@code expr as num} and {@code expr as duo} (which desugar to
 * {@code toNum}/{@code toDuo}) only accepted strings, so {@code duo as num} and
 * {@code num as duo} failed at type-check. They now accept num, duo, and sab:
 * <ul>
 *   <li>{@code toNum}: string -&gt; parse; num -&gt; identity; duo -&gt; truncate toward zero</li>
 *   <li>{@code toDuo}: string -&gt; parse; duo -&gt; identity; num -&gt; widen</li>
 * </ul>
 * Truncating a division result via {@code (a / b) as num} is the supported way to
 * obtain integer division.
 */
@DisplayName("Numeric as-cast conversions")
class CastConversionTest {

    private String[] lines(String src) {
        RuntimeTestUtil.Result r = RuntimeTestUtil.runSource(src);
        assertFalse(r.hadCompileErrors, "unexpected compile error: " + r.stderr);
        assertFalse(r.hadRuntimeError, "unexpected runtime error: " + r.stderr);
        return r.stdout.trim().split("\\R");
    }

    @Test
    @DisplayName("duo as num truncates toward zero")
    void duoToNumTruncates() {
        String[] out = lines("""
                class Main {
                    static kaam main() {
                        printLine(7.9 as num);
                        printLine(-7.9 as num);
                        printLine((7.0 / 2.0) as num);
                    }
                }
                """);
        assertArrayEquals(new String[]{"7", "-7", "3"}, out);
    }

    @Test
    @DisplayName("integer division via (num / num) as num")
    void integerDivisionViaCast() {
        String[] out = lines("""
                class Main {
                    static kaam main() {
                        num a = 7;
                        num b = 2;
                        num q = (a / b) as num;
                        printLine(q);
                    }
                }
                """);
        assertArrayEquals(new String[]{"3"}, out);
    }

    @Test
    @DisplayName("num as duo widens")
    void numToDuoWidens() {
        String[] out = lines("""
                class Main {
                    static kaam main() {
                        num x = 5;
                        printLine(x as duo);
                    }
                }
                """);
        assertArrayEquals(new String[]{"5.0"}, out);
    }

    @Test
    @DisplayName("toNum / toDuo accept numeric and string arguments")
    void directConversions() {
        String[] out = lines("""
                class Main {
                    static kaam main() {
                        printLine(toNum(3.9));
                        printLine(toNum(42));
                        printLine(toNum("42"));
                        printLine(toDuo(5));
                        printLine(toDuo(2.5));
                        printLine(toDuo("4.2"));
                    }
                }
                """);
        assertArrayEquals(new String[]{"3", "42", "42", "5.0", "2.5", "4.2"}, out);
    }

    @Test
    @DisplayName("casting a non-numeric type (kya) is still a compile error")
    void rejectsBooleanCast() {
        RuntimeTestUtil.Result r = RuntimeTestUtil.runSource("""
                class Main {
                    static kaam main() {
                        kya flag = true;
                        printLine(flag as num);
                    }
                }
                """);
        assertTrue(r.hadCompileErrors, "boolean as num must be rejected at type-check");
    }
}
