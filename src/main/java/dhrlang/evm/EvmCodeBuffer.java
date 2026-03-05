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

    // ── Emit helpers ─────────────────────────────────────────────────────

    /** Emit a single opcode (no operands). */
    public void emit(EvmOpcode op) {
        buf.write(op.code);
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
    }

    /** Load a 32-byte word from memory at offset. Stack: [] → [value] */
    public void mloadAt(int offset) {
        pushInt(offset);
        emit(EvmOpcode.MLOAD);
    }

    // ── Storage helpers ──────────────────────────────────────────────────

    /** Emit SLOAD for a given slot index. Stack: [] → [value] */
    public void sloadSlot(int slot) {
        pushInt(slot);
        emit(EvmOpcode.SLOAD);
    }

    /** Emit SSTORE for a given slot index. Stack: [value] → [] */
    public void sstoreSlot(int slot) {
        pushInt(slot);
        emit(EvmOpcode.SSTORE);
    }

    // ── Revert helpers ───────────────────────────────────────────────────

    /** Emit REVERT(0, 0) — revert with no data. */
    public void revert0() {
        emit(EvmOpcode.PUSH0);
        emit(EvmOpcode.PUSH0);
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
