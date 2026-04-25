package dhrlang.evm;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.*;

/**
 * Low-level EVM bytecode buffer with helper methods for emitting opcodes,
 * push values, and managing jump label resolution.
 *
 * <p>This class accumulates raw bytes that form a valid EVM bytecode sequence.
 * Labels and forward jumps are resolved in a final {@link #resolve()} pass.</p>
 */
public class EvmCodeBuffer {

    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

    /** Unresolved forward jump patch sites: label name → list of byte offsets to patch. */
    private final Map<String, List<Integer>> pendingJumps = new LinkedHashMap<>();

    /** Resolved label positions: label name → byte offset in output. */
    private final Map<String, Integer> labelPositions = new LinkedHashMap<>();

    private int labelCounter = 0;

    // ── Stack depth tracking ─────────────────────────────────────────────

    /** Current tracked stack depth. */
    private int stackDepth = 0;

    /** Maximum stack depth reached during code generation. */
    private int maxStackDepth = 0;

    /** EVM maximum stack size. */
    private static final int MAX_EVM_STACK = 1024;

    /** Adjust tracked stack depth by delta (positive = push, negative = pop). */
    public void adjustStack(int delta) {
        stackDepth += delta;
        if (stackDepth > maxStackDepth) maxStackDepth = stackDepth;
    }

    /** Get current tracked stack depth. */
    public int getStackDepth() { return stackDepth; }

    /** Get maximum stack depth reached. */
    public int getMaxStackDepth() { return maxStackDepth; }

    /** Check if stack depth is within EVM limits. */
    public boolean isStackSafe() { return maxStackDepth <= MAX_EVM_STACK; }

    // ── Gas tracking ─────────────────────────────────────────────────────

    /** Accumulated gas cost of all emitted opcodes. */
    private long gasUsed = 0;

    /** Get total accumulated gas cost. */
    public long getGasUsed() { return gasUsed; }

    // ── Memory tracking ──────────────────────────────────────────────────

    /** Highest memory offset written to (tracks memory expansion). */
    private int memoryHighWater = 0;

    /** Get the highest memory offset accessed. */
    public int getMemoryHighWater() { return memoryHighWater; }

    /**
     * Estimate the memory expansion gas cost.
     * EVM charges: 3 * words + words^2 / 512
     */
    public long estimateMemoryGas() {
        int words = (memoryHighWater + 31) / 32;
        return 3L * words + (long) words * words / 512;
    }

    // ── Emit helpers ─────────────────────────────────────────────────────

    /** Emit a single opcode (no operands). */
    public void emit(EvmOpcode op) {
        buf.write(op.code);
        adjustStack(op.stackOut - op.stackIn);
        gasUsed += op.gasCost;
    }

    /** Current byte offset (program counter). */
    public int pc() {
        return buf.size();
    }

    /** Emit PUSH1 with a single byte value (0..255). */
    public void push1(int value) {
        emit(EvmOpcode.PUSH1);
        buf.write(value & 0xFF);
    }

    /** Emit PUSH2 with a 2-byte big-endian value. */
    public void push2(int value) {
        emit(EvmOpcode.PUSH2);
        buf.write((value >> 8) & 0xFF);
        buf.write(value & 0xFF);
    }

    /** Emit PUSH4 with a 4-byte big-endian value. */
    public void push4(int value) {
        emit(EvmOpcode.PUSH4);
        buf.write((value >> 24) & 0xFF);
        buf.write((value >> 16) & 0xFF);
        buf.write((value >> 8) & 0xFF);
        buf.write(value & 0xFF);
    }

    /** Emit PUSH32 with a 32-byte big-endian value. */
    public void push32(BigInteger value) {
        emit(EvmOpcode.PUSH32);
        byte[] raw = value.toByteArray();
        // Pad to 32 bytes
        byte[] padded = new byte[32];
        int srcStart = Math.max(0, raw.length - 32);
        int dstStart = 32 - Math.min(raw.length, 32);
        System.arraycopy(raw, srcStart, padded, dstStart, Math.min(raw.length, 32));
        buf.write(padded, 0, 32);
    }

    /**
     * Push an integer value using the minimal number of bytes.
     */
    public void pushInt(long value) {
        if (value == 0) {
            emit(EvmOpcode.PUSH0);
        } else if (value >= 0 && value <= 0xFF) {
            push1((int) value);
        } else if (value >= 0 && value <= 0xFFFF) {
            push2((int) value);
        } else if (value >= 0 && value <= 0xFFFFFFFFL) {
            push4((int) value);
        } else {
            push32(BigInteger.valueOf(value));
        }
    }

    /**
     * Push a 4-byte function selector.
     */
    public void pushSelector(byte[] selector) {
        emit(EvmOpcode.PUSH4);
        if (selector.length < 4) {
            byte[] padded = new byte[4];
            System.arraycopy(selector, 0, padded, 4 - selector.length, selector.length);
            buf.write(padded, 0, 4);
        } else {
            buf.write(selector, 0, 4);
        }
    }

    // ── Label management ─────────────────────────────────────────────────

    /** Generate a unique label name. */
    public String newLabel() {
        return "L_" + (labelCounter++);
    }

    /** Mark the current position as a label. Emits JUMPDEST. */
    public void placeLabel(String label) {
        emit(EvmOpcode.JUMPDEST);
        labelPositions.put(label, pc() - 1); // point to the JUMPDEST byte
    }

