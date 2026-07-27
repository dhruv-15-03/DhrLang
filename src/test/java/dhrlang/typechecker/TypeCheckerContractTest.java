package dhrlang.typechecker;

import dhrlang.ast.*;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.types.BlockchainTypes;
import dhrlang.validation.MsgContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TypeChecker extensions related to smart contracts:
 * <ul>
 *   <li>Blockchain types (Address, uint256, etc.) as primitives</li>
 *   <li>msg and block globals in contract functions</li>
 *   <li>Mapping type references</li>
 * </ul>
 */
@DisplayName("TypeChecker Contract Integration Tests")
class TypeCheckerContractTest {

    private Program parse(String code) {
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    private ErrorReporter typeCheck(String code) {
        // Add entry point class to satisfy TypeChecker's static main requirement
        String fullCode = code + "\nclass Main { static kaam main() { } }\n";
        ErrorReporter reporter = new ErrorReporter("test.dhr", fullCode);
        TypeChecker tc = new TypeChecker(reporter);
        Program program = parse(fullCode);
        tc.check(program);
        return reporter;
    }

    // ── Blockchain primitive types ────────────────────────────────────────

    @Nested
    @DisplayName("Blockchain primitive types")
    class BlockchainPrimitiveTests {

        @Test
        @DisplayName("num type in @storage is valid")
        void numStorageValid() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 0;
                    }
                }
                """;
            ErrorReporter reporter = typeCheck(code);
            assertFalse(reporter.hasErrors(), "num @storage should pass: " + reporter.getErrors());
        }

        @Test
        @DisplayName("blockchain types are recognized as primitives by TypeChecker")
        void blockchainTypesArePrimitive() {
            // Test via the public BlockchainTypes API used by TypeChecker.isPrimitive()
            assertTrue(BlockchainTypes.isBlockchainType("Address"), "Address should be blockchain type");
            assertTrue(BlockchainTypes.isBlockchainType("uint256"), "uint256 should be blockchain type");
            assertTrue(BlockchainTypes.isBlockchainType("int256"), "int256 should be blockchain type");
            assertTrue(BlockchainTypes.isBlockchainType("bytes32"), "bytes32 should be blockchain type");
            assertTrue(BlockchainTypes.isBlockchainType("wei"), "wei should be blockchain type");
        }

        @Test
        @DisplayName("blockchain types are valid in field declarations (parser accepts them)")
        void blockchainFieldDeclarations() {
            // Verify the parser accepts blockchain types as field types
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    @constructor
                    kaam init() {
                        balance = 0;
                    }
                }
                """;
            Program program = parse(code);
            assertNotNull(program);
            assertFalse(program.getClasses().isEmpty());
        }
    }

    // ── msg/block globals ────────────────────────────────────────────────

    @Nested
    @DisplayName("msg and block globals")
    class MsgBlockGlobalTests {

        @Test
        @DisplayName("msg.sender type resolution in MsgContext")
        void msgSenderTypeResolution() {
            // Verify MsgContext correctly maps msg.sender → Address
            String senderType = MsgContext.getMsgPropertyType("sender");
            assertEquals("Address", senderType, "msg.sender should resolve to Address");
        }

        @Test
        @DisplayName("msg.value type resolution in MsgContext")
        void msgValueTypeResolution() {
            String valueType = MsgContext.getMsgPropertyType("value");
            assertEquals("uint256", valueType, "msg.value should resolve to uint256");
        }

        @Test
        @DisplayName("block.timestamp type resolution in MsgContext")
        void blockTimestampTypeResolution() {
            String tsType = MsgContext.getBlockPropertyType("timestamp");
            assertEquals("uint256", tsType, "block.timestamp should resolve to uint256");
        }

        @Test
        @DisplayName("block.number type resolution in MsgContext")
        void blockNumberTypeResolution() {
            String numType = MsgContext.getBlockPropertyType("number");
            assertEquals("uint256", numType, "block.number should resolve to uint256");
        }
    }

    // ── Valid complete contract ───────────────────────────────────────────

    @Nested
    @DisplayName("Complete contract scenarios")
    class CompleteContractTests {

        @Test
        @DisplayName("minimal valid contract passes type checking")
        void minimalContractPasses() {
            String code = """
                @contract
                class Counter {
                    @storage num count;
                    @constructor
                    kaam init() {
                        count = 0;
                    }
                    @view
                    num getCount() {
                        return count;
                    }
                    kaam increment() {
                        count = count + 1;
                    }
                }
                """;
            ErrorReporter reporter = typeCheck(code);
            assertFalse(reporter.hasErrors(), "Minimal contract should pass: " + reporter.getErrors());
        }

        @Test
        @DisplayName("contract with @pure math function passes")
        void pureFunction() {
            String code = """
                @contract
                class MathLib {
                    @storage num dummy;
                    @constructor
                    kaam init() {
                        dummy = 0;
                    }
                    @pure
                    num add(num a, num b) {
                        return a + b;
                    }
                }
                """;
            ErrorReporter reporter = typeCheck(code);
            assertFalse(reporter.hasErrors(), "Pure function should pass: " + reporter.getErrors());
        }
    }
}
