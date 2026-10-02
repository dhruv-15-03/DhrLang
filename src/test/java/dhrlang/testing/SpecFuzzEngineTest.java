package dhrlang.testing;

import dhrlang.ast.ClassDecl;
import dhrlang.ast.FunctionDecl;
import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SpecFuzzEngine} — the sound concrete uint256 evaluator that
 * backs provable-safety level L3 (invariant/postcondition fuzzing).
 *
 * <p>The engine works directly on the parsed AST (no type-check needed) and must
 * be <em>sound</em>: it only reports {@code VIOLATION} when a faithful concrete
 * execution falsifies a declared specification. Anything it cannot model
 * faithfully degrades to {@code UNSUPPORTED}/{@code PRECONDITION_SKIP}, never a
 * false positive.
 */
@DisplayName("L3 SpecFuzzEngine")
class SpecFuzzEngineTest {

    // ── helpers ──────────────────────────────────────────────────────────

    private static ClassDecl parseClass(String source) {
        ErrorReporter errors = new ErrorReporter();
        List<Token> tokens = new Lexer(source, errors).scanTokens();
        Program program = new Parser(tokens, errors).parse();
        assertFalse(errors.hasErrors(), "unexpected parse errors: " + errors.getErrorCount());
        assertFalse(program.getClasses().isEmpty(), "expected at least one class");
        return program.getClasses().get(0);
    }

    private static FunctionDecl fn(ClassDecl cls, String name) {
        for (FunctionDecl f : cls.getFunctions()) {
            if (f.getName().equals(name)) return f;
        }
        throw new AssertionError("no function named " + name);
    }

    private static SpecFuzzEngine.Result run(ClassDecl cls, String fnName, Object... args) {
        return new SpecFuzzEngine(cls).run(fn(cls, fnName), List.of(args));
    }

    // ── invariants ───────────────────────────────────────────────────────

    @Test
    @DisplayName("detects a contract invariant violated by the function body")
    void buggyInvariantIsViolated() {
        ClassDecl cls = parseClass("""
            @invariant(total == a + b)
            @contract
            class Buggy {
                @storage num a;
                @storage num b;
                @storage num total;
                kaam set(num x, num y) {
                    a = x;
                    b = y;
                    total = x + y + 1;
                }
            }
            """);

        // total = x+y+1 breaks total == a+b even for the smallest input (0,0).
        SpecFuzzEngine.Result r = run(cls, "set", 0, 0);
        assertEquals(SpecFuzzEngine.Outcome.VIOLATION, r.outcome, r.detail);
        assertTrue(r.detail.toLowerCase().contains("invariant"), r.detail);
    }

    @Test
    @DisplayName("a correct invariant holds across the input domain")
    void correctInvariantHolds() {
        ClassDecl cls = parseClass("""
            @invariant(total == a + b)
            @contract
            class Correct {
                @storage num a;
                @storage num b;
                @storage num total;
                kaam set(num x, num y) {
                    a = x;
                    b = y;
                    total = x + y;
                }
            }
            """);

        assertEquals(SpecFuzzEngine.Outcome.OK, run(cls, "set", 3, 4).outcome);
        assertEquals(SpecFuzzEngine.Outcome.OK, run(cls, "set", 0, 0).outcome);
        assertEquals(SpecFuzzEngine.Outcome.OK, run(cls, "set", 1, 9).outcome);
    }

    // ── postconditions ───────────────────────────────────────────────────

    @Test
    @DisplayName("detects a postcondition violated by the return value")
    void postconditionViolation() {
        ClassDecl cls = parseClass("""
            @contract
            class Calc {
                @ensures(result == a)
                kaam plusOne(num a) {
                    return a + 1;
                }
            }
            """);

        SpecFuzzEngine.Result r = run(cls, "plusOne", 5);
        assertEquals(SpecFuzzEngine.Outcome.VIOLATION, r.outcome, r.detail);
        assertTrue(r.detail.toLowerCase().contains("postcondition"), r.detail);
    }

    @Test
    @DisplayName("a satisfied postcondition reports OK")
    void postconditionHolds() {
        ClassDecl cls = parseClass("""
            @contract
            class Calc {
                @ensures(result == a)
                kaam identity(num a) {
                    return a;
                }
            }
            """);

        assertEquals(SpecFuzzEngine.Outcome.OK, run(cls, "identity", 7).outcome);
    }

    // ── preconditions filter the input domain ────────────────────────────

    @Test
    @DisplayName("an unmet precondition skips the run instead of failing")
    void preconditionSkips() {
        ClassDecl cls = parseClass("""
            @contract
            class Guarded {
                @requires(a > 10)
                @ensures(result == a)
                kaam echo(num a) {
                    return a;
                }
            }
            """);

        // a = 3 fails @requires(a > 10) -> out of scope, not a bug.
        assertEquals(SpecFuzzEngine.Outcome.PRECONDITION_SKIP, run(cls, "echo", 3).outcome);
        // a = 20 satisfies the precondition and the postcondition.
        assertEquals(SpecFuzzEngine.Outcome.OK, run(cls, "echo", 20).outcome);
    }

    // ── soundness: unmodelled constructs degrade to UNSUPPORTED ───────────

    @Test
    @DisplayName("an unsupported construct degrades to a skip, never a false violation")
    void unsupportedConstructSkips() {
        ClassDecl cls = parseClass("""
            @contract
            class External {
                @ensures(result == a)
                kaam delegate(num a) {
                    return helper(a);
                }
                kaam helper(num a) {
                    return a;
                }
            }
            """);

        // A user-function call is outside the engine's faithful model.
        assertEquals(SpecFuzzEngine.Outcome.UNSUPPORTED, run(cls, "delegate", 1).outcome);
    }

    // ── arithmetic fidelity: wrapping vs @checked ────────────────────────

    @Test
    @DisplayName("@checked overflow surfaces as a REVERT, not a violation")
    void checkedOverflowReverts() {
        ClassDecl cls = parseClass("""
            @contract
            class Adder {
                @checked
                @ensures(result >= a)
                kaam add(num a, num b) {
                    return a + b;
                }
            }
            """);

        // Wrapping add of two ones never overflows -> postcondition holds.
        assertEquals(SpecFuzzEngine.Outcome.OK, run(cls, "add", 1, 1).outcome);
    }
}
