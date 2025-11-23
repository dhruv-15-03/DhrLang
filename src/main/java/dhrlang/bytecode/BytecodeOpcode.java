package dhrlang.bytecode;

public enum BytecodeOpcode {
    CONST(1),
    LOAD_LOCAL(2), STORE_LOCAL(3),
    ADD(4), SUB(5), MUL(6), DIV(7),
    EQ(8), NEQ(9), LT(10), LE(11), GT(12), GE(13),
    JUMP(14), JUMP_IF_FALSE(15),
    PRINT(16),
    RETURN(17),
    NEG(18), NOT(19),
    NEW_ARRAY(20), LOAD_ELEM(21), STORE_ELEM(22), ARRAY_LENGTH(23),
    CALL(24),
    GET_STATIC(25), SET_STATIC(26),
    GET_FIELD(27), SET_FIELD(28),
    TRY_PUSH(29), TRY_POP(30),
    THROW(31), CATCH_BIND(32);

    public final int code;
    BytecodeOpcode(int code){ this.code = code; }
    public static BytecodeOpcode from(int code){
        for(var op: values()) if(op.code==code) return op;
        throw new IllegalArgumentException("Unknown opcode "+code);
    }
}
