package dhrlang.evm;

import dhrlang.ast.*;
import dhrlang.lexer.TokenType;
import dhrlang.validation.StorageLayouter;
import dhrlang.validation.StorageLayouter.ContractLayout;
import dhrlang.validation.StorageLayouter.SlotInfo;

import java.util.*;

/**
 * Compiles a DhrLang {@code @contract} class into EVM bytecode.
 *
 * <p>This generator produces two bytecodes:
 * <ol>
 *   <li><b>Creation bytecode</b> — executed once at deployment: runs the
 *       constructor, then copies the runtime bytecode to memory and returns it.</li>
 *   <li><b>Runtime bytecode</b> — the code stored on-chain: dispatches
 *       incoming transactions to the correct function via 4-byte function selectors.</li>
 * </ol>
 *
 * <p><b>Compilation model:</b>
 * <ul>
 *   <li>Local variables are stored in EVM memory starting at offset 0x80
 *       (after fixed scratch space).  Each local gets 32 bytes.</li>
 *   <li>Storage variables use the slot indices from {@link StorageLayouter}.</li>
 *   <li>Function parameters are decoded from calldata (ABI-encoded, 32 B each).</li>
 *   <li>Arithmetic is done on the stack (256-bit words).</li>
 * </ul>
 */
public final class EvmCodeGen {

    /**
     * Size of the scratch space (bytes) reserved at the start of memory.
     * Locals start at this offset.
     */
    private static final int MEMORY_BASE = 0x80;

    /**
     * Size of each local variable slot in memory (bytes).
     */
    private static final int SLOT_SIZE = 0x20;  // 32

    // ── State ────────────────────────────────────────────────────────────

    private final ClassDecl contract;
    private final ContractLayout layout;

    /** Maps local variable names to their memory offsets. */
    private final Map<String, Integer> locals = new LinkedHashMap<>();
    private int nextLocalOffset = MEMORY_BASE;

    /** Maps storage variable names to their slot indices. */
    private final Map<String, Integer> storageSlots = new LinkedHashMap<>();

    private EvmCodeBuffer buf;

    // Loop control — break/continue labels
    private final Deque<String> breakLabels = new ArrayDeque<>();
    private final Deque<String> continueLabels = new ArrayDeque<>();

    // ── Constructor ──────────────────────────────────────────────────────

