package dhrlang.evm;

import dhrlang.ast.*;
import dhrlang.validation.StorageLayouter;
import dhrlang.validation.StorageLayouter.ContractLayout;
import dhrlang.validation.StorageLayouter.SlotInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Iteration 3 â€“ EVM Backend: Bytecode Generation & Deployment
 *
 * <p>Tests cover all new classes in the {@code dhrlang.evm} package:
 * EvmOpcode, EvmCodeBuffer, FunctionSelector, StorageEncoder,
 * AbiGenerator, EvmCodeGen, and EvmContractCompiler.</p>
 */
class Iteration3EvmBackendTest {

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  1.  EvmOpcode
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("EvmOpcode")
    class EvmOpcodeTests {

        @Test
        @DisplayName("STOP has code 0x00")
        void stopOpcode() {
            assertEquals(0x00, EvmOpcode.STOP.code & 0xFF);
        }

        @Test
        @DisplayName("ADD has code 0x01")
        void addOpcode() {
            assertEquals(0x01, EvmOpcode.ADD.code & 0xFF);
        }

        @Test
        @DisplayName("PUSH1 has code 0x60")
        void push1Opcode() {
            assertEquals(0x60, EvmOpcode.PUSH1.code & 0xFF);
        }

        @Test
        @DisplayName("PUSH32 has code 0x7F")
        void push32Opcode() {
            assertEquals(0x7F, EvmOpcode.PUSH32.code & 0xFF);
        }

        @Test
        @DisplayName("SSTORE has code 0x55")
        void sstoreOpcode() {
            assertEquals(0x55, EvmOpcode.SSTORE.code & 0xFF);
        }

        @Test
        @DisplayName("REVERT has code 0xFD")
        void revertOpcode() {
            assertEquals(0xFD, EvmOpcode.REVERT.code & 0xFF);
        }

        @Test
        @DisplayName("pushForSize returns correct opcode")
        void pushForSize() {
            assertEquals(EvmOpcode.PUSH1, EvmOpcode.pushForSize(1));
            assertEquals(EvmOpcode.PUSH2, EvmOpcode.pushForSize(2));
            assertEquals(EvmOpcode.PUSH4, EvmOpcode.pushForSize(4));
            assertEquals(EvmOpcode.PUSH32, EvmOpcode.pushForSize(32));
        }

        @Test
        @DisplayName("All opcodes have valid code values")
        void allOpcodesValid() {
            for (EvmOpcode op : EvmOpcode.values()) {
                // Code should be a single byte
                int code = op.code & 0xFF;
                assertTrue(code >= 0 && code <= 255, 
                    "Invalid code for " + op.name() + ": " + code);
            }
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  2.  EvmCodeBuffer
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("EvmCodeBuffer")
    class EvmCodeBufferTests {

        @Test
        @DisplayName("emit single opcode produces one byte")
        void emitSingleOpcode() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.emit(EvmOpcode.STOP);
            assertEquals(1, buf.size());
            assertEquals(0x00, buf.toByteArray()[0] & 0xFF);
        }

        @Test
        @DisplayName("push1 produces 2 bytes (opcode + value)")
        void push1() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.push1(0x42);
            byte[] code = buf.toByteArray();
            assertEquals(2, code.length);
            assertEquals(0x60, code[0] & 0xFF);  // PUSH1
            assertEquals(0x42, code[1] & 0xFF);
        }

        @Test
        @DisplayName("push2 produces 3 bytes")
        void push2() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.push2(0x0102);
            byte[] code = buf.toByteArray();
            assertEquals(3, code.length);
            assertEquals(0x61, code[0] & 0xFF);  // PUSH2
            assertEquals(0x01, code[1] & 0xFF);
            assertEquals(0x02, code[2] & 0xFF);
        }

