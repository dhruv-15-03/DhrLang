package dhrlang.evm;

/**
 * Defines all EVM opcodes used by the DhrLang EVM backend.
 * 
 * <p>Reference: <a href="https://www.evm.codes/">evm.codes</a></p>
 * <p>Only opcodes needed by the DhrLang compiler are included.</p>
 */
public enum EvmOpcode {
    // Arithmetic
    STOP(0x00, 0, 0, 0),
    ADD(0x01, 2, 1, 3),
    MUL(0x02, 2, 1, 5),
    SUB(0x03, 2, 1, 3),
    DIV(0x04, 2, 1, 5),
    SDIV(0x05, 2, 1, 5),
    MOD(0x06, 2, 1, 5),
    SMOD(0x07, 2, 1, 5),
    ADDMOD(0x08, 3, 1, 8),
    MULMOD(0x09, 3, 1, 8),
    EXP(0x0A, 2, 1, 10),
    SIGNEXTEND(0x0B, 2, 1, 5),

    // Comparison & Bitwise Logic
    LT(0x10, 2, 1, 3),
    GT(0x11, 2, 1, 3),
    SLT(0x12, 2, 1, 3),
    SGT(0x13, 2, 1, 3),
    EQ(0x14, 2, 1, 3),
    ISZERO(0x15, 1, 1, 3),
    AND(0x16, 2, 1, 3),
    OR(0x17, 2, 1, 3),
    XOR(0x18, 2, 1, 3),
    NOT(0x19, 1, 1, 3),
    BYTE(0x1A, 2, 1, 3),
    SHL(0x1B, 2, 1, 3),
    SHR(0x1C, 2, 1, 3),
    SAR(0x1D, 2, 1, 3),

    // SHA3
    SHA3(0x20, 2, 1, 30),

    // Environmental Information
    ADDRESS(0x30, 0, 1, 2),
    BALANCE(0x31, 1, 1, 100),
    ORIGIN(0x32, 0, 1, 2),
    CALLER(0x33, 0, 1, 2),
    CALLVALUE(0x34, 0, 1, 2),
    CALLDATALOAD(0x35, 1, 1, 3),
    CALLDATASIZE(0x36, 0, 1, 2),
    CALLDATACOPY(0x37, 3, 0, 3),
    CODESIZE(0x38, 0, 1, 2),
    CODECOPY(0x39, 3, 0, 3),
    GASPRICE(0x3A, 0, 1, 2),

    // Block Information
    BLOCKHASH(0x40, 1, 1, 20),
    COINBASE(0x41, 0, 1, 2),
    TIMESTAMP(0x42, 0, 1, 2),
    NUMBER(0x43, 0, 1, 2),
    DIFFICULTY(0x44, 0, 1, 2),
    GASLIMIT(0x45, 0, 1, 2),
    CHAINID(0x46, 0, 1, 2),
    SELFBALANCE(0x47, 0, 1, 5),

    // Stack, Memory, Storage
    POP(0x50, 1, 0, 2),
    MLOAD(0x51, 1, 1, 3),
    MSTORE(0x52, 2, 0, 3),
    MSTORE8(0x53, 2, 0, 3),
    SLOAD(0x54, 1, 1, 100),
    SSTORE(0x55, 2, 0, 100),
    JUMP(0x56, 1, 0, 8),
    JUMPI(0x57, 2, 0, 10),
    PC(0x58, 0, 1, 2),
    MSIZE(0x59, 0, 1, 2),
    GAS(0x5A, 0, 1, 2),
    JUMPDEST(0x5B, 0, 0, 1),

    // Push operations (PUSH1..PUSH32)
    PUSH0(0x5F, 0, 1, 2),
    PUSH1(0x60, 0, 1, 3),
    PUSH2(0x61, 0, 1, 3),
    PUSH3(0x62, 0, 1, 3),
    PUSH4(0x63, 0, 1, 3),
    PUSH8(0x67, 0, 1, 3),
    PUSH20(0x73, 0, 1, 3),
    PUSH32(0x7F, 0, 1, 3),

    // Duplication operations
    DUP1(0x80, 1, 2, 3),
    DUP2(0x81, 2, 3, 3),
    DUP3(0x82, 3, 4, 3),
    DUP4(0x83, 4, 5, 3),

    // Exchange operations
    SWAP1(0x90, 2, 2, 3),
    SWAP2(0x91, 3, 3, 3),
    SWAP3(0x92, 4, 4, 3),

    // Logging
    LOG0(0xA0, 2, 0, 375),
    LOG1(0xA1, 3, 0, 750),
    LOG2(0xA2, 4, 0, 1125),
    LOG3(0xA3, 5, 0, 1500),
    LOG4(0xA4, 6, 0, 1875),

    // System operations
    CREATE(0xF0, 3, 1, 32000),
    CALL(0xF1, 7, 1, 100),
    CALLCODE(0xF2, 7, 1, 100),
    RETURN(0xF3, 2, 0, 0),
    DELEGATECALL(0xF4, 6, 1, 100),
    CREATE2(0xF5, 4, 1, 32000),
    STATICCALL(0xFA, 6, 1, 100),
    REVERT(0xFD, 2, 0, 0),
    INVALID(0xFE, 0, 0, 0),
    SELFDESTRUCT(0xFF, 1, 0, 5000);

    /** Raw byte value of this opcode. */
    public final int code;
    /** Number of stack items consumed. */
    public final int stackIn;
    /** Number of stack items produced. */
    public final int stackOut;
    /** Base gas cost. */
    public final int gasCost;

    EvmOpcode(int code, int stackIn, int stackOut, int gasCost) {
        this.code = code;
        this.stackIn = stackIn;
        this.stackOut = stackOut;
        this.gasCost = gasCost;
    }

    /** Get the byte representation. */
    public byte toByte() {
        return (byte) code;
    }

    /**
     * Returns a minimal PUSHn opcode for the given byte count (1..32).
     */
    public static EvmOpcode pushForSize(int byteCount) {
        return switch (byteCount) {
            case 0 -> PUSH0;
            case 1 -> PUSH1;
            case 2 -> PUSH2;
            case 3 -> PUSH3;
            case 4 -> PUSH4;
            case 8 -> PUSH8;
            case 20 -> PUSH20;
            case 32 -> PUSH32;
            default -> {
                // For sizes not directly modelled, use next larger available
                if (byteCount <= 4) yield PUSH4;
                if (byteCount <= 8) yield PUSH8;
                if (byteCount <= 20) yield PUSH20;
                yield PUSH32;
            }
        };
    }
}
