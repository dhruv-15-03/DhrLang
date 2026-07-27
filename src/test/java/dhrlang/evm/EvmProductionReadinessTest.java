package dhrlang.evm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production-readiness tests for the EVM subsystem.
 * Covers StorageEncoder, EvmPeepholeOptimizer, EvmCodeBuffer tracking.
 */
@DisplayName("EVM Production Readiness Tests")
class EvmProductionReadinessTest {

    // ═══════════════════════════════════════════════════════════════════
    //  StorageEncoder Tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("StorageEncoder")
    class StorageEncoderTests {

        @Test
        @DisplayName("mappingSlot produces 32-byte result")
        void mappingSlotLength() {
            byte[] key = new byte[32];
            key[31] = 0x01;
            byte[] slot = StorageEncoder.mappingSlot(key, 0);
            assertEquals(32, slot.length);
        }

        @Test
        @DisplayName("mappingSlot is deterministic")
        void mappingSlotDeterministic() {
            byte[] key = StorageEncoder.uint256ToBytes(BigInteger.valueOf(42));
            assertArrayEquals(
                    StorageEncoder.mappingSlot(key, 5),
                    StorageEncoder.mappingSlot(key, 5));
        }

        @Test
        @DisplayName("mappingSlot differs for different keys")
        void mappingSlotDiffKeys() {
            byte[] k1 = StorageEncoder.uint256ToBytes(BigInteger.ONE);
            byte[] k2 = StorageEncoder.uint256ToBytes(BigInteger.TWO);
            assertFalse(Arrays.equals(
                    StorageEncoder.mappingSlot(k1, 0),
                    StorageEncoder.mappingSlot(k2, 0)));
        }

        @Test
        @DisplayName("mappingSlot differs for different base slots")
        void mappingSlotDiffBase() {
            byte[] key = StorageEncoder.uint256ToBytes(BigInteger.ONE);
            assertFalse(Arrays.equals(
                    StorageEncoder.mappingSlot(key, 0),
                    StorageEncoder.mappingSlot(key, 1)));
        }

        @Test
        @DisplayName("mappingSlotForAddress works")
        void mappingSlotForAddress() {
            byte[] slot = StorageEncoder.mappingSlotForAddress(
                    "0x1234567890abcdef1234567890abcdef12345678", 0);
            assertEquals(32, slot.length);
        }

        @Test
        @DisplayName("mappingSlotForUint works")
        void mappingSlotForUint() {
            byte[] slot = StorageEncoder.mappingSlotForUint(BigInteger.valueOf(100), 3);
            assertEquals(32, slot.length);
        }

        @Test
        @DisplayName("dynamicArraySlot index 0 is valid")
        void dynamicArraySlotIndex0() {
            byte[] slot = StorageEncoder.dynamicArraySlot(7, 0);
            assertEquals(32, slot.length);
        }

        @Test
        @DisplayName("dynamicArraySlot consecutive indices differ by 1")
        void dynamicArraySlotConsecutive() {
            BigInteger s0 = new BigInteger(1, StorageEncoder.dynamicArraySlot(7, 0));
            BigInteger s1 = new BigInteger(1, StorageEncoder.dynamicArraySlot(7, 1));
            assertEquals(BigInteger.ONE, s1.subtract(s0));
        }

        @Test
        @DisplayName("uint256ToBytes produces 32 bytes")
        void uint256ToBytes() {
            byte[] r = StorageEncoder.uint256ToBytes(BigInteger.valueOf(256));
            assertEquals(32, r.length);
        }

        @Test
        @DisplayName("addressToBytes32 left-pads to 32")
        void addressToBytes32() {
            byte[] r = StorageEncoder.addressToBytes32("0xABCDef0000000000000000000000000000000001");
            assertEquals(32, r.length);
            for (int i = 0; i < 12; i++) assertEquals(0, r[i]);
        }