    /**
     * Emit a JUMP to a label (resolved later).
     * Uses PUSH2 placeholder + JUMP.
     */
    public void jumpTo(String label) {
        Integer resolved = labelPositions.get(label);
        if (resolved != null) {
            push2(resolved);
            emit(EvmOpcode.JUMP);
        } else {
            emit(EvmOpcode.PUSH2);
            int patchSite = pc();
            buf.write(0); buf.write(0); // placeholder
            emit(EvmOpcode.JUMP);
            pendingJumps.computeIfAbsent(label, k -> new ArrayList<>()).add(patchSite);
        }
    }

    /**
     * Emit conditional JUMPI to a label (resolved later).
     * Stack: [condition] → []
     * Uses PUSH2 placeholder + JUMPI.
     */
    public void jumpIf(String label) {
        Integer resolved = labelPositions.get(label);
        if (resolved != null) {
            push2(resolved);
            emit(EvmOpcode.JUMPI);
        } else {
            emit(EvmOpcode.PUSH2);
            int patchSite = pc();
            buf.write(0); buf.write(0); // placeholder
            emit(EvmOpcode.JUMPI);
            pendingJumps.computeIfAbsent(label, k -> new ArrayList<>()).add(patchSite);
        }
    }

    // ── Memory helpers ───────────────────────────────────────────────────

    /** Store a 32-byte word to memory at offset. Stack: [value] → [] */
    public void mstoreAt(int offset) {
        pushInt(offset);
        emit(EvmOpcode.MSTORE);
        if (offset + 32 > memoryHighWater) memoryHighWater = offset + 32;
    }

    /** Load a 32-byte word from memory at offset. Stack: [] → [value] */
    public void mloadAt(int offset) {
        pushInt(offset);
        emit(EvmOpcode.MLOAD);
        if (offset + 32 > memoryHighWater) memoryHighWater = offset + 32;
    }

    // ── Storage helpers ──────────────────────────────────────────────────

    /** Emit SLOAD for a given slot index. Stack: [] → [value] */
    public void sloadSlot(int slot) {
        pushInt(slot);
        emit(EvmOpcode.SLOAD);
    }

    /** Emit SLOAD for a 256-bit slot. Stack: [] → [value] */
    public void sloadSlot(java.math.BigInteger slot) {
        push32(slot);
        emit(EvmOpcode.SLOAD);
    }

    /** Emit SSTORE for a given slot index. Stack: [value] → [] */
    public void sstoreSlot(int slot) {
        pushInt(slot);
        emit(EvmOpcode.SSTORE);
    }

    /** Emit SSTORE for a 256-bit slot. Stack: [value] → [] */
    public void sstoreSlot(java.math.BigInteger slot) {
        push32(slot);
        emit(EvmOpcode.SSTORE);
    }

    // ── Revert helpers ───────────────────────────────────────────────────

    /** Emit REVERT(0, 0) — revert with no data. */
    public void revert0() {
        emit(EvmOpcode.PUSH0);
        emit(EvmOpcode.PUSH0);
        emit(EvmOpcode.REVERT);
    }

    /**
     * Emit REVERT with an Error(string) ABI-encoded message.
     * Follows Solidity's Error(string) selector: 0x08c379a0
     * Layout in memory at offset 0:
     *   [0x00] selector  (4 bytes, left-padded to 32)
     *   [0x20] offset to string data = 0x20
     *   [0x40] string length
     *   [0x60] string data (padded to 32)
     */
    public void revertWithMessage(String message) {
        byte[] msgBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int paddedLen = ((msgBytes.length + 31) / 32) * 32;
        int totalSize = 4 + 32 + 32 + paddedLen;

        // Store Error(string) selector at memory[0x00]
        // 0x08c379a0 left-shifted to fill first 4 bytes of word
        push32(new java.math.BigInteger("08c379a0", 16)
                .shiftLeft(224));
        pushInt(0);
        emit(EvmOpcode.MSTORE);

        // Store offset to string data = 0x20
        pushInt(0x20);
        pushInt(0x04);
        emit(EvmOpcode.MSTORE);

        // Store string length
        pushInt(msgBytes.length);
        pushInt(0x24);
        emit(EvmOpcode.MSTORE);

        // Store string data (up to 32 bytes for simplicity)
        if (msgBytes.length > 0) {
            byte[] padded = new byte[32];
            System.arraycopy(msgBytes, 0, padded, 0, Math.min(msgBytes.length, 32));
            push32(new java.math.BigInteger(1, padded));
            pushInt(0x44);
            emit(EvmOpcode.MSTORE);
        }

        pushInt(totalSize);
        pushInt(0);
        emit(EvmOpcode.REVERT);
    }

    // ── Output & resolve ─────────────────────────────────────────────────

    /**
     * Resolve all pending forward jumps and return final bytecode.
     *
     * @throws IllegalStateException if any labels remain unresolved
     */
    public byte[] resolve() {
        byte[] code = buf.toByteArray();

        for (Map.Entry<String, List<Integer>> entry : pendingJumps.entrySet()) {
            String label = entry.getKey();
            Integer pos = labelPositions.get(label);
            if (pos == null) {
                throw new IllegalStateException("Unresolved label: " + label);
            }
            for (int patchSite : entry.getValue()) {
                // Write 2-byte big-endian offset
                code[patchSite] = (byte) ((pos >> 8) & 0xFF);
                code[patchSite + 1] = (byte) (pos & 0xFF);
            }
        }

        return code;
    }

    /** Return current bytecode WITHOUT resolving (for inspection). */
    public byte[] toByteArray() {
        return buf.toByteArray();
    }

    /** Current size in bytes. */
    public int size() {
        return buf.size();
    }

    /** Append raw bytes. */
    public void writeRaw(byte[] data) {
        buf.write(data, 0, data.length);
    }

    /** Append raw byte. */
    public void writeByte(int b) {
        buf.write(b & 0xFF);
    }
}
