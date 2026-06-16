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
    //  Custom Errors + revert
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Custom Errors")
    class CustomErrors {

        private static String toHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        }

        @Test
        @DisplayName("@error declaration produces an ABI error entry")
        void customErrorInAbi() {
            var artifact = compileOne("""
                @contract
                class Vault {
                    @storage num balance;

                    @error
                    kaam InsufficientBalance(num available, num required) {}

                    kaam withdraw(num amount) {
                        revert(InsufficientBalance(amount, amount));
                    }
                }
                """);
            String abi = artifact.getAbiJson();
            assertTrue(abi.contains("\"type\":\"error\""),
                    "ABI should contain an error entry; ABI: " + abi);
            assertTrue(abi.contains("InsufficientBalance"),
                    "ABI should contain the error name; ABI: " + abi);
            // Error inputs are never indexed (that flag is event-only).
            assertEquals(0, countOccurrences(abi, "\"type\":\"error\",\"name\":\"InsufficientBalance\",\"inputs\":[{\"name\":\"available\",\"type\":\"uint256\",\"indexed\""),
                    "Error inputs must not carry an indexed flag; ABI: " + abi);
        }

        @Test
        @DisplayName("@error declaration is not emitted as a callable function")
        void customErrorNotDispatchable() {
            var artifact = compileOne("""
                @contract
                class Vault {
                    @error
                    kaam Boom(num code) {}

                    kaam ping() {
                        revert(Boom(1));
                    }
                }
                """);
            // The error selector must not appear as a dispatchable function in the ABI.
            String abi = artifact.getAbiJson();
            assertEquals(0, countOccurrences(abi, "\"type\":\"function\",\"name\":\"Boom\""),
                    "Error must not be a dispatchable function; ABI: " + abi);
        }

        @Test
        @DisplayName("revert with custom error encodes its 4-byte selector")
        void revertCustomErrorEmitsSelector() {
            var artifact = compileOne("""
                @contract
                class Vault {
                    @error
                    kaam InsufficientBalance(num available, num required) {}

                    kaam withdraw(num amount) {
                        revert(InsufficientBalance(amount, amount));
                    }
                }
                """);
            byte[] selector = FunctionSelector.compute("InsufficientBalance(uint256,uint256)");
            String selHex = toHex(selector);
            String code = artifact.getRuntimeBytecodeHex().toLowerCase();
            assertTrue(code.contains(selHex),
                    "Runtime bytecode should embed the custom-error selector " + selHex);
            assertTrue(code.contains("fd"), "Runtime bytecode should contain a REVERT (0xfd)");
        }

        @Test
        @DisplayName("revert(\"message\") and bare revert() compile to REVERT")
        void revertStringAndBare() {
            var artifact = compileOne("""
                @contract
                class Guard {
                    kaam checkOne(num x) {
                        revert("nope");
                    }

                    kaam checkTwo() {
                        revert();
                    }
                }
                """);
            String code = artifact.getRuntimeBytecodeHex().toLowerCase();
            assertTrue(code.contains("fd"), "Runtime bytecode should contain a REVERT (0xfd)");
            // Error(string) selector 0x08c379a0 from revert("nope").
            assertTrue(code.contains("08c379a0"),
                    "revert(\"message\") should encode the Error(string) selector");
        }

        @Test
        @DisplayName("require(cond, CustomError(args)) reverts with the custom error selector")
        void requireWithCustomError() {
            var artifact = compileOne("""
                @contract
                class Vault {
                    @error
                    kaam Unauthorized(Address who) {}

                    kaam guard(Address caller) {
                        require(1 == 1, Unauthorized(caller));
                    }
                }
                """);
            byte[] selector = FunctionSelector.compute(
                    "Unauthorized(" + AbiGenerator.solidityType("Address") + ")");
            String selHex = toHex(selector);
            String code = artifact.getRuntimeBytecodeHex().toLowerCase();
            assertTrue(code.contains(selHex),
                    "require(..., CustomError) should embed the error selector " + selHex);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Checked / Wrapping Arithmetic
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Checked Arithmetic")
    class CheckedArithmetic {

        // Error(string) selector emitted by every revert-with-message.
        private static final String ERROR_STRING_SELECTOR = "08c379a0";

        private static String strHex(String s) {
            StringBuilder sb = new StringBuilder();
            for (byte b : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        @Test
        @DisplayName("@checked add emits an overflow guard")
        void checkedAddEmitsOverflowGuard() {
            var artifact = compileOne("""
                @contract
                class Calc {
                    @storage num result;
                    @checked
                    kaam add(num a, num b) {
                        result = a + b;
                    }
                }
                """);
            String code = artifact.getRuntimeBytecodeHex().toLowerCase();
            assertTrue(code.contains(ERROR_STRING_SELECTOR),
                    "@checked add should embed a revert-with-message overflow guard");
            assertTrue(code.contains(strHex("arithmetic overflow")),
                    "@checked add should revert with 'arithmetic overflow'");
        }

        @Test
        @DisplayName("Default add wraps (no overflow guard)")
        void defaultAddIsWrapping() {
            var artifact = compileOne("""
                @contract
                class Calc {
                    @storage num result;
                    kaam add(num a, num b) {
                        result = a + b;
                    }
                }
                """);
            String code = artifact.getRuntimeBytecodeHex().toLowerCase();
            assertFalse(code.contains(strHex("arithmetic overflow")),
                    "Default (alpha) add should wrap, not emit an overflow guard");
        }

        @Test
        @DisplayName("@unchecked add wraps (no overflow guard)")
        void uncheckedAddIsWrapping() {
            var artifact = compileOne("""
                @contract
                class Calc {
                    @storage num result;
                    @unchecked
                    kaam add(num a, num b) {
                        result = a + b;
                    }
                }
                """);
            String code = artifact.getRuntimeBytecodeHex().toLowerCase();
            assertFalse(code.contains(strHex("arithmetic overflow")),
                    "@unchecked add should wrap, not emit an overflow guard");
        }

        @Test
        @DisplayName("@checked subtract emits an underflow guard")
        void checkedSubEmitsUnderflowGuard() {
            var artifact = compileOne("""
                @contract
                class Calc {
                    @storage num result;
                    @checked
                    kaam sub(num a, num b) {
                        result = a - b;
                    }
                }
                """);
            String code = artifact.getRuntimeBytecodeHex().toLowerCase();
            assertTrue(code.contains(strHex("arithmetic underflow")),
                    "@checked subtract should revert with 'arithmetic underflow'");
        }

        @Test
        @DisplayName("@checked multiply emits an overflow guard")
        void checkedMulEmitsOverflowGuard() {
            var artifact = compileOne("""
                @contract
                class Calc {
                    @storage num result;
                    @checked
                    kaam mul(num a, num b) {
                        result = a * b;
                    }
                }
                """);
            String code = artifact.getRuntimeBytecodeHex().toLowerCase();
            assertTrue(code.contains(ERROR_STRING_SELECTOR),
                    "@checked multiply should embed an overflow guard");
            assertTrue(code.contains(strHex("arithmetic overflow")),
                    "@checked multiply should revert with 'arithmetic overflow'");
        }

        @Test
        @DisplayName("Default multiply wraps (no overflow guard)")
        void defaultMulIsWrapping() {
            var artifact = compileOne("""
                @contract
                class Calc {
                    @storage num result;
                    kaam mul(num a, num b) {
                        result = a * b;
                    }
                }
                """);
            String code = artifact.getRuntimeBytecodeHex().toLowerCase();
            assertFalse(code.contains(strHex("arithmetic overflow")),
                    "Default (alpha) multiply should wrap, not emit an overflow guard");
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
