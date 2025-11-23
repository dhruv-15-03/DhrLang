# DhrLang Bytecode (DHBC) Format v1

Status: Experimental (alpha). Backward compatibility is not yet guaranteed.

Header:
- Magic: 0x44484243 ('D' 'H' 'B' 'C') (4 bytes)
- Version: 1 (4 bytes)

Constant Pool:
- u32 count
- Repeated entries:
  - u8 tag: 0=NULL, 1=LONG, 2=DOUBLE, 3=STRING, 4=BOOLEAN
  - payload per tag (none | i64 | f64 | UTF | u8)

Functions:
- u32 functionCount
- For each function (only the first is executed for now):
  - UTF functionName
  - u32 instructionCount (labels elided)
  - instruction stream:
    - u32 opcode
    - operands (u32, i32, or flag) depending on opcode

Opcodes (subset aligned with IR Phase 1):
- 1 CONST: (targetSlot:i32, constIndex:i32)
- 2 LOAD_LOCAL: (slot:i32, targetSlot:i32)
- 3 STORE_LOCAL: (sourceSlot:i32, destSlot:i32)
- 4 ADD | 5 SUB | 6 MUL | 7 DIV: (leftSlot:i32, rightSlot:i32, targetSlot:i32)
- 8 EQ | 9 NEQ | 10 LT | 11 LE | 12 GT | 13 GE: (leftSlot, rightSlot, targetSlot)
- 14 JUMP: (targetIndex:i32)
- 15 JUMP_IF_FALSE: (condSlot:i32, targetIndex:i32)
- 16 PRINT: (slot:i32, newline:bool)
- 17 RETURN: (slotOrNeg1:i32)
- 18 NEG: (sourceSlot:i32, targetSlot:i32)
- 19 NOT: (sourceSlot:i32, targetSlot:i32)

Semantics:
- Locals are an Object[256] register file. Arithmetic uses long/double with string concat fallback for ADD.
- Truthiness: null=false, boolean as-is, number zero=false, others=true.
- Comparison: numbers via numeric compare; others via String.compareTo.
- Division by zero yields NaN for floating path, 0 for integer path (temporary behavior subject to change).

Limitations:
- Single function executed; calls/returns not yet supported.
- No objects/fields, arrays, exceptions, or short-circuit logical ops.

Planned:
- Multi-function modules, CALL/RET, stack frame model.
- Arrays, objects/fields, try/catch.
- Optimized constant pool and instruction encoding.
