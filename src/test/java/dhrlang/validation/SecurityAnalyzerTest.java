package dhrlang.validation;

import dhrlang.ast.ClassDecl;
import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.validation.SecurityAnalyzer.Finding;
import dhrlang.validation.SecurityAnalyzer.Finding.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L1 detector catalog: reentrancy (SWC-107, state-write-after-external-call) and
 * tx.origin authorization (SWC-115). Each detector is exercised with a positive
 * case that must fire and a negative case that must stay quiet, so the analyzer
 * neither misses the vulnerability nor floods safe code with false positives.
 */
@DisplayName("L1: SecurityAnalyzer detectors")
class SecurityAnalyzerTest {

    private List<Finding> analyze(String source) {
        ErrorReporter errors = new ErrorReporter();
        Lexer lexer = new Lexer(source, errors);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errors);
        Program program = parser.parse();
        ClassDecl contract = program.getClasses().get(0);
        return new SecurityAnalyzer().analyze(contract);
    }

    private boolean hasCategory(List<Finding> findings, Category category) {
        return findings.stream().anyMatch(f -> f.getCategory() == category);
    }

    // ── Reentrancy ────────────────────────────────────────────────────────

    @Test
    @DisplayName("flags a storage write after this.transfer(...)")
    void reentrancyViaValueTransfer() {
        List<Finding> findings = analyze("""
                @contract
                class Vault {
                    @storage num totalStaked;

                    kaam withdraw(num amount) {
                        this.transfer(msg.sender, amount);
                        totalStaked = totalStaked - amount;
                    }
                }
                """);
        assertTrue(hasCategory(findings, Category.REENTRANCY),
                "a storage write after an external value transfer is a reentrancy risk");
    }

    @Test
    @DisplayName("flags a storage write after an external contract call")
    void reentrancyViaExternalContractCall() {
        List<Finding> findings = analyze("""
                @contract
                class Puller {
                    @storage Address token;
                    @storage num balance;

                    kaam pull(num amount) {
                        token.transfer(msg.sender, amount);
                        balance = balance - amount;
                    }
                }
                """);
        assertTrue(hasCategory(findings, Category.REENTRANCY),
                "a storage write after an external contract call is a reentrancy risk");
    }

    @Test
    @DisplayName("@nonreentrant suppresses the reentrancy finding")
    void nonReentrantSuppressesFinding() {
        List<Finding> findings = analyze("""
                @contract
                class Vault {
                    @storage num totalStaked;

                    @nonreentrant
                    kaam withdraw(num amount) {
                        this.transfer(msg.sender, amount);
                        totalStaked = totalStaked - amount;
                    }
                }
                """);
        assertFalse(hasCategory(findings, Category.REENTRANCY),
                "@nonreentrant is an explicit guard and must suppress the finding");
    }

    @Test
    @DisplayName("checks-effects-interactions order is not flagged")
    void ceiOrderingNotFlagged() {
        List<Finding> findings = analyze("""
                @contract
                class Vault {
                    @storage num totalStaked;

                    kaam withdraw(num amount) {
                        totalStaked = totalStaked - amount;
                        this.transfer(msg.sender, amount);
                    }
                }
                """);
        assertFalse(hasCategory(findings, Category.REENTRANCY),
                "updating state before the external call is the safe pattern");
    }

    // ── tx.origin authorization ─────────────────────────────────────────────

    @Test
    @DisplayName("flags tx.origin used in an authorization check")
    void txOriginInComparisonFlagged() {
        List<Finding> findings = analyze("""
                @contract
                class Guarded {
                    @storage Address owner;
                    @storage kya paused;

                    kaam pause() {
                        if (tx.origin == owner) {
                            paused = true;
                        }
                    }
                }
                """);
        assertTrue(hasCategory(findings, Category.TX_ORIGIN),
                "tx.origin in an equality check is a phishable authorization");
    }

    @Test
    @DisplayName("msg.sender authorization is not flagged as tx.origin")
    void msgSenderNotFlagged() {
        List<Finding> findings = analyze("""
                @contract
                class Guarded {
                    @storage Address owner;
                    @storage kya paused;

                    kaam pause() {
                        if (msg.sender == owner) {
                            paused = true;
                        }
                    }
                }
                """);
        assertFalse(hasCategory(findings, Category.TX_ORIGIN),
                "msg.sender is the correct authorization source and must not be flagged");
    }
}
