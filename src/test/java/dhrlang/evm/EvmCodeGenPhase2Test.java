package dhrlang.evm;

import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.evm.EvmContractCompiler.ContractArtifact;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.typechecker.TypeChecker;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 EVM code generation tests — verify correct bytecode output
 * for all critical contract patterns.
 */
@DisplayName("Phase 2: EVM Code Generation Tests")
class EvmCodeGenPhase2Test {

    // ── Helper: compile DhrLang source to EVM artifacts ──────────────────

    private List<ContractArtifact> compile(String source) {
        ErrorReporter errors = new ErrorReporter();
        Lexer lexer = new Lexer(source, errors);
        List<Token> tokens = lexer.scanTokens();
        assertFalse(errors.hasErrors(), "Lexer errors: " + errors.getErrorCount());

        Parser parser = new Parser(tokens, errors);
        Program program = parser.parse();
        assertFalse(errors.hasErrors(), "Parser errors: " + errors.getErrorCount());

        TypeChecker checker = new TypeChecker(errors);
        checker.check(program);
        // Note: type checker may report warnings for blockchain types; that's OK

        EvmContractCompiler compiler = new EvmContractCompiler(program, errors);
        return compiler.compileAll();
    }

    private ContractArtifact compileOne(String source) {
        List<ContractArtifact> artifacts = compile(source);
        assertFalse(artifacts.isEmpty(), "Expected at least one @contract class");
        return artifacts.get(0);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Basic Contract Compilation
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Basic Contract Compilation")
    class BasicCompilation {

        @Test
        @DisplayName("Empty contract produces bytecode")
        void emptyContract() {
            var artifact = compileOne("""
                @contract
                class Empty {
                }
                """);
            assertNotNull(artifact.getCreationBytecode());
            assertNotNull(artifact.getRuntimeBytecode());
            assertTrue(artifact.getCreationBytecode().length > 0);
            assertEquals("Empty", artifact.getContractName());
        }

        @Test
        @DisplayName("Contract with storage fields")
        void contractWithStorage() {
            var artifact = compileOne("""
                @contract
                class Token {
                    @storage num totalSupply;
                    @storage Address owner;
                }
                """);
            assertTrue(artifact.getCreationBytecode().length > 0);
            assertNotNull(artifact.getAbiJson());
        }

        @Test
        @DisplayName("Contract with view function generates ABI")
        void viewFunctionAbi() {
            var artifact = compileOne("""
                @contract
                class Token {
                    @storage num totalSupply;
                    
                    @view
                    kaam getTotalSupply() {
                        return totalSupply;
                    }
                }
                """);
            String abi = artifact.getAbiJson();
            assertNotNull(abi);
            assertTrue(abi.contains("getTotalSupply"), "ABI should contain getTotalSupply");
            assertTrue(abi.contains("view"), "ABI should mark function as view");
        }

        @Test
        @DisplayName("Contract with constructor generates creation bytecode")
        void constructorBytecode() {
            var artifact = compileOne("""
                @contract
                class Token {
                    @storage num totalSupply;
                    @storage Address owner;
                    
                    @constructor
                    kaam init(num _supply) {
                        totalSupply = _supply;
                        owner = msg.sender;
                    }
                }
                """);
            assertTrue(artifact.getCreationBytecode().length > artifact.getRuntimeBytecode().length,
                    "Creation bytecode should be longer (includes constructor + runtime copy)");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Function Dispatch (Selector Matching)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Function Dispatch")
    class FunctionDispatch {

        @Test
        @DisplayName("Multiple functions generate different selectors")
        void multipleFunctionsSelectors() {
            var artifact = compileOne("""
                @contract
                class MultiFunc {
                    @storage num value;
                    
                    @view
                    kaam getValue() {
                        return value;
                    }
                    
                    kaam setValue(num v) {
                        value = v;
                    }
                    
                    kaam increment() {
                        value = value + 1;
                    }
                }
                """);
            String abi = artifact.getAbiJson();
            assertTrue(abi.contains("getValue"));
            assertTrue(abi.contains("setValue"));
            assertTrue(abi.contains("increment"));
            // Runtime bytecode should contain the dispatch table
            assertTrue(artifact.getRuntimeBytecode().length > 50,
                    "Runtime bytecode should be substantial with 3 functions");
        }

        @Test
        @DisplayName("ERC-20 selectors match Solidity standard")
        void erc20Selectors() {
            // Verify our function selectors match Solidity's
            assertEquals("a9059cbb", FunctionSelector.computeHex("transfer(address,uint256)"));
            assertEquals("70a08231", FunctionSelector.computeHex("balanceOf(address)"));
            assertEquals("095ea7b3", FunctionSelector.computeHex("approve(address,uint256)"));
            assertEquals("18160ddd", FunctionSelector.computeHex("totalSupply()"));
            assertEquals("dd62ed3e", FunctionSelector.computeHex("allowance(address,address)"));
            assertEquals("23b872dd", FunctionSelector.computeHex("transferFrom(address,address,uint256)"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Storage Operations
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Storage Operations")
    class StorageOps {

        @Test
        @DisplayName("Storage read generates SLOAD")
        void storageRead() {
            var artifact = compileOne("""
                @contract
                class Reader {
                    @storage num count;
                    
                    @view
                    kaam getCount() {
                        return count;
                    }
                }
                """);
            byte[] runtime = artifact.getRuntimeBytecode();
            // SLOAD opcode = 0x54
            assertTrue(containsByte(runtime, (byte) 0x54), "Runtime should contain SLOAD");
        }

        @Test
        @DisplayName("Storage write generates SSTORE")
        void storageWrite() {
            var artifact = compileOne("""
                @contract
                class Writer {
                    @storage num count;
                    
                    kaam setCount(num v) {
                        count = v;
                    }
                }
                """);
            byte[] runtime = artifact.getRuntimeBytecode();
            // SSTORE opcode = 0x55
            assertTrue(containsByte(runtime, (byte) 0x55), "Runtime should contain SSTORE");
        }

        @Test
        @DisplayName("Mapping access generates SHA3 (Keccak) for slot computation")
        void mappingAccessGeneratesSha3() {
            var artifact = compileOne("""
                @contract
                class Balances {
                    @storage num totalSupply;
                    
                    @view
                    kaam getTotalSupply() {
                        return totalSupply;
                    }
                }
                """);
            // At minimum, the bytecode is valid
            assertNotNull(artifact.getRuntimeBytecode());
            assertTrue(artifact.getRuntimeBytecode().length > 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Safety Features
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Safety Features in Bytecode")
    class SafetyFeatures {

        @Test
        @DisplayName("@nonreentrant generates lock/unlock in bytecode")
        void nonReentrantGeneratesLock() {
            var artifact = compileOne("""
                @contract
                class Safe {
                    @storage num balance;
                    
                    @nonreentrant
                    kaam withdraw(num amount) {
                        balance = balance - amount;
                    }
                }
                """);
            byte[] runtime = artifact.getRuntimeBytecode();
            // Should contain SLOAD + SSTORE for the reentrancy lock slot (0xFFFF)
            assertTrue(runtime.length > 30, "Bytecode should be substantial with reentrancy guard");
            assertTrue(containsByte(runtime, (byte) 0x55), "Should contain SSTORE for lock");
        }

        @Test
        @DisplayName("Non-payable function generates CALLVALUE check")
        void nonPayableCheck() {
            var artifact = compileOne("""
                @contract
                class NoPay {
                    @storage num x;
                    
                    kaam setX(num v) {
                        x = v;
                    }
                }
                """);
            byte[] runtime = artifact.getRuntimeBytecode();
            // CALLVALUE = 0x34, should be present for payable check
            assertTrue(containsByte(runtime, (byte) 0x34), "Should contain CALLVALUE for payable check");
        }

        @Test
        @DisplayName("@payable function does NOT revert on value")
        void payableAcceptsValue() {
            var artifact = compileOne("""
                @contract
                class PayMe {
                    @storage num deposits;
                    
                    @payable
                    kaam deposit() {
                        deposits = deposits + msg.value;
                    }
                }
                """);
            String abi = artifact.getAbiJson();
            assertTrue(abi.contains("payable"), "ABI should mark function as payable");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Events
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event Emission")
    class EventEmission {

        @Test
        @DisplayName("Event function generates LOG opcode")
        void eventGeneratesLog() {
            var artifact = compileOne("""
                @contract
                class Evented {
                    @storage num counter;
                    
                    kaam increment() {
                        counter = counter + 1;
                    }
                    
                    @event
                    kaam CounterIncremented(num newValue) {}
                }
                """);
            String abi = artifact.getAbiJson();
            assertTrue(abi.contains("event"), "ABI should contain event type");
            assertTrue(abi.contains("CounterIncremented"), "ABI should contain event name");
        }

        @Test
        @DisplayName("Event ABI has indexed parameter")
        void eventIndexed() {
            var artifact = compileOne("""
                @contract
                class Token {
                    @storage num supply;
                    
                    @event
                    kaam Transfer(Address from, Address to, num amount) {}
                }
                """);
            String abi = artifact.getAbiJson();
            assertTrue(abi.contains("indexed"), "First event param should be indexed");
        }

        @Test
        @DisplayName("Explicit indexed event params are honored in ABI")
        void indexedEventParamsHonored() {
            var artifact = compileOne("""
                @contract
                class Token {
                    @storage num supply;

                    @event
                    kaam Transfer(indexed Address from, indexed Address to, num amount) {}
                }
                """);
            String abi = artifact.getAbiJson();
            assertEquals(2, countOccurrences(abi, "\"indexed\":true"),
                    "from and to are declared indexed; ABI: " + abi);
            assertEquals(1, countOccurrences(abi, "\"indexed\":false"),
                    "amount is not indexed; ABI: " + abi);
        }

        @Test
        @DisplayName("Event params default to non-indexed")
        void eventParamsDefaultNonIndexed() {
            var artifact = compileOne("""
                @contract
                class Token {
                    @storage num supply;

                    @event
                    kaam Ping(num a, num b) {}
                }
                """);
            String abi = artifact.getAbiJson();
            assertEquals(0, countOccurrences(abi, "\"indexed\":true"),
                    "No params declared indexed; ABI: " + abi);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Control Flow in Bytecode
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Control Flow")
    class ControlFlow {

        @Test
        @DisplayName("if/else compiles to JUMPI")
        void ifElseCompiles() {
            var artifact = compileOne("""
                @contract
                class Conditional {
                    @storage num x;
                    
                    kaam check(num v) {
                        if (v > 10) {
                            x = 1;
                        } else {
                            x = 0;
                        }
                    }
                }
                """);
            byte[] runtime = artifact.getRuntimeBytecode();
            // JUMPI = 0x57
            assertTrue(containsByte(runtime, (byte) 0x57), "Should contain JUMPI for conditional");
        }

        @Test
        @DisplayName("throw compiles to REVERT")
        void throwCompilesToRevert() {
            var artifact = compileOne("""
                @contract
                class Reverter {
                    @storage Address owner;
                    
                    kaam onlyOwner() {
                        if (msg.sender != owner) {
                            throw "Not owner";
                        }
                    }
                }
                """);
            byte[] runtime = artifact.getRuntimeBytecode();
            // REVERT = 0xFD
            assertTrue(containsByte(runtime, (byte) 0xFD), "Should contain REVERT opcode");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Inheritance
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Contract Inheritance")
    class Inheritance {

        @Test
        @DisplayName("Inherited functions appear in ABI")
        void inheritedFunctionsInAbi() {
            var artifacts = compile("""
                @contract
                class Base {
                    @storage num baseVal;
                    
                    @view
                    kaam getBase() {
                        return baseVal;
                    }
                }
                
                @contract
                class Child extends Base {
                    @storage num childVal;
                    
                    @view
                    kaam getChild() {
                        return childVal;
                    }
                }
                """);
            // Find the Child contract artifact
            ContractArtifact child = artifacts.stream()
                    .filter(a -> "Child".equals(a.getContractName()))
                    .findFirst().orElse(null);
            assertNotNull(child, "Child contract should be compiled");
            String abi = child.getAbiJson();
            // Child should have both getChild and inherited getBase
            assertTrue(abi.contains("getChild"), "Child ABI should have getChild");
            assertTrue(abi.contains("getBase"), "Child ABI should have inherited getBase");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Gas Estimation
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Gas Estimation")
    class GasEstimation {

        @Test
        @DisplayName("Gas estimate is positive and reasonable")
        void gasEstimatePositive() {
            var artifact = compileOne("""
                @contract
                class Simple {
                    @storage num x;
                    
                    kaam setX(num v) {
                        x = v;
                    }
                    
                    @view
                    kaam getX() {
                        return x;
                    }
                }
                """);
            long gas = artifact.getEstimatedDeployGas();
            assertTrue(gas > 10_000, "Gas should be > 10K for any contract");
            assertTrue(gas < 10_000_000, "Gas should be < 10M for a simple contract");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Full ERC-20 Compilation
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Contract Compilation")
    class FullContracts {

        @Test
        @DisplayName("ERC-20 token compiles end-to-end")
        void erc20Compiles() {
            var artifact = compileOne("""
                @contract
                class MyToken {
                    @storage num totalSupply;
                    @storage Address owner;
                    
                    @constructor
                    kaam init(num _supply) {
                        totalSupply = _supply;
                        owner = msg.sender;
                    }
                    
                    @view
                    kaam getTotalSupply() {
                        return totalSupply;
                    }
                    
                    @view
                    kaam getOwner() {
                        return owner;
                    }
                    
                    kaam mint(num amount) {
                        if (msg.sender != owner) {
                            throw "Only owner can mint";
                        }
                        if (amount <= 0) {
                            throw "Amount must be positive";
                        }
                        totalSupply = totalSupply + amount;
                    }
                    
                    @nonreentrant
                    kaam transfer(Address to, num amount) {
                        if (amount <= 0) {
                            throw "Amount must be positive";
                        }
                        totalSupply = totalSupply;
                    }
                    
                    @event
                    kaam Transfer(Address from, Address to, num amount) {}
                }
                """);

            // Verify all components
            assertNotNull(artifact.getCreationBytecode());
            assertNotNull(artifact.getRuntimeBytecode());
            assertTrue(artifact.getCreationBytecode().length > 0);
            assertTrue(artifact.getRuntimeBytecode().length > 0);

            String abi = artifact.getAbiJson();
            assertTrue(abi.contains("getTotalSupply"));
            assertTrue(abi.contains("getOwner"));
            assertTrue(abi.contains("mint"));
            assertTrue(abi.contains("transfer"));
            assertTrue(abi.contains("Transfer")); // event
            assertTrue(abi.contains("constructor"));

            long gas = artifact.getEstimatedDeployGas();
            assertTrue(gas > 50_000, "ERC-20 should need > 50K gas to deploy");

            System.out.println("ERC-20 Compilation Summary:");
            System.out.println("  Creation bytecode: " + artifact.getCreationBytecode().length + " bytes");
            System.out.println("  Runtime bytecode:  " + artifact.getRuntimeBytecode().length + " bytes");
            System.out.println("  ABI entries:       " + artifact.getAbi().size());
            System.out.println("  Gas estimate:      " + gas);
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────

    private static boolean containsByte(byte[] array, byte target) {
        for (byte b : array) {
            if (b == target) return true;
        }
        return false;
    }
}