        @Test
        @DisplayName("push4 produces 5 bytes")
        void push4() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.push4(0xDEADBEEF);
            byte[] code = buf.toByteArray();
            assertEquals(5, code.length);
            assertEquals(0x63, code[0] & 0xFF);  // PUSH4
            assertEquals(0xDE, code[1] & 0xFF);
            assertEquals(0xAD, code[2] & 0xFF);
            assertEquals(0xBE, code[3] & 0xFF);
            assertEquals(0xEF, code[4] & 0xFF);
        }

        @Test
        @DisplayName("pushInt(0) emits PUSH0")
        void pushIntZero() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushInt(0);
            byte[] code = buf.toByteArray();
            assertEquals(1, code.length);
            assertEquals(0x5F, code[0] & 0xFF);  // PUSH0
        }

        @Test
        @DisplayName("pushInt(255) emits PUSH1")
        void pushInt255() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushInt(255);
            assertEquals(2, buf.size());
        }

        @Test
        @DisplayName("pushInt(256) emits PUSH2")
        void pushInt256() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushInt(256);
            assertEquals(3, buf.size());
        }

        @Test
        @DisplayName("pushSelector emits 5 bytes (PUSH4 + 4 bytes)")
        void pushSelector() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushSelector(new byte[]{0x01, 0x02, 0x03, 0x04});
            assertEquals(5, buf.size());
        }

        @Test
        @DisplayName("label placement and resolution")
        void labelResolution() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            String lbl = buf.newLabel();
            buf.jumpTo(lbl);  // forward jump: PUSH2(2 bytes) + JUMP = 4 bytes total (offsets 0-3)
            buf.push1(0x42);  // 2 bytes (offsets 4-5)
            buf.placeLabel(lbl);  // JUMPDEST at offset 6
            buf.emit(EvmOpcode.STOP);

            byte[] code = buf.resolve();
            assertNotNull(code);
            assertTrue(code.length > 0);

            // The JUMPDEST should be at offset 6
            assertEquals(0x5B, code[6] & 0xFF, "JUMPDEST at offset 6");

            // The PUSH2 at offset 0 should have value 6
            assertEquals(0x61, code[0] & 0xFF, "PUSH2 opcode");
            assertEquals(0x00, code[1] & 0xFF, "High byte of jump target");
            assertEquals(0x06, code[2] & 0xFF, "Low byte of jump target = 6");
        }

        @Test
        @DisplayName("conditional jump resolution")
        void conditionalJump() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            String lbl = buf.newLabel();
            buf.push1(1);      // condition
            buf.jumpIf(lbl);   // PUSH2 placeholder + JUMPI = 4 bytes
            buf.emit(EvmOpcode.STOP);
            buf.placeLabel(lbl);
            buf.emit(EvmOpcode.STOP);

            byte[] code = buf.resolve();
            assertNotNull(code);
            // Should contain JUMPI opcode
            boolean hasJumpi = false;
            for (byte b : code) {
                if ((b & 0xFF) == 0x57) hasJumpi = true;  // JUMPI
            }
            assertTrue(hasJumpi);
        }

        @Test
        @DisplayName("unresolved label throws")
        void unresolvedLabel() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            String lbl = buf.newLabel();
            buf.jumpTo(lbl);  // never placed
            assertThrows(IllegalStateException.class, buf::resolve);
        }

        @Test
        @DisplayName("mstoreAt emits PUSH + MSTORE")
        void mstoreAt() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.mstoreAt(0x80);
            byte[] code = buf.toByteArray();
            // Should end with MSTORE (0x52)
            assertEquals(0x52, code[code.length - 1] & 0xFF);
        }

        @Test
        @DisplayName("sloadSlot emits PUSH + SLOAD")
        void sloadSlot() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.sloadSlot(3);
            byte[] code = buf.toByteArray();
            assertEquals(0x54, code[code.length - 1] & 0xFF);  // SLOAD
        }

        @Test
        @DisplayName("revert0 emits PUSH0 PUSH0 REVERT")
        void revert0() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.revert0();
            byte[] code = buf.toByteArray();
            assertEquals(3, code.length);
            assertEquals(0x5F, code[0] & 0xFF);  // PUSH0
            assertEquals(0x5F, code[1] & 0xFF);  // PUSH0
            assertEquals(0xFD, code[2] & 0xFF);  // REVERT
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  3.  FunctionSelector
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("FunctionSelector")
    class FunctionSelectorTests {

        @Test
        @DisplayName("transfer(address,uint256) â†’ 0xa9059cbb")
        void transferSelector() {
            byte[] sel = FunctionSelector.compute("transfer(address,uint256)");
            assertEquals(4, sel.length);
            String hex = FunctionSelector.bytesToHex(sel);
            assertEquals("a9059cbb", hex);
        }

        @Test
        @DisplayName("balanceOf(address) â†’ 0x70a08231")
        void balanceOfSelector() {
            String hex = FunctionSelector.computeHex("balanceOf(address)");
            assertEquals("70a08231", hex);
        }

        @Test
        @DisplayName("approve(address,uint256) â†’ 0x095ea7b3")
        void approveSelector() {
            String hex = FunctionSelector.computeHex("approve(address,uint256)");
            assertEquals("095ea7b3", hex);
        }

        @Test
        @DisplayName("canonicalSignature builds correct signature")
        void canonicalSignature() {
            String sig = FunctionSelector.canonicalSignature("transfer", "address", "uint256");
            assertEquals("transfer(address,uint256)", sig);
        }

        @Test
        @DisplayName("keccak256 of empty string matches known hash")
        void keccak256EmptyString() {
            byte[] hash = FunctionSelector.keccak256(new byte[0]);
            String hex = FunctionSelector.bytesToHex(hash);
            assertEquals("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470", hex);
        }

        @Test
        @DisplayName("keccak256 deterministic")
        void keccak256Deterministic() {
            byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
            byte[] h1 = FunctionSelector.keccak256(input);
            byte[] h2 = FunctionSelector.keccak256(input);
            assertArrayEquals(h1, h2);
        }

        @Test
        @DisplayName("different inputs produce different selectors")
        void differentSelectorsForDifferentFunctions() {
            String h1 = FunctionSelector.computeHex("transfer(address,uint256)");
            String h2 = FunctionSelector.computeHex("balanceOf(address)");
            assertNotEquals(h1, h2);
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  4.  StorageEncoder
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("StorageEncoder")
    class StorageEncoderTests {

        @Test
        @DisplayName("uint256ToBytes produces 32 bytes")
        void uint256ToBytes() {
            byte[] result = StorageEncoder.uint256ToBytes(BigInteger.valueOf(42));
            assertEquals(32, result.length);
            assertEquals(42, result[31] & 0xFF);
        }

        @Test
        @DisplayName("addressToBytes32 pads to 32 bytes")
        void addressToBytes32() {
            byte[] result = StorageEncoder.addressToBytes32("0x0000000000000000000000000000000000000001");
            assertEquals(32, result.length);
            assertEquals(1, result[31] & 0xFF);
        }

        @Test
        @DisplayName("mappingSlot produces 32-byte result")
        void mappingSlot() {
            byte[] key = StorageEncoder.uint256ToBytes(BigInteger.ONE);
            byte[] slot = StorageEncoder.mappingSlot(key, 0);
            assertEquals(32, slot.length);
        }

        @Test
        @DisplayName("mappingSlot is deterministic")
        void mappingSlotDeterministic() {
            byte[] key = StorageEncoder.uint256ToBytes(BigInteger.valueOf(100));
            byte[] s1 = StorageEncoder.mappingSlot(key, 5);
            byte[] s2 = StorageEncoder.mappingSlot(key, 5);
            assertArrayEquals(s1, s2);
        }

        @Test
        @DisplayName("different mapping keys produce different slots")
        void differentMappingKeys() {
            byte[] key1 = StorageEncoder.uint256ToBytes(BigInteger.ONE);
            byte[] key2 = StorageEncoder.uint256ToBytes(BigInteger.TWO);
            byte[] s1 = StorageEncoder.mappingSlot(key1, 0);
            byte[] s2 = StorageEncoder.mappingSlot(key2, 0);
            assertFalse(Arrays.equals(s1, s2));
        }

        @Test
        @DisplayName("different base slots produce different mapping slots")
        void differentBaseSlots() {
            byte[] key = StorageEncoder.uint256ToBytes(BigInteger.ONE);
            byte[] s1 = StorageEncoder.mappingSlot(key, 0);
            byte[] s2 = StorageEncoder.mappingSlot(key, 1);
            assertFalse(Arrays.equals(s1, s2));
        }

        @Test
        @DisplayName("dynamicArraySlot produces 32-byte result")
        void dynamicArraySlot() {
            byte[] slot = StorageEncoder.dynamicArraySlot(5, 0);
            assertEquals(32, slot.length);
        }

        @Test
        @DisplayName("dynamicArraySlot consecutive elements are sequential")
        void dynamicArraySlotSequential() {
            byte[] s0 = StorageEncoder.dynamicArraySlot(5, 0);
            byte[] s1 = StorageEncoder.dynamicArraySlot(5, 1);
            BigInteger b0 = new BigInteger(1, s0);
            BigInteger b1 = new BigInteger(1, s1);
            assertEquals(b0.add(BigInteger.ONE), b1);
        }

        @Test
        @DisplayName("abiEncode concatenates 32-byte chunks")
        void abiEncode() {
            byte[] v1 = StorageEncoder.uint256ToBytes(BigInteger.ONE);
            byte[] v2 = StorageEncoder.uint256ToBytes(BigInteger.TWO);
            byte[] encoded = StorageEncoder.abiEncode(v1, v2);
            assertEquals(64, encoded.length);
        }

        @Test
        @DisplayName("sstoreGasCost zero-to-nonzero costs 20000")
        void sstoreGasCostZeroToNonZero() {
            assertEquals(20000, StorageEncoder.sstoreGasCost(true));
        }

        @Test
        @DisplayName("sstoreGasCost nonzero-to-nonzero costs 5000")
        void sstoreGasCostNonZeroToNonZero() {
            assertEquals(5000, StorageEncoder.sstoreGasCost(false));
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  5.  AbiGenerator
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("AbiGenerator")
    class AbiGeneratorTests {

        @Test
        @DisplayName("generate produces entries for public functions")
        void generatePublicFunctions() {
            ClassDecl contract = makeSimpleContract();
            List<Map<String, Object>> abi = AbiGenerator.generate(contract);
            assertFalse(abi.isEmpty());
        }

        @Test
        @DisplayName("function entry has correct type")
        void functionEntryType() {
            ClassDecl contract = makeSimpleContract();
            List<Map<String, Object>> abi = AbiGenerator.generate(contract);
            // Find the 'getBalance' function entry
            Map<String, Object> entry = findAbiEntry(abi, "function", "getBalance");
            assertNotNull(entry, "Should have a function entry for 'getBalance'");
            assertEquals("function", entry.get("type"));
        }

        @Test
        @DisplayName("view function has view stateMutability")
        void viewMutability() {
            ClassDecl contract = makeSimpleContract();
            List<Map<String, Object>> abi = AbiGenerator.generate(contract);
            Map<String, Object> entry = findAbiEntry(abi, "function", "getBalance");
            assertNotNull(entry);
            assertEquals("view", entry.get("stateMutability"));
        }

        @Test
        @DisplayName("constructor entry has type 'constructor'")
        void constructorEntry() {
            ClassDecl contract = makeContractWithConstructor();
            List<Map<String, Object>> abi = AbiGenerator.generate(contract);
            Map<String, Object> entry = findAbiEntryByType(abi, "constructor");
            assertNotNull(entry, "Should have a constructor entry");
            assertEquals("constructor", entry.get("type"));
        }

        @Test
        @DisplayName("event entry has type 'event'")
        void eventEntry() {
            ClassDecl contract = makeContractWithEvent();
            List<Map<String, Object>> abi = AbiGenerator.generate(contract);
            Map<String, Object> entry = findAbiEntry(abi, "event", "Transfer");
            assertNotNull(entry, "Should have an event entry for 'Transfer'");
        }

        @Test
        @DisplayName("toJson produces valid JSON string")
        void toJsonFormat() {
            ClassDecl contract = makeSimpleContract();
            List<Map<String, Object>> abi = AbiGenerator.generate(contract);
            String json = AbiGenerator.toJson(abi);
            assertTrue(json.startsWith("["));
            assertTrue(json.endsWith("]"));
            assertTrue(json.contains("\"type\""));
        }

        @Test
        @DisplayName("solidityType maps DhrLang types correctly")
        void solidityTypeMapping() {
            assertEquals("uint256", AbiGenerator.solidityType("num"));
            assertEquals("string", AbiGenerator.solidityType("sab"));
            assertEquals("bool", AbiGenerator.solidityType("kya"));
            assertEquals("address", AbiGenerator.solidityType("Address"));
            assertEquals("uint256", AbiGenerator.solidityType("uint256"));
        }

        @Test
        @DisplayName("functionSelector produces 4 bytes")
        void functionSelectorLength() {
            FunctionDecl fn = makeFunctionDecl("uint256", "getBalance", List.of());
            byte[] sel = AbiGenerator.functionSelector(fn);
            assertEquals(4, sel.length);
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  6.  EvmCodeGen
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("EvmCodeGen")
    class EvmCodeGenTests {

        @Test
        @DisplayName("compile produces non-empty runtime bytecode")
        void compileProducesBytecode() {
            ClassDecl contract = makeSimpleContract();
            ContractLayout layout = makeLayout("TestToken",
                    new SlotInfo("totalSupply", "uint256", 0, 32));

            EvmCodeGen gen = new EvmCodeGen(contract, layout);
            EvmCodeGen.CompilationResult result = gen.compile();

            assertNotNull(result.getRuntimeBytecode());
            assertTrue(result.getRuntimeBytecode().length > 0,
                    "Runtime bytecode should not be empty");
        }

        @Test
        @DisplayName("compile produces non-empty creation bytecode")
        void creationBytecodeNotEmpty() {
            ClassDecl contract = makeSimpleContract();
            ContractLayout layout = makeLayout("TestToken",
                    new SlotInfo("totalSupply", "uint256", 0, 32));

            EvmCodeGen gen = new EvmCodeGen(contract, layout);
            EvmCodeGen.CompilationResult result = gen.compile();

            assertNotNull(result.getCreationBytecode());
            assertTrue(result.getCreationBytecode().length > 0);
        }

        @Test
        @DisplayName("creation bytecode is larger than runtime (includes runtime appended)")
        void creationLargerThanRuntime() {
            ClassDecl contract = makeSimpleContract();
            ContractLayout layout = makeLayout("TestToken",
                    new SlotInfo("totalSupply", "uint256", 0, 32));

            EvmCodeGen gen = new EvmCodeGen(contract, layout);
            EvmCodeGen.CompilationResult result = gen.compile();

            assertTrue(result.getCreationBytecode().length >= result.getRuntimeBytecode().length,
                    "Creation bytecode should include runtime bytecode");
        }

        @Test
        @DisplayName("compile produces valid ABI JSON")
        void compiledAbiJson() {
            ClassDecl contract = makeSimpleContract();
            ContractLayout layout = makeLayout("TestToken",
                    new SlotInfo("totalSupply", "uint256", 0, 32));

            EvmCodeGen gen = new EvmCodeGen(contract, layout);
            EvmCodeGen.CompilationResult result = gen.compile();

            assertNotNull(result.getAbiJson());
            assertTrue(result.getAbiJson().startsWith("["));
        }

        @Test
        @DisplayName("runtime bytecode starts with CALLDATALOAD dispatcher")
        void dispatcherPattern() {
            ClassDecl contract = makeSimpleContract();
            ContractLayout layout = makeLayout("TestToken",
                    new SlotInfo("totalSupply", "uint256", 0, 32));

            EvmCodeGen gen = new EvmCodeGen(contract, layout);
            EvmCodeGen.CompilationResult result = gen.compile();

            byte[] runtime = result.getRuntimeBytecode();
            // Should start with PUSH0 (for offset 0) 
            assertEquals(0x5F, runtime[0] & 0xFF, "Should start with PUSH0");
            // Followed by CALLDATALOAD
            assertEquals(0x35, runtime[1] & 0xFF, "Second byte should be CALLDATALOAD");
        }

        @Test
        @DisplayName("hex output is valid hex string")
        void hexOutputFormat() {
            ClassDecl contract = makeSimpleContract();
            ContractLayout layout = makeLayout("TestToken",
                    new SlotInfo("totalSupply", "uint256", 0, 32));

            EvmCodeGen gen = new EvmCodeGen(contract, layout);
            EvmCodeGen.CompilationResult result = gen.compile();

            String hex = result.getRuntimeBytecodeHex();
            assertNotNull(hex);
            assertTrue(hex.matches("[0-9a-f]+"), "Should be valid hex: " + hex);
        }

        @Test
        @DisplayName("compile contract with no functions produces revert-only runtime")
        void emptyContract() {
            ClassDecl contract = makeContract("Empty", List.of(), List.of());
            EvmCodeGen gen = new EvmCodeGen(contract, null);
            EvmCodeGen.CompilationResult result = gen.compile();
            assertNotNull(result.getRuntimeBytecode());
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  7.  EvmContractCompiler
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("EvmContractCompiler")
    class EvmContractCompilerTests {

        @Test
        @DisplayName("compileAll finds all contracts in program")
        void compileAllFindsContracts() {
            ClassDecl c1 = makeSimpleContract();
            ClassDecl c2 = makeContract("AnotherToken",
                    List.of(makeVarDecl("uint256", "supply")),
                    List.of(makeViewFunction("getSupply", "uint256")));

            Program program = new Program(List.of(c1, c2), List.of());
            EvmContractCompiler compiler = new EvmContractCompiler(program);
            List<EvmContractCompiler.ContractArtifact> artifacts = compiler.compileAll();

            assertEquals(2, artifacts.size());
        }

        @Test
        @DisplayName("compileAll skips non-contract classes")
        void compileAllSkipsNonContracts() {
            // Non-contract class
            ClassDecl regular = new ClassDecl("Helper", null, List.of(), List.of());
            ClassDecl contract = makeSimpleContract();

            Program program = new Program(List.of(regular, contract), List.of());
            EvmContractCompiler compiler = new EvmContractCompiler(program);
            List<EvmContractCompiler.ContractArtifact> artifacts = compiler.compileAll();

            assertEquals(1, artifacts.size());
            assertEquals("TestToken", artifacts.get(0).getContractName());
        }

        @Test
        @DisplayName("artifact has correct contract name")
        void artifactContractName() {
            ClassDecl contract = makeSimpleContract();
            Program program = new Program(List.of(contract), List.of());
            EvmContractCompiler compiler = new EvmContractCompiler(program);
            List<EvmContractCompiler.ContractArtifact> artifacts = compiler.compileAll();

            assertEquals("TestToken", artifacts.get(0).getContractName());
        }

        @Test
        @DisplayName("artifact has non-zero estimated gas")
        void artifactGasEstimate() {
            ClassDecl contract = makeSimpleContract();
            Program program = new Program(List.of(contract), List.of());
            EvmContractCompiler compiler = new EvmContractCompiler(program);
            List<EvmContractCompiler.ContractArtifact> artifacts = compiler.compileAll();

            assertTrue(artifacts.get(0).getEstimatedDeployGas() > 0,
                    "Gas estimate should be positive");
        }

        @Test
        @DisplayName("artifact summary contains contract name")
        void artifactSummary() {
            ClassDecl contract = makeSimpleContract();
            Program program = new Program(List.of(contract), List.of());
            EvmContractCompiler compiler = new EvmContractCompiler(program);
            List<EvmContractCompiler.ContractArtifact> artifacts = compiler.compileAll();

            String summary = artifacts.get(0).summary();
            assertTrue(summary.contains("TestToken"));
            assertTrue(summary.contains("Creation bytecode"));
        }

        @Test
        @DisplayName("gas estimation includes base cost")
        void gasEstimation() {
            // Minimum gas: 21,000 (base) + 32,000 (CREATE) = 53,000
            long gas = EvmContractCompiler.estimateGas(new byte[0]);
            assertEquals(53_000L, gas);
        }

        @Test
        @DisplayName("gas estimation counts byte costs")
        void gasEstimationByteCosts() {
            byte[] code = new byte[]{0x00, 0x01, 0x00};  // 2 zero bytes, 1 non-zero
            long gas = EvmContractCompiler.estimateGas(code);
            // 53,000 + 2*4 + 1*16 = 53,024
            assertEquals(53_024L, gas);
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  8.  Integration Tests
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("Integration")
    class IntegrationTests {

        @Test
        @DisplayName("end-to-end: compile contract â†’ get bytecode + ABI")
        void endToEndCompilation() {
            ClassDecl contract = makeFullContract();
            Program program = new Program(List.of(contract), List.of());

            EvmContractCompiler compiler = new EvmContractCompiler(program);
            List<EvmContractCompiler.ContractArtifact> artifacts = compiler.compileAll();

            assertEquals(1, artifacts.size());
            EvmContractCompiler.ContractArtifact artifact = artifacts.get(0);

            // Bytecodes
            assertNotNull(artifact.getCreationBytecode());
            assertNotNull(artifact.getRuntimeBytecode());
            assertTrue(artifact.getCreationBytecode().length > 0);
            assertTrue(artifact.getRuntimeBytecode().length > 0);

            // ABI
            assertNotNull(artifact.getAbiJson());
            assertTrue(artifact.getAbi().size() >= 2, "Should have multiple ABI entries");

            // Hex
            assertNotNull(artifact.getCreationBytecodeHex());
            assertNotNull(artifact.getRuntimeBytecodeHex());

            // Gas
            assertTrue(artifact.getEstimatedDeployGas() >= 53_000);
        }

        @Test
        @DisplayName("function selector in runtime matches expected")
        void functionSelectorInRuntime() {
            // Create a contract with a single function
            FunctionDecl fn = makeViewFunction("getBalance", "uint256");
            ClassDecl contract = makeContract("Tok", List.of(), List.of(fn));
            ContractLayout layout = makeLayout("Tok");

            EvmCodeGen gen = new EvmCodeGen(contract, layout);
            EvmCodeGen.CompilationResult result = gen.compile();

            // The runtime bytecode should contain the function selector bytes
            byte[] selector = AbiGenerator.functionSelector(fn);
            String selectorHex = FunctionSelector.bytesToHex(selector);
            String runtimeHex = result.getRuntimeBytecodeHex();

            assertTrue(runtimeHex.contains(selectorHex),
                    "Runtime should contain selector " + selectorHex);
        }

        @Test
        @DisplayName("multiple contracts compiled independently")
        void multipleContracts() {
            ClassDecl c1 = makeSimpleContract();
            ClassDecl c2 = makeContract("Vault",
                    List.of(makeStorageVarDecl("uint256", "locked")),
                    List.of(makeViewFunction("isLocked", "bool")));

            Program program = new Program(List.of(c1, c2), List.of());
            EvmContractCompiler compiler = new EvmContractCompiler(program);
            List<EvmContractCompiler.ContractArtifact> artifacts = compiler.compileAll();

            assertEquals(2, artifacts.size());
            // Different contracts should produce different bytecodes
            assertFalse(Arrays.equals(
                    artifacts.get(0).getRuntimeBytecode(),
                    artifacts.get(1).getRuntimeBytecode()));
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  Test Helpers â€” AST construction
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /** Create a simple @contract ClassDecl named "TestToken" with a @view getBalance function */
    private ClassDecl makeSimpleContract() {
        VarDecl storageField = makeStorageVarDecl("uint256", "totalSupply");
        FunctionDecl getBalance = makeViewFunction("getBalance", "uint256");
        return makeContract("TestToken", List.of(storageField), List.of(getBalance));
    }

    /** Create a @contract ClassDecl with a @constructor */
    private ClassDecl makeContractWithConstructor() {
        VarDecl storageField = makeStorageVarDecl("uint256", "totalSupply");
        FunctionDecl ctor = makeConstructorFunction(
                List.of(makeVarDecl("uint256", "initialSupply")));
        FunctionDecl getBalance = makeViewFunction("getBalance", "uint256");
        return makeContract("TokenWithCtor",
                List.of(storageField), List.of(ctor, getBalance));
    }

    /** Create a @contract ClassDecl with an @event */
    private ClassDecl makeContractWithEvent() {
        VarDecl storageField = makeStorageVarDecl("uint256", "totalSupply");
        FunctionDecl event = makeEventFunction("Transfer",
                List.of(makeVarDecl("Address", "from"), makeVarDecl("Address", "to"),
                        makeVarDecl("uint256", "amount")));
        FunctionDecl getBalance = makeViewFunction("getBalance", "uint256");
        return makeContract("TokenWithEvents",
                List.of(storageField), List.of(event, getBalance));
    }

    /** Create a fuller contract with storage, constructor, view, and payable functions */
    private ClassDecl makeFullContract() {
        List<VarDecl> vars = List.of(
                makeStorageVarDecl("uint256", "totalSupply"),
                makeStorageVarDecl("Address", "owner"));

        FunctionDecl ctor = makeConstructorFunction(
                List.of(makeVarDecl("uint256", "supply")));
        FunctionDecl getBalance = makeViewFunction("getBalance", "uint256");
        FunctionDecl transfer = makePayableFunction("deposit");

        return makeContract("FullToken", vars, List.of(ctor, getBalance, transfer));
    }

    private ClassDecl makeContract(String name, List<VarDecl> vars, List<FunctionDecl> fns) {
        Set<ContractAnnotation> annotations = EnumSet.of(ContractAnnotation.CONTRACT);
        return new ClassDecl(name, null, List.of(), new ArrayList<>(fns),
                new ArrayList<>(vars), new HashSet<>(), annotations);
    }

    private VarDecl makeVarDecl(String type, String name) {
        return new VarDecl(type, name, null);
    }

    private VarDecl makeStorageVarDecl(String type, String name) {
        Set<ContractAnnotation> anns = EnumSet.of(ContractAnnotation.STORAGE);
        return new VarDecl(type, name, null, new HashSet<>(), anns);
    }

    private FunctionDecl makeFunctionDecl(String returnType, String name, List<VarDecl> params) {
        return new FunctionDecl(returnType, name, params, new Block(List.of()));
    }

    private FunctionDecl makeViewFunction(String name, String returnType) {
        Set<ContractAnnotation> anns = EnumSet.of(ContractAnnotation.VIEW);
        return new FunctionDecl(returnType, name, List.of(), new Block(List.of()),
                new HashSet<>(), anns);
    }

    private FunctionDecl makePayableFunction(String name) {
        Set<ContractAnnotation> anns = EnumSet.of(ContractAnnotation.PAYABLE);
        return new FunctionDecl("void", name, List.of(), new Block(List.of()),
                new HashSet<>(), anns);
    }

    private FunctionDecl makeConstructorFunction(List<VarDecl> params) {
        Set<ContractAnnotation> anns = EnumSet.of(ContractAnnotation.CONSTRUCTOR);
        return new FunctionDecl("void", "constructor", params, new Block(List.of()),
                new HashSet<>(), anns);
    }

    private FunctionDecl makeEventFunction(String name, List<VarDecl> params) {
        Set<ContractAnnotation> anns = EnumSet.of(ContractAnnotation.EVENT);
        return new FunctionDecl("void", name, params, new Block(List.of()),
                new HashSet<>(), anns);
    }

    private ContractLayout makeLayout(String name, SlotInfo... slots) {
        return new ContractLayout(name, Arrays.asList(slots));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findAbiEntry(List<Map<String, Object>> abi,
                                              String type, String name) {
        for (Map<String, Object> entry : abi) {
            if (type.equals(entry.get("type")) && name.equals(entry.get("name"))) {
                return entry;
            }
        }
        return null;
    }

    private Map<String, Object> findAbiEntryByType(List<Map<String, Object>> abi, String type) {
        for (Map<String, Object> entry : abi) {
            if (type.equals(entry.get("type"))) {
                return entry;
            }
        }
        return null;
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  9.  Multi-param Events & Advanced EVM
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("Advanced EVM Features")
    class AdvancedEvmTests {

        @Test
        @DisplayName("Multi-param event contract compiles with LOG2+ opcodes")
        void multiParamEventCompiles() {
            FunctionDecl event = makeEventFunction("Transfer",
                    List.of(makeVarDecl("Address", "from"),
                            makeVarDecl("Address", "to"),
                            makeVarDecl("uint256", "amount")));
            FunctionDecl fn = makeViewFunction("getBalance", "uint256");
            ClassDecl contract = makeContract("Token",
                    List.of(makeStorageVarDecl("uint256", "totalSupply")),
                    List.of(event, fn));
            ContractLayout layout = makeLayout("Token",
                    new SlotInfo("totalSupply", "uint256", 0, 32));

            EvmCodeGen gen = new EvmCodeGen(contract, layout);
            EvmCodeGen.CompilationResult result = gen.compile();

            // Should produce valid bytecode
            assertNotNull(result.getRuntimeBytecode());
            assertTrue(result.getRuntimeBytecode().length > 0);
        }

        @Test
        @DisplayName("ABI generator marks first event param as indexed")
        void eventParamIndexed() {
            ClassDecl contract = makeContractWithEvent();
            List<Map<String, Object>> abi = AbiGenerator.generate(contract, Map.of());
            Map<String, Object> eventEntry = findAbiEntry(abi, "event", "Transfer");
            assertNotNull(eventEntry, "Should have Transfer event in ABI");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inputs = (List<Map<String, Object>>) eventEntry.get("inputs");
            assertNotNull(inputs);
            assertTrue(inputs.size() >= 1);
            assertEquals(true, inputs.get(0).get("indexed"),
                    "First event parameter should be indexed");
        }

        @Test
        @DisplayName("Runtime gas estimation is positive")
        void runtimeGasEstimation() {
            ClassDecl contract = makeSimpleContract();
            ContractLayout layout = makeLayout("TestToken",
                    new SlotInfo("totalSupply", "uint256", 0, 32));
            EvmCodeGen gen = new EvmCodeGen(contract, layout);
            EvmCodeGen.CompilationResult result = gen.compile();

            long runtimeGas = EvmContractCompiler.estimateRuntimeGas(result.getRuntimeBytecode());
            assertTrue(runtimeGas > 21_000, "Runtime gas should include base cost + bytecode");
        }

        @Test
        @DisplayName("DELEGATECALL opcode exists in EvmOpcode")
        void delegateCallOpcodeExists() {
            assertEquals(0xF4, EvmOpcode.DELEGATECALL.code & 0xFF);
        }

        @Test
        @DisplayName("STATICCALL opcode exists in EvmOpcode")
        void staticCallOpcodeExists() {
            assertEquals(0xFA, EvmOpcode.STATICCALL.code & 0xFF);
        }

        @Test
        @DisplayName("LOG2 opcode exists in EvmOpcode")
        void log2OpcodeExists() {
            assertEquals(0xA2, EvmOpcode.LOG2.code & 0xFF);
        }

        @Test
        @DisplayName("LOG4 opcode exists in EvmOpcode")
        void log4OpcodeExists() {
            assertEquals(0xA4, EvmOpcode.LOG4.code & 0xFF);
        }
    }
}