    public EvmCodeGen(ClassDecl contract, ContractLayout layout) {
        this.contract = contract;
        this.layout = layout;

        // Pre-populate storage slot map
        if (layout != null) {
            for (SlotInfo slot : layout.getSlots()) {
                storageSlots.put(slot.getFieldName(), slot.getSlotIndex());
            }
        }
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Compile the contract and return a {@link CompilationResult}.
     */
    public CompilationResult compile() {
        byte[] runtimeBytecode = compileRuntime();
        byte[] creationBytecode = compileCreation(runtimeBytecode);
        List<Map<String, Object>> abi = AbiGenerator.generate(contract);
        return new CompilationResult(
                creationBytecode,
                runtimeBytecode,
                AbiGenerator.toJson(abi),
                layout,
                abi
        );
    }

    // ── Runtime bytecode ─────────────────────────────────────────────────

    /**
     * The runtime bytecode implements the function dispatch table and
     * all public function bodies.
     */
    private byte[] compileRuntime() {
        buf = new EvmCodeBuffer();

        // ── Function dispatcher ──────────────────────────────────
        // Load first 4 bytes of calldata as function selector
        buf.pushInt(0);                         // offset 0
        buf.emit(EvmOpcode.CALLDATALOAD);       // load 32 bytes from calldata[0]
        buf.pushInt(0xE0);                      // shift right by 224 bits
        buf.emit(EvmOpcode.SHR);                // → 4-byte selector on stack

        // Collect non-constructor, non-event functions
        List<FunctionDecl> publicFunctions = new ArrayList<>();
        for (FunctionDecl fn : contract.getFunctions()) {
            if (!fn.isContractConstructor()
                    && !fn.hasContractAnnotation(ContractAnnotation.EVENT)) {
                publicFunctions.add(fn);
            }
        }

        // Compare selector → jump to matching function
        String[] functionLabels = new String[publicFunctions.size()];
        for (int i = 0; i < publicFunctions.size(); i++) {
            functionLabels[i] = buf.newLabel();
            FunctionDecl fn = publicFunctions.get(i);
            byte[] selector = AbiGenerator.functionSelector(fn);
            buf.emit(EvmOpcode.DUP1);                 // dup selector
            buf.pushSelector(selector);                // push expected
            buf.emit(EvmOpcode.EQ);                    // compare
            buf.jumpIf(functionLabels[i]);             // jump if match
        }

        // No match → revert
        buf.revert0();

        // ── Function bodies ──────────────────────────────────────
        for (int i = 0; i < publicFunctions.size(); i++) {
            emitFunction(publicFunctions.get(i), functionLabels[i]);
        }

        return buf.resolve();
    }

    // ── Creation bytecode ────────────────────────────────────────────────

    /**
     * The creation bytecode optionally runs the constructor, then returns
     * the runtime bytecode.
     */
    private byte[] compileCreation(byte[] runtimeBytecode) {
        EvmCodeBuffer creation = new EvmCodeBuffer();

        // Run constructor if it exists
        FunctionDecl ctor = findConstructor();
        if (ctor != null) {
            EvmCodeGen ctorGen = new EvmCodeGen(contract, layout);
            ctorGen.buf = creation;
            ctorGen.emitFunctionBody(ctor);
        }

        // Copy runtime bytecode to memory and return it
        int rtLen = runtimeBytecode.length;
        // CODECOPY(destOffset=0, offset=<end of creation code>, size=rtLen)
        // We'll use a placeholder: emit the pattern, then patch it.
        // For simplicity: creation code ends at current position + 10 bytes
        // (for the CODECOPY + RETURN sequence).
        //
        // Layout of remaining creation code:
        //   PUSH2 rtLen
        //   DUP1
        //   PUSH2 codeOffset     ← will be patched
        //   PUSH1 0              ← destOffset in memory
        //   CODECOPY
        //   PUSH1 0
        //   RETURN
        //
        // That's: 3 + 1 + 3 + 2 + 1 + 2 + 1 = 13 bytes
        int seqStart = creation.size();
        int codeOffset = seqStart + 13;       // runtime code will be appended here

        creation.push2(rtLen);                 // PUSH2 rtLen
        creation.emit(EvmOpcode.DUP1);         // duplicate for RETURN size
        creation.push2(codeOffset);            // PUSH2 codeOffset
        creation.push1(0);                     // destOffset = 0
        creation.emit(EvmOpcode.CODECOPY);     // CODECOPY
        creation.push1(0);                     // memory offset 0
        creation.emit(EvmOpcode.RETURN);       // RETURN(0, rtLen)

        // Append runtime bytecode after the creation code
        byte[] creationBytes = creation.resolve();
        byte[] full = new byte[creationBytes.length + rtLen];
        System.arraycopy(creationBytes, 0, full, 0, creationBytes.length);
        System.arraycopy(runtimeBytecode, 0, full, creationBytes.length, rtLen);
        return full;
    }

    // ── Function emission ────────────────────────────────────────────────

    private void emitFunction(FunctionDecl fn, String label) {
        resetLocals();
        buf.placeLabel(label);

        // Pop the selector from the stack (left over from dispatch)
        buf.emit(EvmOpcode.POP);

        // Check payable
        if (!fn.isPayable()) {
            // Revert if msg.value > 0
            buf.emit(EvmOpcode.CALLVALUE);
            String okLabel = buf.newLabel();
            buf.emit(EvmOpcode.ISZERO);
            buf.jumpIf(okLabel);
            buf.revert0();
            buf.placeLabel(okLabel);
        }

        // Decode parameters from calldata
        List<VarDecl> params = fn.getParameters();
        for (int i = 0; i < params.size(); i++) {
            String name = params.get(i).getName();
            int offset = allocLocal(name);
            // Calldata offset: 4 (selector) + i * 32
            buf.pushInt(4 + i * 32);
            buf.emit(EvmOpcode.CALLDATALOAD);
            buf.mstoreAt(offset);
        }

        // Emit function body
        emitFunctionBody(fn);

        // Implicit return (stop) if no explicit return was encountered
        buf.emit(EvmOpcode.STOP);
    }

    private void emitFunctionBody(FunctionDecl fn) {
        if (fn.getBody() != null) {
            emitBlock(fn.getBody());
        }
    }

    // ── Statement emission ───────────────────────────────────────────────

    private void emitStatement(Statement stmt) {
        if (stmt instanceof VarDecl) {
            emitVarDecl((VarDecl) stmt);
        } else if (stmt instanceof ExpressionStmt) {
            emitExpressionStmt((ExpressionStmt) stmt);
        } else if (stmt instanceof IfStmt) {
            emitIfStmt((IfStmt) stmt);
        } else if (stmt instanceof WhileStmt) {
            emitWhileStmt((WhileStmt) stmt);
        } else if (stmt instanceof ReturnStmt) {
            emitReturnStmt((ReturnStmt) stmt);
        } else if (stmt instanceof Block) {
            emitBlock((Block) stmt);
        } else if (stmt instanceof BreakStmt) {
            if (!breakLabels.isEmpty()) {
                buf.jumpTo(breakLabels.peek());
            }
        } else if (stmt instanceof ContinueStmt) {
            if (!continueLabels.isEmpty()) {
                buf.jumpTo(continueLabels.peek());
            }
        }
        // FunctionDecl nested inside body — skip (EVM doesn't support nested functions)
    }

    private void emitBlock(Block block) {
        for (Statement stmt : block.getStatements()) {
            emitStatement(stmt);
        }
    }

    private void emitVarDecl(VarDecl decl) {
        int offset = allocLocal(decl.getName());
        if (decl.getInitializer() != null) {
            emitExpression(decl.getInitializer());
            buf.mstoreAt(offset);
        }
    }

    private void emitExpressionStmt(ExpressionStmt stmt) {
        emitExpression(stmt.getExpression());
        // If the expression left a value on the stack, pop it
        buf.emit(EvmOpcode.POP);
    }

    private void emitIfStmt(IfStmt stmt) {
        emitExpression(stmt.getCondition());
        if (stmt.getElseBranch() != null) {
            String elseLabel = buf.newLabel();
            String endLabel = buf.newLabel();
            buf.emit(EvmOpcode.ISZERO);
            buf.jumpIf(elseLabel);
            emitStatement(stmt.getThenBranch());
            buf.jumpTo(endLabel);
            buf.placeLabel(elseLabel);
            emitStatement(stmt.getElseBranch());
            buf.placeLabel(endLabel);
        } else {
            String endLabel = buf.newLabel();
            buf.emit(EvmOpcode.ISZERO);
            buf.jumpIf(endLabel);
            emitStatement(stmt.getThenBranch());
            buf.placeLabel(endLabel);
        }
    }

    private void emitWhileStmt(WhileStmt stmt) {
        String condLabel = buf.newLabel();
        String endLabel = buf.newLabel();
        breakLabels.push(endLabel);
        continueLabels.push(condLabel);

        buf.placeLabel(condLabel);
        emitExpression(stmt.getCondition());
        buf.emit(EvmOpcode.ISZERO);
        buf.jumpIf(endLabel);
        emitStatement(stmt.getBody());
        buf.jumpTo(condLabel);
        buf.placeLabel(endLabel);

        breakLabels.pop();
        continueLabels.pop();
    }

    private void emitReturnStmt(ReturnStmt stmt) {
        if (stmt.getValue() != null) {
            emitExpression(stmt.getValue());
            // Store result in memory at offset 0 and return 32 bytes
            buf.pushInt(0);
            buf.emit(EvmOpcode.MSTORE);
            buf.pushInt(32);
            buf.pushInt(0);
            buf.emit(EvmOpcode.RETURN);
        } else {
            buf.emit(EvmOpcode.STOP);
        }
    }

    // ── Expression emission ──────────────────────────────────────────────
    //  Every emitExpression leaves exactly one 256-bit value on the EVM stack.

    private void emitExpression(Expression expr) {
        if (expr instanceof LiteralExpr) {
            emitLiteral((LiteralExpr) expr);
        } else if (expr instanceof VariableExpr) {
            emitVariable((VariableExpr) expr);
        } else if (expr instanceof BinaryExpr) {
            emitBinary((BinaryExpr) expr);
        } else if (expr instanceof UnaryExpr) {
            emitUnary((UnaryExpr) expr);
        } else if (expr instanceof AssignmentExpr) {
            emitAssignment((AssignmentExpr) expr);
        } else if (expr instanceof CallExpr) {
            emitCall((CallExpr) expr);
        } else if (expr instanceof GetExpr) {
            emitGet((GetExpr) expr);
        } else if (expr instanceof SetExpr) {
            emitSet((SetExpr) expr);
        } else if (expr instanceof IndexExpr) {
            emitIndexAccess((IndexExpr) expr);
        } else if (expr instanceof IndexAssignExpr) {
            emitIndexAssign((IndexAssignExpr) expr);
        } else if (expr instanceof PrefixIncrementExpr) {
            emitPrefixIncrement((PrefixIncrementExpr) expr);
        } else if (expr instanceof PostfixIncrementExpr) {
            emitPostfixIncrement((PostfixIncrementExpr) expr);
        } else {
            // Unsupported expression → push 0
            buf.pushInt(0);
        }
    }

    private void emitLiteral(LiteralExpr expr) {
        Object value = expr.getValue();
        if (value == null) {
            buf.pushInt(0);
        } else if (value instanceof Integer) {
            buf.pushInt((Integer) value);
        } else if (value instanceof Long) {
            buf.pushInt(((Long) value).intValue());
        } else if (value instanceof Double) {
            // EVM doesn't have floating point — truncate
            buf.pushInt(((Double) value).intValue());
        } else if (value instanceof Boolean) {
            buf.pushInt((Boolean) value ? 1 : 0);
        } else if (value instanceof String) {
            // Strings: hash the string and push the hash (simplification)
            byte[] hash = FunctionSelector.keccak256(
                    ((String) value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            buf.push32(new java.math.BigInteger(1, hash));
        } else {
            buf.pushInt(0);
        }
    }

    private void emitVariable(VariableExpr expr) {
        String name = expr.getName().getLexeme();
        // Check storage first
        if (storageSlots.containsKey(name)) {
            buf.sloadSlot(storageSlots.get(name));
        } else if (locals.containsKey(name)) {
            buf.mloadAt(locals.get(name));
        } else if ("msg.sender".equals(name)) {
            buf.emit(EvmOpcode.CALLER);
        } else if ("msg.value".equals(name)) {
            buf.emit(EvmOpcode.CALLVALUE);
        } else if ("block.timestamp".equals(name)) {
            buf.emit(EvmOpcode.TIMESTAMP);
        } else {
            // Unknown variable — push 0
            buf.pushInt(0);
        }
    }

    private void emitBinary(BinaryExpr expr) {
        TokenType op = expr.getOperator().getType();

        // Short-circuit for logical AND/OR
        if (op == TokenType.AND) {
            emitExpression(expr.getLeft());
            String falseLabel = buf.newLabel();
            String endLabel = buf.newLabel();
            buf.emit(EvmOpcode.DUP1);
            buf.emit(EvmOpcode.ISZERO);
            buf.jumpIf(falseLabel);
            buf.emit(EvmOpcode.POP);
            emitExpression(expr.getRight());
            buf.jumpTo(endLabel);
            buf.placeLabel(falseLabel);
            // false result already on stack
            buf.placeLabel(endLabel);
            return;
        }
        if (op == TokenType.OR) {
            emitExpression(expr.getLeft());
            String trueLabel = buf.newLabel();
            String endLabel = buf.newLabel();
            buf.emit(EvmOpcode.DUP1);
            buf.jumpIf(trueLabel);
            buf.emit(EvmOpcode.POP);
            emitExpression(expr.getRight());
            buf.jumpTo(endLabel);
            buf.placeLabel(trueLabel);
            // true result already on stack
            buf.placeLabel(endLabel);
            return;
        }

        // Standard binary — evaluate both sides
        emitExpression(expr.getLeft());
        emitExpression(expr.getRight());

        switch (op) {
            case PLUS:     buf.emit(EvmOpcode.ADD);     break;
            case MINUS:    buf.emit(EvmOpcode.SUB);     break;
            case STAR:     buf.emit(EvmOpcode.MUL);     break;
            case SLASH:    buf.emit(EvmOpcode.SDIV);    break;
            case MOD:      buf.emit(EvmOpcode.SMOD);    break;
            case EQUALITY: buf.emit(EvmOpcode.EQ);      break;
            case NEQ:
                buf.emit(EvmOpcode.EQ);
                buf.emit(EvmOpcode.ISZERO);
                break;
            case LESS:     buf.emit(EvmOpcode.SLT);     break;
            case GREATER:  buf.emit(EvmOpcode.SGT);     break;
            case LEQ:
                buf.emit(EvmOpcode.SGT);
                buf.emit(EvmOpcode.ISZERO);
                break;
            case GEQ:
                buf.emit(EvmOpcode.SLT);
                buf.emit(EvmOpcode.ISZERO);
                break;
            default:
                // Fallback: pop one operand, leave the other
                buf.emit(EvmOpcode.POP);
                break;
        }
    }

    private void emitUnary(UnaryExpr expr) {
        emitExpression(expr.getRight());
        TokenType op = expr.getOperator().getType();
        switch (op) {
            case MINUS:
                // negate: 0 - value
                buf.pushInt(0);
                buf.emit(EvmOpcode.SUB);
                // Swap so result = 0 - expr (SUB pops a,b and pushes a-b; 
                // stack is [expr, 0] → SUB = 0 - expr ? No.
                // Actually stack after push 0 is [..., expr_val, 0].
                // SUB pops top two: a=0, b=expr_val → pushes a-b = 0 - expr_val.  Correct!
                break;
            case NOT:
                buf.emit(EvmOpcode.ISZERO);
                break;
            default:
                break;
        }
    }

    private void emitAssignment(AssignmentExpr expr) {
        emitExpression(expr.getValue());
        String name = expr.getName().getLexeme();
        if (storageSlots.containsKey(name)) {
            buf.emit(EvmOpcode.DUP1);        // keep value on stack as expression result
            buf.sstoreSlot(storageSlots.get(name));
        } else {
            int offset = locals.containsKey(name) ? locals.get(name) : allocLocal(name);
            buf.emit(EvmOpcode.DUP1);
            buf.mstoreAt(offset);
        }
    }

    private void emitCall(CallExpr expr) {
        Expression callee = expr.getCallee();

        // Pattern: require(condition) → revert if false
        if (callee instanceof VariableExpr
                && "require".equals(((VariableExpr) callee).getName().getLexeme())) {
            if (!expr.getArguments().isEmpty()) {
                emitExpression(expr.getArguments().get(0));
                String okLabel = buf.newLabel();
                buf.jumpIf(okLabel);
                buf.revert0();
                buf.placeLabel(okLabel);
            }
            buf.pushInt(1);  // require returns a truthy value for expression context
            return;
        }

        // Pattern: emit EventName(args...) → LOG instruction
        if (callee instanceof VariableExpr) {
            String fnName = ((VariableExpr) callee).getName().getLexeme();
            if (isEventFunction(fnName)) {
                emitEvent(fnName, expr.getArguments());
                return;
            }
        }

        // Pattern: msg.sender / msg.value access via GetExpr
        if (callee instanceof GetExpr) {
            GetExpr getExpr = (GetExpr) callee;
            Expression obj = getExpr.getObject();
            if (obj instanceof VariableExpr) {
                String objName = ((VariableExpr) obj).getName().getLexeme();
                String member = getExpr.getName().getLexeme();
                // this.transfer(to, amount) → external CALL
                if ("this".equals(objName) && "transfer".equals(member)) {
                    emitExternalTransfer(expr.getArguments());
                    return;
                }
            }
        }

        // Generic internal call — not directly supported in EVM (functions 
        // are inlined or dispatched). Push 0 as fallback.
        buf.pushInt(0);
    }

    private void emitGet(GetExpr expr) {
        Expression obj = expr.getObject();
        String member = expr.getName().getLexeme();

        // msg.sender
        if (obj instanceof VariableExpr
                && "msg".equals(((VariableExpr) obj).getName().getLexeme())) {
            if ("sender".equals(member)) {
                buf.emit(EvmOpcode.CALLER);
                return;
            } else if ("value".equals(member)) {
                buf.emit(EvmOpcode.CALLVALUE);
                return;
            }
        }

        // block.timestamp, block.number
        if (obj instanceof VariableExpr
                && "block".equals(((VariableExpr) obj).getName().getLexeme())) {
            if ("timestamp".equals(member)) {
                buf.emit(EvmOpcode.TIMESTAMP);
                return;
            } else if ("number".equals(member)) {
                buf.emit(EvmOpcode.NUMBER);
                return;
            }
        }

        // this.storageField → SLOAD
        if (obj instanceof ThisExpr && storageSlots.containsKey(member)) {
            buf.sloadSlot(storageSlots.get(member));
            return;
        }

        // Fallback
        buf.pushInt(0);
    }

    private void emitSet(SetExpr expr) {
        // this.storageField = value → SSTORE
        String member = expr.getName().getLexeme();
        if (expr.getObject() instanceof ThisExpr && storageSlots.containsKey(member)) {
            emitExpression(expr.getValue());
            buf.emit(EvmOpcode.DUP1);
            buf.sstoreSlot(storageSlots.get(member));
            return;
        }
        // Fallback
        emitExpression(expr.getValue());
    }

    private void emitIndexAccess(IndexExpr expr) {
        // For mappings stored in storage: compute keccak256(key . slot)
        // For now, push 0 as placeholder
        buf.pushInt(0);
    }

    private void emitIndexAssign(IndexAssignExpr expr) {
        // For mappings: SSTORE to computed slot
        emitExpression(expr.getValue());
    }

    private void emitPrefixIncrement(PrefixIncrementExpr expr) {
        Expression target = expr.getTarget();
        if (target instanceof VariableExpr) {
            String name = ((VariableExpr) target).getName().getLexeme();
            if (locals.containsKey(name)) {
                int offset = locals.get(name);
                buf.mloadAt(offset);
                buf.pushInt(1);
                if (expr.isIncrement()) {
                    buf.emit(EvmOpcode.ADD);
                } else {
                    buf.emit(EvmOpcode.SUB);
                }
                buf.emit(EvmOpcode.DUP1);
                buf.mstoreAt(offset);
            } else {
                buf.pushInt(0);
            }
        } else {
            buf.pushInt(0);
        }
    }

    private void emitPostfixIncrement(PostfixIncrementExpr expr) {
        Expression target = expr.getTarget();
        if (target instanceof VariableExpr) {
            String name = ((VariableExpr) target).getName().getLexeme();
            if (locals.containsKey(name)) {
                int offset = locals.get(name);
                buf.mloadAt(offset);       // push old value
                buf.emit(EvmOpcode.DUP1);  // dup for return
                buf.pushInt(1);
                if (expr.isIncrement()) {
                    buf.emit(EvmOpcode.ADD);
                } else {
                    buf.emit(EvmOpcode.SUB);
                }
                buf.mstoreAt(offset);      // store new value
                // Old value is left on stack
            } else {
                buf.pushInt(0);
            }
        } else {
            buf.pushInt(0);
        }
    }

    // ── Event emission ───────────────────────────────────────────────────

    private void emitEvent(String eventName, List<Expression> args) {
        // Compute event topic hash
        StringBuilder sig = new StringBuilder(eventName).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sig.append(',');
            sig.append("uint256");  // Simplified: assume uint256 params
        }
        sig.append(')');
        byte[] topic = FunctionSelector.keccak256(
                sig.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Store first non-indexed arg in memory for log data
        if (!args.isEmpty()) {
            emitExpression(args.get(0));
            buf.pushInt(0);
            buf.emit(EvmOpcode.MSTORE);
        }

        // Push topic
        buf.push32(new java.math.BigInteger(1, topic));

        // LOG1(offset=0, size=32, topic)
        buf.pushInt(32);   // size
        buf.pushInt(0);    // offset
        buf.emit(EvmOpcode.LOG1);

        buf.pushInt(1);  // Expression result
    }

    // ── External transfer ────────────────────────────────────────────────

    private void emitExternalTransfer(List<Expression> args) {
        // transfer(to, amount)
        if (args.size() >= 2) {
            // amount → value
            emitExpression(args.get(1));
            // to → address
            emitExpression(args.get(0));

            // CALL(gas, to, value, inOffset, inSize, outOffset, outSize)
            buf.pushInt(0);    // outSize
            buf.pushInt(0);    // outOffset
            buf.pushInt(0);    // inSize
            buf.pushInt(0);    // inOffset
            // value is already on stack (3rd from top)
            // to is already on stack (2nd from top)
            // Need: gas, to, value, 0, 0, 0, 0
            // Gas: push remaining gas
            buf.emit(EvmOpcode.GAS);
            // Stack: [..., amount, to, 0, 0, 0, 0, gas]
            // Need to rearrange...  Use a simpler pattern for now:
            buf.emit(EvmOpcode.CALL);
            // CALL returns success (0 or 1) on stack
        } else {
            buf.pushInt(0);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private FunctionDecl findConstructor() {
        for (FunctionDecl fn : contract.getFunctions()) {
            if (fn.isContractConstructor()) return fn;
        }
        return null;
    }

    private boolean isEventFunction(String name) {
        for (FunctionDecl fn : contract.getFunctions()) {
            if (fn.getName().equals(name)
                    && fn.hasContractAnnotation(ContractAnnotation.EVENT)) {
                return true;
            }
        }
        return false;
    }

    private int allocLocal(String name) {
        if (locals.containsKey(name)) return locals.get(name);
        int offset = nextLocalOffset;
        locals.put(name, offset);
        nextLocalOffset += SLOT_SIZE;
        return offset;
    }

    private void resetLocals() {
        locals.clear();
        nextLocalOffset = MEMORY_BASE;
    }

    // ── Compilation result ───────────────────────────────────────────────

    /**
     * Immutable result of compiling a contract.
     */
    public static final class CompilationResult {
        private final byte[] creationBytecode;
        private final byte[] runtimeBytecode;
        private final String abiJson;
        private final ContractLayout storageLayout;
        private final List<Map<String, Object>> abi;

        CompilationResult(byte[] creationBytecode, byte[] runtimeBytecode,
                          String abiJson, ContractLayout storageLayout,
                          List<Map<String, Object>> abi) {
            this.creationBytecode = creationBytecode;
            this.runtimeBytecode = runtimeBytecode;
            this.abiJson = abiJson;
            this.storageLayout = storageLayout;
            this.abi = abi;
        }

        public byte[] getCreationBytecode() { return creationBytecode; }
        public byte[] getRuntimeBytecode() { return runtimeBytecode; }
        public String getAbiJson() { return abiJson; }
        public ContractLayout getStorageLayout() { return storageLayout; }
        public List<Map<String, Object>> getAbi() { return abi; }

        /**
         * Return the creation bytecode as a hex string.
         */
        public String getCreationBytecodeHex() {
            return FunctionSelector.bytesToHex(creationBytecode);
        }

        /**
         * Return the runtime bytecode as a hex string.
         */
        public String getRuntimeBytecodeHex() {
            return FunctionSelector.bytesToHex(runtimeBytecode);
        }
    }
}
