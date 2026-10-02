package dhrlang.testing;

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
 * Integration tests for {@link ContractFuzzer} once it is backed by the real
 * {@link SpecFuzzEngine} (provable-safety level L3). These exercise the full
 * generate → execute → check → minimize loop through the public fuzzer API.
 */
@DisplayName("L3 ContractFuzzer integration")
class ContractFuzzerL3Test {

    private static Program parse(String source) {
        ErrorReporter errors = new ErrorReporter();
        List<Token> tokens = new Lexer(source, errors).scanTokens();
        Program program = new Parser(tokens, errors).parse();
        assertFalse(errors.hasErrors(), "unexpected parse errors: " + errors.getErrorCount());
        return program;
    }

    @Test
    @DisplayName("finds a counterexample for a buggy invariant")
    void findsBuggyInvariant() {
        Program program = parse("""
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

        ContractFuzzer fuzzer = new ContractFuzzer(program);
        fuzzer.setSeed(1);
        fuzzer.setRuns(64);
        fuzzer.fuzzAll();

        assertTrue(fuzzer.hasFailures(), "expected the buggy invariant to be falsified");
        assertTrue(fuzzer.totalFailures() > 0);
    }

    @Test
    @DisplayName("reports no failures for a correct contract")
    void correctContractHasNoFailures() {
        Program program = parse("""
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

        ContractFuzzer fuzzer = new ContractFuzzer(program);
        fuzzer.setSeed(1);
        fuzzer.setRuns(64);
        fuzzer.fuzzAll();

        assertFalse(fuzzer.hasFailures(), "a correct invariant must not be flagged");
    }

    @Test
    @DisplayName("is reproducible under a fixed seed")
    void reproducibleUnderSeed() {
        String src = """
            @contract
            class Calc {
                @ensures(result == a)
                kaam plusOne(num a) {
                    return a + 1;
                }
            }
            """;

        ContractFuzzer a = new ContractFuzzer(parse(src));
        a.setSeed(42);
        a.setRuns(32);
        a.fuzzAll();

        ContractFuzzer b = new ContractFuzzer(parse(src));
        b.setSeed(42);
        b.setRuns(32);
        b.fuzzAll();

        assertEquals(a.totalFailures(), b.totalFailures(),
                "same seed must yield the same failure count");
        assertTrue(a.hasFailures(), "plusOne violates @ensures(result == a)");
    }
}
