package dhrlang.proving;

import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SpecProver} — provable-safety level L2b (experimental static
 * proving). The prover discharges {@code @ensures} / {@code @invariant}
 * obligations for <em>all</em> inputs via symbolic execution plus a hand-rolled
 * Fourier–Motzkin decision procedure, refuting with a concrete counterexample
 * (cross-checked by the L3 {@link dhrlang.testing.SpecFuzzEngine}) where it can.
 *
 * <p>Soundness is the property under test: an obligation is only {@code PROVED}
 * when the linear-arithmetic proof goes through under checked semantics, only
 * {@code REFUTED} when a concrete execution actually falsifies it, and otherwise
 * {@code UNKNOWN}.
 */
@DisplayName("L2b SpecProver")
class SpecProverTest {

    // ── helpers ──────────────────────────────────────────────────────────

    private static Program parse(String source) {
        ErrorReporter errors = new ErrorReporter();
        List<Token> tokens = new Lexer(source, errors).scanTokens();
        Program program = new Parser(tokens, errors).parse();
        assertFalse(errors.hasErrors(), "unexpected parse errors: " + errors.getErrorCount());
        return program;
    }

    private static SpecProver prove(String source) {
        SpecProver prover = new SpecProver(parse(source)).setBound(8);
        prover.proveAll();
        return prover;
    }

    private static SpecProver.Obligation obligation(SpecProver prover, String fnPrefix, String kind) {
        for (SpecProver.FunctionProof fp : prover.getProofs()) {
            if (!fp.signature.startsWith(fnPrefix)) continue;
            for (SpecProver.Obligation ob : fp.obligations) {
                if (ob.kind.equals(kind)) return ob;
            }
        }
        throw new AssertionError("no " + kind + " obligation on " + fnPrefix
                + " (proofs: " + prover.getProofs().size() + ")");
    }

    // ── PROVED ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("proves universally-true specifications")
    class Proved {

        @Test
        @DisplayName("@checked add satisfies result >= a (b is non-negative)")
        void provesAddLowerBound() {
            SpecProver p = prove("""
                @contract
                class Adder {
                    @checked
                    @ensures(result >= a)
                    num add(num a, num b) {
                        return a + b;
                    }
                }
                """);
            assertEquals(SpecProver.Status.PROVED, obligation(p, "add", "@ensures").status);
            assertTrue(p.getProvedCount() >= 1);
            assertFalse(p.hasRefutations());
        }

        @Test
        @DisplayName("@checked guarded subtraction satisfies result == a - b")
        void provesExactSubtraction() {
            SpecProver p = prove("""
                @contract
                class Sub {
                    @requires(b <= a)
                    @checked
                    @ensures(result == a - b)
                    num sub(num a, num b) {
                        return a - b;
                    }
                }
                """);
            assertEquals(SpecProver.Status.PROVED, obligation(p, "sub", "@ensures").status);
        }

        @Test
        @DisplayName("a contract invariant maintained by a @checked mutator is proved")
        void provesContractInvariant() {
            SpecProver p = prove("""
                @invariant(total == a + b)
                @contract
                class Sum {
                    @storage num a;
                    @storage num b;
                    @storage num total;
                    @checked
                    kaam set(num x, num y) {
                        a = x;
                        b = y;
                        total = x + y;
                    }
                }
                """);
            assertEquals(SpecProver.Status.PROVED, obligation(p, "set", "@invariant").status);
        }
    }

    // ── REFUTED ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refutes false specifications with a concrete counterexample")
    class Refuted {

        @Test
        @DisplayName("result > a is false for add(0, 0)")
        void refutesWrongPostcondition() {
            SpecProver p = prove("""
                @contract
                class Adder {
                    @checked
                    @ensures(result > a)
                    num add(num a, num b) {
                        return a + b;
                    }
                }
                """);
            SpecProver.Obligation ob = obligation(p, "add", "@ensures");
            assertEquals(SpecProver.Status.REFUTED, ob.status);
            assertNotNull(ob.detail);
            assertTrue(ob.detail.contains("a=0") && ob.detail.contains("b=0"),
                    "counterexample should pin a=0, b=0. Got: " + ob.detail);
            assertTrue(p.hasRefutations());
            assertEquals(1, p.getRefutedCount());
        }

        @Test
        @DisplayName("a broken invariant is refuted via the concrete engine")
        void refutesBrokenInvariant() {
            SpecProver p = prove("""
                @invariant(total == a + b)
                @contract
                class Buggy {
                    @storage num a;
                    @storage num b;
                    @storage num total;
                    @checked
                    kaam set(num x, num y) {
                        a = x;
                        b = y;
                        total = x + y + 1;
                    }
                }
                """);
            SpecProver.Obligation ob = obligation(p, "set", "@invariant");
            assertEquals(SpecProver.Status.REFUTED, ob.status);
            assertNotNull(ob.detail);
            assertTrue(ob.detail.contains("invariant violated"),
                    "detail should cite the invariant. Got: " + ob.detail);
        }
    }

