package dhrlang.debug;

import dhrlang.evm.EvmOpcode;

import java.math.BigInteger;
import java.util.*;

/**
 * Step-by-step EVM bytecode debugger for DhrLang-compiled contracts.
 *
 * <p>Simulates EVM execution of raw bytecode with full control over
 * stepping, breakpoints, and state inspection.  The debugger maintains
 * a program counter, 256-bit word stack, byte-addressable memory, and
 * a key-value storage map that mirrors EVM semantics.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 *   var dbg = new ContractDebugger(bytecode);
 *   dbg.setBreakpoint(0x10);
 *   DebugState st = dbg.continueExecution();
 *   System.out.println(dbg.getDisassembly(0, 30));
 * </pre>
 */
public class ContractDebugger {

    // ── Debug state snapshot ─────────────────────────────────────────────

    /**
     * Immutable snapshot of the debugger state at a single point in time.
     */
    public static class DebugState {
        private final int pc;
        private final EvmOpcode opcode;
        private final List<BigInteger> stack;
        private final long gasUsed;
        private final boolean halted;
        private final String haltReason;
        private final int stepNumber;

        public DebugState(int pc, EvmOpcode opcode, List<BigInteger> stack,
                          long gasUsed, boolean halted, String haltReason, int stepNumber) {
            this.pc = pc;
            this.opcode = opcode;
            this.stack = Collections.unmodifiableList(new ArrayList<>(stack));
            this.gasUsed = gasUsed;
            this.halted = halted;
            this.haltReason = haltReason;
            this.stepNumber = stepNumber;
        }

        public int getPc()             { return pc; }
        public EvmOpcode getOpcode()   { return opcode; }
        public List<BigInteger> getStack() { return stack; }
        public long getGasUsed()       { return gasUsed; }
        public boolean isHalted()      { return halted; }
        public String getHaltReason()  { return haltReason; }
        public int getStepNumber()     { return stepNumber; }

        @Override
        public String toString() {
            return String.format("Step %d: PC=0x%04X %s stack=%s gas=%d%s",
                    stepNumber, pc,
                    opcode != null ? opcode.name() : "???",
                    formatStack(stack),
                    gasUsed,
                    halted ? " [HALTED: " + haltReason + "]" : "");
        }

        private static String formatStack(List<BigInteger> stack) {
            if (stack.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            int limit = Math.min(stack.size(), 5);
            for (int i = 0; i < limit; i++) {
                if (i > 0) sb.append(", ");
                sb.append("0x").append(stack.get(i).toString(16));
            }
            if (stack.size() > 5) sb.append(", ...(").append(stack.size() - 5).append(" more)");
            sb.append("]");
            return sb.toString();
        }
    }

    // ── Constants ────────────────────────────────────────────────────────

    private static final int MAX_STACK_SIZE = 1024;
    private static final int MAX_STEPS = 1_000_000;
    private static final BigInteger UINT256_MAX =
            BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);

    // ── Opcode lookup ────────────────────────────────────────────────────

