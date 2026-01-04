# DhrLang Bytecode (DHBC) Format v2

Status: Implemented and versioned. Backward compatibility across major format versions is not guaranteed.

This document describes the serialized bytecode consumed by the bytecode VM. It is intended as a developer-facing format reference.

## Header
- Magic: 0x44484243 ('D' 'H' 'B' 'C') (4 bytes)
- Version: 2 (4 bytes)

## Constant Pool
- i32 count
- Repeated entries:
  - u8 tag:
    - 0 = NULL
    - 1 = LONG
    - 2 = DOUBLE
    - 3 = STRING
    - 4 = BOOLEAN
  - payload per tag:
    - NULL: none
    - LONG: i64
    - DOUBLE: f64
    - STRING: modified UTF (DataOutput.writeUTF)
    - BOOLEAN: u8

Notes:
- Names (class names, field names, catch types) are stored as STRING entries in the constant pool.

## Functions
- i32 functionCount
- For each function:
  - UTF functionName (e.g. "Main.main", "Foo.bar")
  - i32 instructionCount
  - instruction stream (instructionCount entries):
    - i32 opcode
    - operands (i32 and/or boolean), depending on opcode

Entrypoint resolution (VM behavior):
- Prefer function named "Main.main".
- Otherwise the first function whose name ends with ".main".
- If `dhrlang.bytecode.strictEntry=true`, missing entrypoint is rejected.

## Execution Model
- Each frame has a fixed Object[256] slot array.
- Calls create new frames; returns can write into a caller slot.
- Exceptions use a per-frame handler stack.

## Opcodes
All operands are i32 unless specified.

- CONST: (targetSlot, constIndex)
- LOAD_LOCAL: (slot, targetSlot)
- STORE_LOCAL: (sourceSlot, destSlot)

- ADD|SUB|MUL|DIV: (leftSlot, rightSlot, targetSlot)
- EQ|NEQ|LT|LE|GT|GE: (leftSlot, rightSlot, targetSlot)

- JUMP: (targetPc)
- JUMP_IF_FALSE: (condSlot, targetPc)

- PRINT: (slot, newline:boolean)
- RETURN: (slotOrNeg1)

- NEG|NOT: (sourceSlot, targetSlot)

- NEW_ARRAY: (sizeSlot, targetSlot, elementTypeConstIndexOrNeg1)
- LOAD_ELEM: (arraySlot, indexSlot, targetSlot)
- STORE_ELEM: (arraySlot, indexSlot, valueSlot)
- ARRAY_LENGTH: (arraySlot, targetSlot)

- CALL: (functionIndex, arg0SlotOrNeg1, arg1SlotOrNeg1, arg2SlotOrNeg1, arg3SlotOrNeg1, destSlotOrNeg1)

- GET_STATIC: (classNameConstIndex, fieldNameConstIndex, targetSlot)
- SET_STATIC: (classNameConstIndex, fieldNameConstIndex, valueSlot)
- GET_FIELD: (objectSlot, fieldNameConstIndex, targetSlot)
- SET_FIELD: (objectSlot, fieldNameConstIndex, valueSlot)

- TRY_PUSH: (catchPc, catchTypeConstIndex)
- TRY_POP: ()
- THROW: (valueSlot)
- CATCH_BIND: (targetSlot)

## Semantics (selected)
- Truthiness: null=false, boolean as-is, numeric zero=false, others=true.
- ADD: numeric addition for two numbers; string concatenation if either operand is a string.
- DIV: division by zero throws a runtime arithmetic error.
- Arrays: bounds-checked; new arrays are initialized with element-type defaults when available.

## Validation / Untrusted Mode
The bytecode VM validates bytecode before executing:
- jump targets must be within the instruction array
- constant pool indices must be in range and of the expected type
- function indices must be in range
- structural validation for try/catch control flow (enabled by default)

For untrusted code, run the VM with:
- `dhrlang.bytecode.untrusted=true`

This enables conservative size/shape caps for the bytecode and tighter execution limits.