    // ── UNKNOWN ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reports UNKNOWN when it can neither prove nor refute")
    class Unknown {

        @Test
        @DisplayName("a true spec under @unchecked is UNKNOWN (proving disabled)")
        void unknownWhenUnchecked() {
            SpecProver p = prove("""
                @contract
                class Wrap {
                    @unchecked
                    @ensures(result == a + b)
                    num addU(num a, num b) {
                        return a + b;
                    }
                }
                """);
            SpecProver.Obligation ob = obligation(p, "addU", "@ensures");
            assertEquals(SpecProver.Status.UNKNOWN, ob.status);
            assertEquals(0, p.getRefutedCount());
            assertFalse(p.hasRefutations());
        }

        @Test
        @DisplayName("a loop body is not modelled, so its postcondition is UNKNOWN")
        void unknownWithLoop() {
            SpecProver p = prove("""
                @contract
                class Looper {
                    @checked
                    @ensures(result == a)
                    num countTo(num a) {
                        num i = 0;
                        while (i < a) {
                            i = i + 1;
                        }
                        return i;
                    }
                }
                """);
            assertEquals(SpecProver.Status.UNKNOWN, obligation(p, "countTo", "@ensures").status);
        }
    }

    // ── reporting ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reporting")
    class Reporting {

        @Test
        @DisplayName("a contract with no specs proves nothing and reports empty")
        void emptyWhenNoSpecs() {
            SpecProver p = prove("""
                @contract
                class Bare {
                    @storage num x;
                    kaam bump() {
                        x = x + 1;
                    }
                }
                """);
            assertTrue(p.isEmpty());
            assertTrue(p.formatReport().contains("No @ensures"));
        }

        @Test
        @DisplayName("JSON output carries the L2b version tag and a summary block")
        void jsonShape() {
            SpecProver p = prove("""
                @contract
                class Sub {
                    @requires(b <= a)
                    @checked
                    @ensures(result == a - b)
                    num sub(num a, num b) {
                        return a - b;
                    }
                }
                """);
            String json = p.formatJson();
            assertTrue(json.contains("\"version\":\"l2b\""), json);
            assertTrue(json.contains("\"summary\""), json);
            assertTrue(json.contains("\"proved\":1"), json);
        }
    }

    // ── CLI end-to-end (fresh JVM on the test classpath) ──────────────────

    @Nested
    @DisplayName("contract prove CLI")
    class Cli {

        private static final String JAVA =
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

        @Test
        @DisplayName("a sound @checked contract proves and exits 0")
        void provedContractExitsZero(@TempDir Path dir) throws Exception {
            Path src = write(dir, "Ok.dhr", """
                @contract
                class Ok {
                    @constructor
                    kaam init() { x = 0; }
                    @storage num x;
                    @requires(b <= a)
                    @checked
                    @ensures(result == a - b)
                    num sub(num a, num b) {
                        return a - b;
                    }
                }
                """);
            Result r = run("contract", "prove", src.toString());
            assertEquals(0, r.exit, r.out);
            assertTrue(r.out.contains("PROVED"), "expected a PROVED line. Got: " + r.out);
        }

        @Test
        @DisplayName("a falsifiable spec is refuted and exits 1")
        void refutedContractExitsOne(@TempDir Path dir) throws Exception {
            Path src = write(dir, "Bug.dhr", """
                @contract
                class Bug {
                    @constructor
                    kaam init() { x = 0; }
                    @storage num x;
                    @checked
                    @ensures(result > a)
                    num add(num a, num b) {
                        return a + b;
                    }
                }
                """);
            Result r = run("contract", "prove", src.toString());
            assertEquals(1, r.exit, "a refuted obligation must gate CI. Got: " + r.out);
            assertTrue(r.out.contains("REFUTED"), "expected a REFUTED line. Got: " + r.out);
        }

        private static Path write(Path dir, String name, String body) throws IOException {
            Path f = dir.resolve(name);
            Files.writeString(f, body, StandardCharsets.UTF_8);
            return f;
        }

        private Result run(String... toolArgs) throws IOException, InterruptedException {
            String cp = System.getProperty("java.class.path");
            String[] full = new String[4 + toolArgs.length];
            full[0] = JAVA;
            full[1] = "-cp";
            full[2] = cp;
            full[3] = "dhrlang.Main";
            System.arraycopy(toolArgs, 0, full, 4, toolArgs.length);

            ProcessBuilder pb = new ProcessBuilder(full);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                in.transferTo(baos);
            }
            int exit = p.waitFor();
            return new Result(exit, baos.toString(StandardCharsets.UTF_8));
        }

        private record Result(int exit, String out) {}
    }
}
