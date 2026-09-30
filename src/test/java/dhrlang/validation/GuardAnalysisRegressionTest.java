package dhrlang.validation;

import dhrlang.ast.ClassDecl;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.parser.Parser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GuardAnalysisRegressionTest {
    private ClassDecl contract(String body) {
        String source = """
                @contract class Guarded {
                    @storage num balance;
                    @storage num cap;
                    @storage num other;
                    kaam externalEffect() {}
                    kaam update(num amount, num extra, kya choose) {
                        %s
                    }
                }
                """.formatted(body);
        ErrorReporter errors = new ErrorReporter();
        var result = new Parser(new Lexer(source, errors).scanTokens(), errors).parse();
        assertFalse(errors.hasErrors(), errors.toJson());
        return result.getClasses().get(0);
    }

    private void subtraction(String body, boolean guarded) {
        ClassDecl cls = contract(body);
        var risks = new ArithmeticOverflowDetector().analyze(cls);
        assertEquals(1, risks.size(), body);
        assertEquals(guarded, risks.get(0).hasGuard(), body);
        long violations = new InvariantChecker().check(cls).stream()
                .filter(v -> v.getInvariant().getKind() == InvariantChecker.Invariant.Kind.NON_NEGATIVE)
                .count();
        assertEquals(guarded ? 0 : 1, violations, body);
    }

    @TestFactory
    Stream<DynamicTest> rejectingGuardsAndBranchFacts() {
        return Stream.of(
                "if (amount > balance) { throw \"no\"; } balance = balance - amount;",
                "if (balance < amount) { throw \"no\"; } balance = balance - amount;",
                "if (amount >= balance) { throw \"no\"; } balance = balance - amount;",
                "if (amount != balance) { throw \"no\"; } balance = balance - amount;",
                "if (amount > balance) { return; } balance = balance - amount;",
                "require(amount <= balance, \"no\"); balance = balance - amount;",
                "if (amount <= balance) { balance = balance - amount; }",
                "if (amount > balance) {} else { balance = balance - amount; }",
                "if (!(amount <= balance)) { throw \"no\"; } balance = balance - amount;",
                "if (amount > balance || extra > cap) { throw \"no\"; } balance = balance - amount;",
                "{ if (amount > balance) { throw \"no\"; } balance = balance - amount; }",
                "while (balance >= amount) { balance = balance - amount; }",
                "while (choose) { if (amount > balance) { throw \"no\"; } balance = balance - amount; }",
                "if (amount > this.balance) { throw \"no\"; } this.balance = this.balance - amount;",
                "if (extra > other) { throw \"no\"; } balance = other - extra;",
                "if (choose) { require(amount <= balance); } else { require(amount <= balance); } balance = balance - amount;"
        ).map(body -> DynamicTest.dynamicTest(body, () -> subtraction(body, true)));
    }

    @TestFactory
    Stream<DynamicTest> unsafeChecksDoNotSuppressFindings() {
        return Stream.of(
                "balance = balance - amount;",
                "if (amount < balance) { throw \"wrong\"; } balance = balance - amount;",
                "if (amount == balance) { throw \"wrong\"; } balance = balance - amount;",
                "if (amount > balance) {} balance = balance - amount;",
                "if (amount > balance) { num local = 1; } balance = balance - amount;",
                "balance = balance - amount; if (amount > balance) { throw \"late\"; }",
                "if (amount > balance) { throw \"no\"; } amount = extra; balance = balance - amount;",
                "if (amount > balance) { throw \"no\"; } amount++; balance = balance - amount;",
                "if (amount > balance) { throw \"no\"; } this.balance = 0; balance = balance - amount;",
                "if (amount > balance) { throw \"no\"; } externalEffect(); balance = balance - amount;",
                "if (amount > balance && choose) { throw \"maybe\"; } balance = balance - amount;",
                "if (choose) { if (amount > balance) { throw \"no\"; } } balance = balance - amount;",
                "if (amount <= 0) { throw \"no\"; } balance = balance - amount;",
                "if (balance < 1) { throw \"no\"; } balance = balance - amount;",
                "if (amount > balance) { throw \"no\"; } while (choose) { balance = balance - amount; }",
                "try { if (amount > balance) { throw \"caught\"; } } catch (e) {} balance = balance - amount;",
                "if (amount > balance) { throw \"no\"; } balance = other - amount;",
                "if (amount > balance) { throw \"no\"; } balance = balance - (amount + 1);",
                "{ num amount = 0; if (amount > balance) { throw \"no\"; } } balance = balance - amount;",
                "if (amount > balance) { throw \"no\"; } this.balance--; balance = balance - amount;",
                "if (amount > balance) { throw \"no\"; } if (choose) { amount = extra; } balance = balance - amount;"
        ).map(body -> DynamicTest.dynamicTest(body, () -> {
            ClassDecl cls = contract(body);
            var arithmetic = new ArithmeticOverflowDetector().analyze(cls).stream()
                    .filter(r -> r.getKind() == ArithmeticOverflowDetector.ArithmeticRisk.Kind.SUBTRACTION_UNDERFLOW)
                    .toList();
            assertFalse(arithmetic.isEmpty(), body);
            assertFalse(arithmetic.get(arithmetic.size() - 1).hasGuard(), body);
            assertTrue(new InvariantChecker().check(cls).stream()
                    .anyMatch(v -> v.getInvariant().getKind() == InvariantChecker.Invariant.Kind.NON_NEGATIVE), body);
        }));
    }

    @TestFactory
    Stream<DynamicTest> additionNeedsAnUpperBoundNotJustAnyComparison() {
        record Case(String body, boolean guarded) {}
        return Stream.of(
                new Case("if (balance <= 0) { throw \"no\"; } balance = balance + amount;", false),
                new Case("if (amount > cap) { throw \"no\"; } if (balance > cap - amount) { throw \"full\"; } balance = balance + amount;", true),
                new Case("if (balance > cap - amount) { throw \"full\"; } balance = balance + amount;", false),
                new Case("if (amount > cap) { throw \"no\"; } if (balance > cap - amount) { throw \"full\"; } cap = extra; balance = balance + amount;", false),
                new Case("if (balance >= cap) { throw \"full\"; } balance = balance + 1;", true),
                new Case("if (balance > cap) { throw \"full\"; } balance = balance + 1;", false),
                new Case("if (amount > cap) { throw \"no\"; } if (balance + amount > cap) { throw \"late\"; } balance = balance + amount;", false),
                new Case("balance = balance + amount; if (balance >= cap) { throw \"late\"; }", false)
        ).map(test -> DynamicTest.dynamicTest(test.body, () -> {
            var risks = new ArithmeticOverflowDetector().analyze(contract(test.body));
            assertEquals(1, risks.size());
            assertEquals(test.guarded, risks.get(0).hasGuard());
        }));
    }

    @Test
    void eachWriteUsesItsOwnPositionAndInvalidatesThePreviousBound() {
        ClassDecl cls = contract("""
                if (amount > balance) { throw "no"; }
                balance = balance - amount;
                balance = balance - amount;
                """);
        var risks = new ArithmeticOverflowDetector().analyze(cls);
        assertEquals(2, risks.size());
        assertTrue(risks.get(0).hasGuard());
        assertFalse(risks.get(1).hasGuard());
        assertEquals(risks.get(0).getLocation().getLine() + 1, risks.get(1).getLocation().getLine());
        assertEquals(risks.get(1).getLocation(), new InvariantChecker().check(cls).get(0).getLocation());
    }

    @Test
    void aLocalShadowDoesNotCountAsAStorageWrite() {
        var cls = contract("num balance = 1; balance = balance - amount;");
        assertTrue(new ArithmeticOverflowDetector().analyze(cls).isEmpty());
        assertTrue(new InvariantChecker().check(cls).isEmpty());
    }

    @Test
    void divisionNeedsANonzeroDivisorAndPreservesUngardedFindings() {
        for (String op : List.of("/", "%")) {
            var safe = new ArithmeticOverflowDetector().analyze(contract(
                    "if (amount == 0) { throw \"zero\"; } balance = balance " + op + " amount;"));
            assertTrue(safe.get(0).hasGuard());
            var unsafe = new ArithmeticOverflowDetector().analyze(contract(
                    "if (amount > balance) { throw \"no\"; } balance = balance " + op + " amount;"));
            assertFalse(unsafe.get(0).hasGuard());
        }
    }
}