    private static final Map<Integer, EvmOpcode> OPCODE_MAP = new HashMap<>();
    static {
        for (EvmOpcode op : EvmOpcode.values()) {
            OPCODE_MAP.put(op.code & 0xFF, op);
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private final byte[] bytecode;
    private int pc = 0;
    private final Deque<BigInteger> stack = new ArrayDeque<>();
    private final byte[] memory;
    private final Map<BigInteger, BigInteger> storage = new HashMap<>();
    private final Set<Integer> breakpoints = new TreeSet<>();
    private long gasUsed = 0;
    private boolean halted = false;
    private String haltReason = null;
    private int stepNumber = 0;
    private final List<DebugState> history = new ArrayList<>();
    private int maxMemorySize;

    // ── Constructor ──────────────────────────────────────────────────────

    /**
     * Create a debugger for the given bytecode.
     *
     * @param bytecode raw EVM bytecode to debug
     */
    public ContractDebugger(byte[] bytecode) {
        this(bytecode, 4096);
    }

    /**
     * Create a debugger with a custom memory size.
     *
     * @param bytecode      raw EVM bytecode
     * @param maxMemorySize maximum memory in bytes
     */
    public ContractDebugger(byte[] bytecode, int maxMemorySize) {
        this.bytecode = Arrays.copyOf(bytecode, bytecode.length);
        this.maxMemorySize = maxMemorySize;
        this.memory = new byte[maxMemorySize];
    }

    // ── Breakpoints ──────────────────────────────────────────────────────

    /** Set a breakpoint at a PC offset. */
    public void setBreakpoint(int pcOffset) {
        breakpoints.add(pcOffset);
    }

    /** Remove a breakpoint. */
    public void removeBreakpoint(int pcOffset) {
        breakpoints.remove(pcOffset);
    }

    /** Clear all breakpoints. */
    public void clearBreakpoints() {
        breakpoints.clear();
    }

    /** Get all breakpoint locations. */
    public Set<Integer> getBreakpoints() {
        return Collections.unmodifiableSet(breakpoints);
    }

    // ── Execution control ────────────────────────────────────────────────

    /**
     * Execute a single instruction and return the resulting state.
     */
    public DebugState step() {
        if (halted) {
            return captureState();
        }
        if (pc >= bytecode.length) {
            halt("End of bytecode (PC=" + pc + ")");
            return captureState();
        }
        if (stepNumber >= MAX_STEPS) {
            halt("Maximum steps exceeded (" + MAX_STEPS + ")");
            return captureState();
        }

        int opByte = bytecode[pc] & 0xFF;
        EvmOpcode opcode = OPCODE_MAP.get(opByte);

        try {
            executeOpcode(opByte, opcode);
        } catch (Exception e) {
            halt("Exception: " + e.getMessage());
        }

        stepNumber++;
        DebugState state = captureState();
        history.add(state);
        return state;
    }

    /**
     * Continue execution until a breakpoint is hit, the program halts,
     * or the step limit is reached.
     */
    public DebugState continueExecution() {
        // Step past current breakpoint if we're on one
        if (breakpoints.contains(pc) && stepNumber > 0) {
            step();
        }

        while (!halted) {
            if (breakpoints.contains(pc)) {
                return captureState();
            }
            step();
            if (stepNumber >= MAX_STEPS) break;
        }
        return captureState();
    }

    /**
     * Run until completion (no breakpoints, up to step limit).
     */
    public DebugState runToEnd() {
        while (!halted && stepNumber < MAX_STEPS) {
            step();
        }
        return captureState();
    }

    // ── State inspection ─────────────────────────────────────────────────

    /** Current program counter. */
    public int getPc() { return pc; }

    /** Current stack as a list (top at index 0). */
    public List<BigInteger> getStack() {
        return new ArrayList<>(stack);
    }

    /** Stack depth. */
    public int getStackDepth() { return stack.size(); }

    /** Peek at the top of stack (or null if empty). */
    public BigInteger peekStack() {
        return stack.peek();
    }

    /** Get raw memory. */
    public byte[] getMemory() {
        return Arrays.copyOf(memory, memory.length);
    }

    /** Read a 32-byte word from memory at offset. */
    public BigInteger readMemory(int offset) {
        if (offset < 0 || offset + 32 > memory.length) return BigInteger.ZERO;
        byte[] word = new byte[32];
        System.arraycopy(memory, offset, word, 0, 32);
        return new BigInteger(1, word);
    }

    /** Storage map: slot→value. */
    public Map<BigInteger, BigInteger> getStorage() {
        return Collections.unmodifiableMap(storage);
    }

    /** Read a storage slot. */
    public BigInteger readStorage(BigInteger slot) {
        return storage.getOrDefault(slot, BigInteger.ZERO);
    }

    /** Total gas consumed so far. */
    public long getGasUsed() { return gasUsed; }

    /** Whether execution has halted. */
    public boolean isHalted() { return halted; }

    /** Reason for halting (null if still running). */
    public String getHaltReason() { return haltReason; }

    /** Number of steps executed. */
    public int getStepNumber() { return stepNumber; }

    /** Get the current opcode at PC. */
    public EvmOpcode getCurrentOpcode() {
        if (pc >= bytecode.length) return null;
        return OPCODE_MAP.get(bytecode[pc] & 0xFF);
    }

    /** Get execution history. */
    public List<DebugState> getHistory() {
        return Collections.unmodifiableList(history);
    }

    // ── Disassembly ──────────────────────────────────────────────────────

    /**
     * Disassemble bytecode from startPc to endPc (exclusive).
     *
     * @param startPc first byte to disassemble
     * @param endPc   byte after last byte to disassemble
     * @return formatted disassembly string
     */
    public String getDisassembly(int startPc, int endPc) {
        StringBuilder sb = new StringBuilder();
        int i = Math.max(0, startPc);
        int end = Math.min(endPc, bytecode.length);

        while (i < end) {
            int op = bytecode[i] & 0xFF;
            EvmOpcode opcode = OPCODE_MAP.get(op);
            String marker = (i == pc) ? ">>>" : "   ";
            String bpMarker = breakpoints.contains(i) ? "•" : " ";

            sb.append(String.format("%s%s 0x%04X: ", marker, bpMarker, i));

            if (opcode != null) {
                sb.append(opcode.name());
                int pushBytes = pushDataSize(op);
                if (pushBytes > 0 && i + 1 + pushBytes <= bytecode.length) {
                    sb.append(" 0x");
                    for (int j = 0; j < pushBytes; j++) {
                        sb.append(String.format("%02x", bytecode[i + 1 + j] & 0xFF));
                    }
                    i += pushBytes;
                }
            } else {
                sb.append(String.format("UNKNOWN(0x%02X)", op));
            }

            sb.append("\n");
            i++;
        }
        return sb.toString();
    }

    /**
     * Full disassembly of the bytecode.
     */
    public String getFullDisassembly() {
        return getDisassembly(0, bytecode.length);
    }

    // ── Reset ────────────────────────────────────────────────────────────

    /** Reset the debugger to initial state. */
    public void reset() {
        pc = 0;
        stack.clear();
        Arrays.fill(memory, (byte) 0);
        storage.clear();
        gasUsed = 0;
        halted = false;
        haltReason = null;
        stepNumber = 0;
        history.clear();
    }

    // ── Opcode execution ─────────────────────────────────────────────────

    private void executeOpcode(int opByte, EvmOpcode opcode) {
        if (opcode != null) {
            gasUsed += opcode.gasCost;
        }

        // PUSH0..PUSH32 range: 0x5F..0x7F
        if (opByte >= 0x5F && opByte <= 0x7F) {
            executePush(opByte);
            return;
        }

        // DUP range: 0x80..0x8F
        if (opByte >= 0x80 && opByte <= 0x8F) {
            int depth = opByte - 0x80 + 1;
            executeDup(depth);
            return;
        }

        // SWAP range: 0x90..0x9F
        if (opByte >= 0x90 && opByte <= 0x9F) {
            int depth = opByte - 0x90 + 1;
            executeSwap(depth);
            return;
        }

        switch (opByte) {
            case 0x00 -> halt("STOP");
            case 0x01 -> { BigInteger a = pop(); BigInteger b = pop(); push(a.add(b).and(UINT256_MAX)); }
            case 0x02 -> { BigInteger a = pop(); BigInteger b = pop(); push(a.multiply(b).and(UINT256_MAX)); }
            case 0x03 -> { BigInteger a = pop(); BigInteger b = pop(); push(a.subtract(b).and(UINT256_MAX)); }
            case 0x04 -> { BigInteger a = pop(); BigInteger b = pop(); push(b.equals(BigInteger.ZERO) ? BigInteger.ZERO : a.divide(b)); }
            case 0x06 -> { BigInteger a = pop(); BigInteger b = pop(); push(b.equals(BigInteger.ZERO) ? BigInteger.ZERO : a.mod(b)); }
            case 0x10 -> { BigInteger a = pop(); BigInteger b = pop(); push(a.compareTo(b) < 0 ? BigInteger.ONE : BigInteger.ZERO); }
            case 0x11 -> { BigInteger a = pop(); BigInteger b = pop(); push(a.compareTo(b) > 0 ? BigInteger.ONE : BigInteger.ZERO); }
            case 0x14 -> { BigInteger a = pop(); BigInteger b = pop(); push(a.equals(b) ? BigInteger.ONE : BigInteger.ZERO); }
            case 0x15 -> { BigInteger a = pop(); push(a.equals(BigInteger.ZERO) ? BigInteger.ONE : BigInteger.ZERO); }
            case 0x16 -> { BigInteger a = pop(); BigInteger b = pop(); push(a.and(b)); }
            case 0x17 -> { BigInteger a = pop(); BigInteger b = pop(); push(a.or(b)); }
            case 0x18 -> { BigInteger a = pop(); BigInteger b = pop(); push(a.xor(b)); }
            case 0x19 -> { BigInteger a = pop(); push(UINT256_MAX.xor(a)); }
            case 0x50 -> pop();
            case 0x51 -> { int offset = pop().intValueExact(); push(mload(offset)); }
            case 0x52 -> { int offset = pop().intValueExact(); BigInteger value = pop(); mstore(offset, value); }
            case 0x54 -> { BigInteger slot = pop(); push(storage.getOrDefault(slot, BigInteger.ZERO)); }
            case 0x55 -> { BigInteger slot = pop(); BigInteger value = pop(); storage.put(slot, value); }
            case 0x56 -> { int dest = pop().intValueExact(); pc = dest; return; } // JUMP
            case 0x57 -> { int dest = pop().intValueExact(); BigInteger cond = pop();
                           if (!cond.equals(BigInteger.ZERO)) { pc = dest; return; } }
            case 0x58 -> push(BigInteger.valueOf(pc));
            case 0x5B -> { /* JUMPDEST — no-op */ }
            case 0xF3 -> { pop(); pop(); halt("RETURN"); return; }
            case 0xFD -> { pop(); pop(); halt("REVERT"); return; }
            case 0xFE -> halt("INVALID");
            default -> { /* Unknown opcode — just advance PC */ }
        }
        pc++;
    }

    private void executePush(int opByte) {
        if (opByte == 0x5F) {
            // PUSH0
            push(BigInteger.ZERO);
            pc++;
            return;
        }

        int n = opByte - 0x60 + 1; // PUSH1=1, PUSH2=2, ... PUSH32=32
        byte[] data = new byte[n];
        for (int i = 0; i < n && pc + 1 + i < bytecode.length; i++) {
            data[i] = bytecode[pc + 1 + i];
        }
        push(new BigInteger(1, data));
        pc += 1 + n;
    }

    private void executeDup(int depth) {
        List<BigInteger> items = new ArrayList<>(stack);
        if (depth > items.size()) {
            halt("Stack underflow on DUP" + depth);
            pc++;
            return;
        }
        push(items.get(depth - 1));
        pc++;
    }

    private void executeSwap(int depth) {
        List<BigInteger> items = new ArrayList<>(stack);
        if (depth + 1 > items.size()) {
            halt("Stack underflow on SWAP" + depth);
            pc++;
            return;
        }
        // Swap top (index 0) with item at `depth`
        BigInteger top = items.get(0);
        BigInteger other = items.get(depth);
        items.set(0, other);
        items.set(depth, top);
        stack.clear();
        for (BigInteger item : items) {
            stack.addLast(item);
        }
        pc++;
    }

    // ── Stack helpers ────────────────────────────────────────────────────

    private void push(BigInteger value) {
        if (stack.size() >= MAX_STACK_SIZE) {
            halt("Stack overflow");
            return;
        }
        stack.push(value);
    }

    private BigInteger pop() {
        if (stack.isEmpty()) {
            halt("Stack underflow");
            return BigInteger.ZERO;
        }
        return stack.pop();
    }

    // ── Memory helpers ───────────────────────────────────────────────────

    private BigInteger mload(int offset) {
        if (offset < 0 || offset + 32 > memory.length) return BigInteger.ZERO;
        byte[] word = new byte[32];
        System.arraycopy(memory, offset, word, 0, 32);
        return new BigInteger(1, word);
    }

    private void mstore(int offset, BigInteger value) {
        if (offset < 0 || offset + 32 > memory.length) return;
        byte[] raw = value.toByteArray();
        byte[] padded = new byte[32];
        int srcStart = Math.max(0, raw.length - 32);
        int dstStart = 32 - Math.min(raw.length, 32);
        System.arraycopy(raw, srcStart, padded, dstStart, Math.min(raw.length, 32));
        System.arraycopy(padded, 0, memory, offset, 32);
    }

    // ── Misc helpers ─────────────────────────────────────────────────────

    private void halt(String reason) {
        halted = true;
        haltReason = reason;
    }

    private DebugState captureState() {
        EvmOpcode op = (pc < bytecode.length) ? OPCODE_MAP.get(bytecode[pc] & 0xFF) : null;
        return new DebugState(pc, op, new ArrayList<>(stack), gasUsed, halted, haltReason, stepNumber);
    }

    /**
     * Number of data bytes following a PUSH opcode.
     * Returns 0 for non-PUSH opcodes.
     */
    private static int pushDataSize(int opByte) {
        if (opByte == 0x5F) return 0; // PUSH0
        if (opByte >= 0x60 && opByte <= 0x7F) return opByte - 0x60 + 1;
        return 0;
    }
}