        @Test
        @DisplayName("abiEncode concatenates chunks")
        void abiEncode() {
            byte[] a = StorageEncoder.uint256ToBytes(BigInteger.ONE);
            byte[] b = StorageEncoder.uint256ToBytes(BigInteger.TEN);
            assertEquals(64, StorageEncoder.abiEncode(a, b).length);
        }

        @Test
        @DisplayName("sstoreGasCost returns correct values")
        void sstoreGasCost() {
            assertEquals(20000, StorageEncoder.sstoreGasCost(true));
            assertEquals(5000, StorageEncoder.sstoreGasCost(false));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  EvmPeepholeOptimizer Tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EvmPeepholeOptimizer")
    class PeepholeOptimizerTests {

        @Test
        @DisplayName("PUSH1 0x00 → PUSH0")
        void push0Opt() {
            byte[] out = EvmPeepholeOptimizer.optimize(new byte[]{0x60, 0x00});
            assertArrayEquals(new byte[]{0x5F}, out);
        }

        @Test
        @DisplayName("PUSH1+POP eliminated")
        void pushPopElim() {
            byte[] out = EvmPeepholeOptimizer.optimize(new byte[]{0x60, 0x42, 0x50});
            assertEquals(0, out.length);
        }

        @Test
        @DisplayName("PUSH2+POP eliminated")
        void push2PopElim() {
            byte[] out = EvmPeepholeOptimizer.optimize(new byte[]{0x61, 0x00, 0x01, 0x50});
            assertEquals(0, out.length);
        }

        @Test
        @DisplayName("Const fold: 3+4=7")
        void constFoldAdd() {
            byte[] out = EvmPeepholeOptimizer.optimize(
                    new byte[]{0x60, 0x03, 0x60, 0x04, 0x01});
            assertEquals(2, out.length);
            assertEquals(0x07, out[1] & 0xFF);
        }

        @Test
        @DisplayName("Const fold: 5*6=30")
        void constFoldMul() {
            byte[] out = EvmPeepholeOptimizer.optimize(
                    new byte[]{0x60, 0x05, 0x60, 0x06, 0x02});
            assertEquals(2, out.length);
            assertEquals(30, out[1] & 0xFF);
        }

        @Test
        @DisplayName("DUP1+POP eliminated")
        void dup1PopElim() {
            byte[] out = EvmPeepholeOptimizer.optimize(new byte[]{(byte) 0x80, 0x50});
            assertEquals(0, out.length);
        }

        @Test
        @DisplayName("Non-optimizable passes through")
        void passThrough() {
            byte[] in = {0x01, 0x03};
            assertArrayEquals(in, EvmPeepholeOptimizer.optimize(in));
        }

        @Test
        @DisplayName("Empty bytecode stays empty")
        void emptyBytecode() {
            assertEquals(0, EvmPeepholeOptimizer.optimize(new byte[0]).length);
        }

        @Test
        @DisplayName("analyze() produces valid report")
        void analyzeReport() {
            byte[] orig = {0x60, 0x00, 0x60, 0x42, 0x50};
            byte[] opt = EvmPeepholeOptimizer.optimize(orig);
            var report = EvmPeepholeOptimizer.analyze(orig, opt);
            assertEquals(5, report.originalSize);
            assertTrue(report.optimizedSize < report.originalSize);
            assertTrue(report.savedBytes > 0);
        }

        @Test
        @DisplayName("SLOAD cache cleared at block boundary")
        void sloadCacheClearedAtJump() {
            byte[] in = {
                0x60, 0x01, 0x54,  // PUSH1 1, SLOAD
                0x60, 0x00, 0x56,  // PUSH1 0, JUMP
                0x5B,              // JUMPDEST
                0x60, 0x01, 0x54   // PUSH1 1, SLOAD
            };
            byte[] out = EvmPeepholeOptimizer.optimize(in);
            int sloadCount = 0;
            for (byte b : out) if ((b & 0xFF) == 0x54) sloadCount++;
            assertEquals(2, sloadCount, "Both SLOADs should remain");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  EvmCodeBuffer Stack & Gas Tracking
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EvmCodeBuffer Tracking")
    class CodeBufferTrackingTests {

        @Test
        @DisplayName("Stack depth tracks pushes")
        void stackPush() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushInt(42);
            assertEquals(1, buf.getStackDepth());
            buf.pushInt(10);
            assertEquals(2, buf.getStackDepth());
        }

        @Test
        @DisplayName("Stack depth tracks ADD (pop 2, push 1)")
        void stackAdd() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushInt(1);
            buf.pushInt(2);
            buf.emit(EvmOpcode.ADD);
            assertEquals(1, buf.getStackDepth());
        }

        @Test
        @DisplayName("Max stack depth is recorded")
        void maxStack() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushInt(1);
            buf.pushInt(2);
            buf.pushInt(3);
            buf.emit(EvmOpcode.ADD);
            assertEquals(3, buf.getMaxStackDepth());
            assertEquals(2, buf.getStackDepth());
        }

        @Test
        @DisplayName("isStackSafe() for normal code")
        void stackSafe() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            for (int i = 0; i < 10; i++) buf.pushInt(i);
            assertTrue(buf.isStackSafe());
        }

