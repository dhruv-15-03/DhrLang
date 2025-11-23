package dhrlang.ir;

/** Draft opcode set (Phase 1 will flesh out operands & validation). */
public enum IrOpcode {
    CONST,
    LOAD_LOCAL, STORE_LOCAL,
    ADD, SUB, MUL, DIV, NEG,
    EQ, NEQ, LT, LE, GT, GE,
    JUMP, JUMP_IF_FALSE,
    PRINT,
    RETURN,
    NOP
}