        @Test
        @DisplayName("Gas accumulates across opcodes")
        void gasAccumulates() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushInt(1);
            buf.pushInt(2);
            buf.emit(EvmOpcode.ADD);
            assertTrue(buf.getGasUsed() > 0);
        }

        @Test
        @DisplayName("SLOAD costs >= 100 gas")
        void sloadGas() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            long before = buf.getGasUsed();
            buf.sloadSlot(0);
            assertTrue(buf.getGasUsed() - before >= 100);
        }

        @Test
        @DisplayName("BigInteger slot overloads work")
        void bigIntSlot() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushInt(42);
            buf.sstoreSlot(BigInteger.valueOf(0xFFFF));
            buf.sloadSlot(BigInteger.valueOf(0xFFFF));
            assertTrue(buf.resolve().length > 0);
        }

        @Test
        @DisplayName("Memory high-water mark tracks mstoreAt")
        void memoryHighWater() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushInt(0xFF);
            buf.mstoreAt(0x80);
            assertEquals(0x80 + 32, buf.getMemoryHighWater());
        }

        @Test
        @DisplayName("Memory gas estimation is non-zero for used memory")
        void memoryGasEstimate() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushInt(1);
            buf.mstoreAt(0x100);
            assertTrue(buf.estimateMemoryGas() > 0);
        }

        @Test
        @DisplayName("Memory high-water increases with higher offsets")
        void memoryHighWaterIncreases() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.pushInt(1);
            buf.mstoreAt(0x00);
            int first = buf.getMemoryHighWater();
            buf.pushInt(2);
            buf.mstoreAt(0x200);
            assertTrue(buf.getMemoryHighWater() > first);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Access Control & Owner Slot Tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Access Control")
    class AccessControlTests {

        @Test
        @DisplayName("Owner slot is a valid 256-bit keccak hash")
        void ownerSlotComputed() {
            // Verify the owner slot is computed deterministically
            byte[] hash = FunctionSelector.keccak256(
                    "dhrlang.owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            BigInteger ownerSlot = new BigInteger(1, hash);
            assertTrue(ownerSlot.bitLength() > 128, "Owner slot should be a large hash");
        }

        @Test
        @DisplayName("Reentrancy lock slot is a valid 256-bit keccak hash")
        void reentrancySlotComputed() {
            byte[] hash = FunctionSelector.keccak256(
                    "dhrlang.reentrancy.lock".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            BigInteger slot = new BigInteger(1, hash);
            assertTrue(slot.bitLength() > 128, "Reentrancy slot should be a large hash");
        }

        @Test
        @DisplayName("Owner and reentrancy slots are different")
        void slotsAreDifferent() {
            byte[] ownerHash = FunctionSelector.keccak256(
                    "dhrlang.owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] reentHash = FunctionSelector.keccak256(
                    "dhrlang.reentrancy.lock".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertFalse(java.util.Arrays.equals(ownerHash, reentHash));
        }
    }
}
