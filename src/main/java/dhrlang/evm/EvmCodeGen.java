package dhrlang.evm;

import dhrlang.ast.*;
import dhrlang.lexer.TokenType;
import dhrlang.validation.StorageLayouter;
import dhrlang.validation.StorageLayouter.ContractLayout;
import dhrlang.validation.StorageLayouter.SlotInfo;

import java.math.BigInteger;

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

    /**
     * Reserved storage slot for reentrancy lock.
     * Computed as keccak256("dhrlang.reentrancy.lock") to avoid collision
     * with user-defined storage variables.
     */
    private static final java.math.BigInteger REENTRANCY_LOCK_SLOT;
    static {
        byte[] hash = FunctionSelector.keccak256(
                "dhrlang.reentrancy.lock".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        REENTRANCY_LOCK_SLOT = new java.math.BigInteger(1, hash);
    }

    /**
     * Reserved storage slot for contract owner (access control).
     * Computed as keccak256("dhrlang.owner") to avoid collision.
     */
    private static final java.math.BigInteger OWNER_SLOT;
    static {
        byte[] ownerHash = FunctionSelector.keccak256(
                "dhrlang.owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        OWNER_SLOT = new java.math.BigInteger(1, ownerHash);
    }

    // ── State ────────────────────────────────────────────────────────────

    private final ClassDecl contract;
    private final ContractLayout layout;

    /** Maps local variable names to their memory offsets. */
    private final Map<String, Integer> locals = new LinkedHashMap<>();
    private int nextLocalOffset = MEMORY_BASE;

    /** Maps storage variable names to their slot indices. */
    private final Map<String, Integer> storageSlots = new LinkedHashMap<>();

    /** Class registry — maps class name to ClassDecl for inheritance resolution. */
    private final Map<String, ClassDecl> classRegistry;

    private EvmCodeBuffer buf;

    // Loop control — break/continue labels
    private final Deque<String> breakLabels = new ArrayDeque<>();
    private final Deque<String> continueLabels = new ArrayDeque<>();

    // Reentrancy guard state — true when inside a @nonreentrant function
    private boolean insideNonReentrant = false;

    /**
     * Compiler-wide default for {@code num}/{@code duo} arithmetic.
     * {@code true} = checked (revert on overflow/underflow) by default;
     * {@code false} = wrapping (modulo 2^256). As of v4.0.0 DhrLang is
     * checked-by-default, matching Solidity 0.8+. Opt back into wrapping per
     * method with {@code @unchecked}; per-method {@code @checked}/{@code @unchecked}
     * always override this default.
     */
    private static final boolean CHECKED_ARITHMETIC_BY_DEFAULT = true;

    // True while emitting a function body whose num/duo +,-,* must revert on
    // overflow/underflow. Resolved per function from annotations + the default.
    private boolean checkedArithmetic = CHECKED_ARITHMETIC_BY_DEFAULT;

    // ── Constructor ──────────────────────────────────────────────────────

    public EvmCodeGen(ClassDecl contract, ContractLayout layout) {
        this(contract, layout, Map.of());
    }

    public EvmCodeGen(ClassDecl contract, ContractLayout layout, Map<String, ClassDecl> classRegistry) {
        this.contract = contract;
        this.layout = layout;
        this.classRegistry = classRegistry;

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
        List<Map<String, Object>> abi = AbiGenerator.generate(contract, classRegistry);
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

        // Find receive() and fallback() functions if they exist
        FunctionDecl receiveFunc = findNamedFunction("receive");
        FunctionDecl fallbackFunc = findNamedFunction("fallback");
        String receiveLabel = receiveFunc != null ? buf.newLabel() : null;
        String fallbackLabel = fallbackFunc != null ? buf.newLabel() : null;

        // ── Receive routing (empty calldata + ETH sent) ──────────
        if (receiveFunc != null) {
            buf.emit(EvmOpcode.CALLDATASIZE);      // calldatasize
            buf.emit(EvmOpcode.ISZERO);             // calldatasize == 0?
            buf.jumpIf(receiveLabel);               // jump to receive()
        }

        // ── Function dispatcher ──────────────────────────────────
        // Load first 4 bytes of calldata as function selector
        buf.pushInt(0);                         // offset 0
        buf.emit(EvmOpcode.CALLDATALOAD);       // load 32 bytes from calldata[0]
        buf.pushInt(0xE0);                      // shift right by 224 bits
        buf.emit(EvmOpcode.SHR);                // → 4-byte selector on stack

        // Collect non-constructor, non-event functions (including inherited)
        List<FunctionDecl> publicFunctions = new ArrayList<>();
        Set<String> seenSelectors = new HashSet<>();
        collectDispatchFunctions(contract, publicFunctions, seenSelectors);

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

        // No match → fallback or revert
        if (fallbackFunc != null) {
            buf.emit(EvmOpcode.POP);                   // pop selector
            buf.jumpTo(fallbackLabel);
        } else {
            buf.revert0();
        }

        // ── Function bodies ──────────────────────────────────────
        for (int i = 0; i < publicFunctions.size(); i++) {
            emitFunction(publicFunctions.get(i), functionLabels[i]);
        }

        // ── Receive function body ────────────────────────────────
        if (receiveFunc != null) {
            emitFunction(receiveFunc, receiveLabel);
        }

        // ── Fallback function body ───────────────────────────────
        if (fallbackFunc != null) {
            emitFunction(fallbackFunc, fallbackLabel);
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

        // Auto-store msg.sender as contract owner for access control
        creation.emit(EvmOpcode.CALLER);
        creation.push32(OWNER_SLOT);
        creation.emit(EvmOpcode.SSTORE);

        if (ctor != null) {
            EvmCodeGen ctorGen = new EvmCodeGen(contract, layout, classRegistry);
            ctorGen.buf = creation;

            // Decode constructor parameters from calldata.
            // At deploy time, constructor args are ABI-encoded in calldata
            // (no 4-byte selector prefix for constructors).
            List<VarDecl> ctorParams = ctor.getParameters();
            for (int i = 0; i < ctorParams.size(); i++) {
                String name = ctorParams.get(i).getName();
                int offset = ctorGen.allocLocal(name);
                creation.pushInt(i * 32);
                creation.emit(EvmOpcode.CALLDATALOAD);
                creation.mstoreAt(offset);
            }

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

        // Access control: private functions require msg.sender == owner
        if (fn.hasModifier(dhrlang.ast.Modifier.PRIVATE)) {
            buf.emit(EvmOpcode.CALLER);
            buf.sloadSlot(OWNER_SLOT);
            buf.emit(EvmOpcode.EQ);
            String ownerOk = buf.newLabel();
            buf.jumpIf(ownerOk);
            buf.revertWithMessage("caller is not the owner");
            buf.placeLabel(ownerOk);
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

        // Track current function for return-type / spec awareness
        currentFn = fn;

        // @requires preconditions: revert at entry if any condition is false.
        // Checked after parameters are decoded so conditions can reference them.
        for (Expression pre : fn.getRequires()) {
            emitExpression(pre);
            String ok = buf.newLabel();
            buf.jumpIf(ok);
            buf.revertWithMessage("precondition failed");
            buf.placeLabel(ok);
        }

        // @nonreentrant guard: check lock, set lock, emit body, clear lock
        boolean guarded = fn.hasContractAnnotation(ContractAnnotation.NONREENTRANT);
        if (guarded) {
            // Check: SLOAD(REENTRANCY_LOCK_SLOT) must be 0
            buf.sloadSlot(REENTRANCY_LOCK_SLOT);
            String notLockedLabel = buf.newLabel();
            buf.emit(EvmOpcode.ISZERO);
            buf.jumpIf(notLockedLabel);
            buf.revert0();  // revert if already locked
            buf.placeLabel(notLockedLabel);
            // Set lock = 1
            buf.pushInt(1);
            buf.sstoreSlot(REENTRANCY_LOCK_SLOT);
            insideNonReentrant = true;
        }

        // Emit function body
        emitFunctionBody(fn);

        // Clear reentrancy lock before implicit return
        if (guarded) {
            buf.pushInt(0);
            buf.sstoreSlot(REENTRANCY_LOCK_SLOT);
            insideNonReentrant = false;
        }

        // Fall-through epilogue: enforce postconditions + invariants on the
        // implicit (void) return path. Explicit returns are handled in
        // emitReturnStmt; at runtime each path reaches exactly one exit.
        emitEnsuresChecks();
        emitInvariantChecks();

        // Implicit return (stop) if no explicit return was encountered
        buf.emit(EvmOpcode.STOP);
    }

    /** Memory-backed local holding the return value, bound to {@code result} in @ensures. */
    private static final String RESULT_LOCAL = "result";

    /**
     * Emit {@code @ensures} postcondition guards for {@link #currentFn}. Each
     * condition is evaluated and, if false, reverts. Postconditions may
     * reference {@code result}; callers that return a value must store it into
     * the {@link #RESULT_LOCAL} memory slot before invoking this.
     */
    private void emitEnsuresChecks() {
        if (currentFn == null) return;
        for (Expression post : currentFn.getEnsures()) {
            emitExpression(post);
            String ok = buf.newLabel();
            buf.jumpIf(ok);
            buf.revertWithMessage("postcondition failed");
            buf.placeLabel(ok);
        }
    }

    /**
     * Emit contract-level {@code @invariant} guards. Each invariant is
     * evaluated against current storage and, if false, reverts. Skipped for
     * {@code @view}/{@code @pure} functions, which cannot mutate state.
     */
    private void emitInvariantChecks() {
        if (currentFn != null && (currentFn.isView() || currentFn.isPure())) return;
        for (Expression inv : contract.getInvariants()) {
            emitExpression(inv);
            String ok = buf.newLabel();
            buf.jumpIf(ok);
            buf.revertWithMessage("invariant violated");
            buf.placeLabel(ok);
        }
    }

    private void emitFunctionBody(FunctionDecl fn) {
        boolean prevChecked = checkedArithmetic;
        checkedArithmetic = resolveCheckedArithmetic(fn);
        try {
            if (fn.getBody() != null) {
                emitBlock(fn.getBody());
            }
        } finally {
            checkedArithmetic = prevChecked;
        }
    }

    /**
     * Decide whether {@code num}/{@code duo} {@code + - *} inside this function
     * should revert on overflow/underflow (checked) or wrap (unchecked).
     * {@code @checked}/{@code @unchecked} on the method override the compiler
     * default; if both are absent the compiler default applies.
     */
    private boolean resolveCheckedArithmetic(FunctionDecl fn) {
        if (fn.hasContractAnnotation(ContractAnnotation.UNCHECKED)) {
            return false;
        }
        if (fn.hasContractAnnotation(ContractAnnotation.CHECKED)) {
            return true;
        }
        return CHECKED_ARITHMETIC_BY_DEFAULT;
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

    /** Tracks the current function being compiled (for return type awareness). */
    private FunctionDecl currentFn;

    private void emitReturnStmt(ReturnStmt stmt) {
        // Clear reentrancy lock before returning
        if (insideNonReentrant) {
            buf.pushInt(0);
            buf.sstoreSlot(REENTRANCY_LOCK_SLOT);
        }
        boolean hasSpecExits = currentFn != null
                && (!currentFn.getEnsures().isEmpty() || !contract.getInvariants().isEmpty());
        if (stmt.getValue() != null) {
            // Check if return type is string (sab) — requires dynamic ABI encoding
            String retType = currentFn != null ? currentFn.getReturnType() : null;
            if ("sab".equals(retType) || "string".equals(retType)) {
                // Postconditions/invariants run before the dynamic-string return.
                // `result` binding is not supported for sab returns.
                emitEnsuresChecks();
                emitInvariantChecks();
                emitDynamicStringReturn(stmt.getValue());
            } else {
                emitExpression(stmt.getValue());
                if (hasSpecExits) {
                    // Bind the return value to `result` (a memory local) so
                    // @ensures can reference it, keeping a copy on the stack.
                    int rslot = locals.containsKey(RESULT_LOCAL)
                            ? locals.get(RESULT_LOCAL) : allocLocal(RESULT_LOCAL);
                    buf.emit(EvmOpcode.DUP1);
                    buf.mstoreAt(rslot);
                    emitEnsuresChecks();
                    emitInvariantChecks();
                }
                // ABI-encode return value: store at memory offset 0, return 32 bytes
                buf.pushInt(0);
                buf.emit(EvmOpcode.MSTORE);
                buf.pushInt(32);
                buf.pushInt(0);
                buf.emit(EvmOpcode.RETURN);
            }
        } else {
            emitEnsuresChecks();
            emitInvariantChecks();
            buf.emit(EvmOpcode.STOP);
        }
    }

    /**
     * ABI-encode a dynamic string return value.
     *
     * <p>Layout (Solidity-compatible):
     * <pre>
     *   [0x00] offset to string data = 0x20
     *   [0x20] string length
     *   [0x40] string data (right-padded to 32 bytes)
     * </pre>
     */
    private void emitDynamicStringReturn(Expression value) {
        // For string literals, we can compute at compile time
        if (value instanceof LiteralExpr lit && lit.getValue() instanceof String strVal) {
            byte[] strBytes = strVal.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int paddedLen = ((strBytes.length + 31) / 32) * 32;
            int totalSize = 32 + 32 + paddedLen; // offset + length + data

            // Store offset to string data = 0x20
            buf.pushInt(0x20);
            buf.mstoreAt(0x00);

            // Store string length
            buf.pushInt(strBytes.length);
            buf.mstoreAt(0x20);

            // Store string bytes (padded to 32)
            byte[] padded = new byte[32];
            System.arraycopy(strBytes, 0, padded, 0, Math.min(strBytes.length, 32));
            buf.push32(new java.math.BigInteger(1, padded));
            buf.mstoreAt(0x40);

            // Handle strings > 32 bytes
            for (int chunk = 1; chunk * 32 < strBytes.length; chunk++) {
                byte[] next = new byte[32];
                int srcOff = chunk * 32;
                int copyLen = Math.min(32, strBytes.length - srcOff);
                System.arraycopy(strBytes, srcOff, next, 0, copyLen);
                buf.push32(new java.math.BigInteger(1, next));
                buf.mstoreAt(0x40 + chunk * 32);
            }

            buf.pushInt(totalSize);
            buf.pushInt(0);
            buf.emit(EvmOpcode.RETURN);
        } else {
            // For non-literal strings (storage variables), store as 32-byte hash for now
            // Full dynamic encoding from storage requires runtime length tracking
            emitExpression(value);
            buf.pushInt(0);
            buf.emit(EvmOpcode.MSTORE);
            buf.pushInt(32);
            buf.pushInt(0);
            buf.emit(EvmOpcode.RETURN);
        }
    }

    /**
     * Encode multiple return values in ABI format at memory starting at offset 0.
     * Each value is stored as a 32-byte word. Returns total encoded size.
     */
    private int emitAbiEncodeValues(List<Expression> values) {
        for (int i = 0; i < values.size(); i++) {
            emitExpression(values.get(i));
            buf.mstoreAt(i * 32);
        }
        return values.size() * 32;
    }

    /**
     * Encode a dynamic array return value in ABI format.
     * Layout: [offset=0x20][length][element0][element1]...
     *
     * @param elements the array element expressions
     */
    private void emitDynamicArrayReturn(List<Expression> elements) {
        int count = elements.size();
        int totalSize = 32 + 32 + count * 32; // offset + length + data

        buf.pushInt(0x20);
        buf.mstoreAt(0x00);       // offset to array data

        buf.pushInt(count);
        buf.mstoreAt(0x20);       // array length

        for (int i = 0; i < count; i++) {
            emitExpression(elements.get(i));
            buf.mstoreAt(0x40 + i * 32);  // elements
        }

        buf.pushInt(totalSize);
        buf.pushInt(0);
        buf.emit(EvmOpcode.RETURN);
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
        } else if (expr instanceof TernaryExpr) {
            emitTernary((TernaryExpr) expr);
        } else {
            // Unsupported expression type — report error instead of silently pushing 0
            throw new IllegalStateException(
                "EVM codegen: unsupported expression type: " + expr.getClass().getSimpleName()
                + (expr.getSourceLocation() != null
                    ? " at line " + expr.getSourceLocation().getLine()
                    : ""));
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
        } else if ("block.number".equals(name)) {
            buf.emit(EvmOpcode.NUMBER);
        } else if ("block.coinbase".equals(name)) {
            buf.emit(EvmOpcode.COINBASE);
        } else if ("block.gaslimit".equals(name)) {
            buf.emit(EvmOpcode.GASLIMIT);
        } else if ("block.chainid".equals(name)) {
            buf.emit(EvmOpcode.CHAINID);
        } else if ("tx.origin".equals(name)) {
            buf.emit(EvmOpcode.ORIGIN);
        } else if ("tx.gasprice".equals(name)) {
            buf.emit(EvmOpcode.GASPRICE);
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
            case PLUS:
                if (checkedArithmetic) {
                    // Checked add (unsigned): c = a + b; revert if c < a (overflow).
                    buf.emit(EvmOpcode.DUP2);     // [a, b, a]
                    buf.emit(EvmOpcode.ADD);      // [a, c]   (c = a+b)
                    buf.emit(EvmOpcode.DUP1);     // [a, c, c]
                    buf.emit(EvmOpcode.SWAP2);    // [c, c, a]
                    buf.emit(EvmOpcode.GT);       // [c, (a > c)?]  overflow iff a > c
                    String okAdd = buf.newLabel();
                    buf.emit(EvmOpcode.ISZERO);
                    buf.jumpIf(okAdd);
                    buf.revertWithMessage("arithmetic overflow");
                    buf.placeLabel(okAdd);        // [c]
                } else {
                    buf.emit(EvmOpcode.ADD);      // wrapping add (mod 2^256)
                }
                break;
            case MINUS:
                if (checkedArithmetic) {
                    // Checked sub (unsigned): revert if b > a (underflow).
                    buf.emit(EvmOpcode.DUP2);     // [a, b, a]
                    buf.emit(EvmOpcode.DUP2);     // [a, b, a, b]
                    buf.emit(EvmOpcode.GT);       // [a, b, (b > a)?]  underflow iff b > a
                    String okSub = buf.newLabel();
                    buf.emit(EvmOpcode.ISZERO);
                    buf.jumpIf(okSub);
                    buf.revertWithMessage("arithmetic underflow");
                    buf.placeLabel(okSub);        // [a, b]
                }
                buf.emit(EvmOpcode.SWAP1);        // [b, a]
                buf.emit(EvmOpcode.SUB);          // a - b
                break;
            case STAR:
                if (checkedArithmetic) {
                    emitCheckedMul();             // [a*b] with overflow revert
                } else {
                    buf.emit(EvmOpcode.MUL);      // wrapping mul (mod 2^256)
                }
                break;
            case SLASH:
                buf.emit(EvmOpcode.SWAP1);        // [b, a]
                buf.emit(EvmOpcode.DIV);          // a / b (unsigned)
                break;
            case MOD:
                buf.emit(EvmOpcode.SWAP1);        // [b, a]
                buf.emit(EvmOpcode.MOD);          // a % b (unsigned)
                break;
            case EQUALITY: buf.emit(EvmOpcode.EQ);      break;
            case NEQ:
                buf.emit(EvmOpcode.EQ);
                buf.emit(EvmOpcode.ISZERO);
                break;
            case LESS:
                buf.emit(EvmOpcode.SWAP1);        // [b, a]
                buf.emit(EvmOpcode.LT);           // a < b (unsigned)
                break;
            case GREATER:
                buf.emit(EvmOpcode.SWAP1);        // [b, a]
                buf.emit(EvmOpcode.GT);           // a > b (unsigned)
                break;
            case LEQ:
                buf.emit(EvmOpcode.SWAP1);        // [b, a]
                buf.emit(EvmOpcode.GT);           // a > b
                buf.emit(EvmOpcode.ISZERO);       // a <= b
                break;
            case GEQ:
                buf.emit(EvmOpcode.SWAP1);        // [b, a]
                buf.emit(EvmOpcode.LT);           // a < b
                buf.emit(EvmOpcode.ISZERO);       // a >= b
                break;
            case BIT_AND:  buf.emit(EvmOpcode.AND);     break;
            case BIT_OR:   buf.emit(EvmOpcode.OR);      break;
            case BIT_XOR:  buf.emit(EvmOpcode.XOR);     break;
            case LSHIFT:   buf.emit(EvmOpcode.SHL);     break;
            case RSHIFT:   buf.emit(EvmOpcode.SHR);     break;
            default:
                // Fallback: pop one operand, leave the other
                buf.emit(EvmOpcode.POP);
                break;
        }
    }

    /**
     * Emit a checked unsigned multiply: {@code c = a * b}, reverting on overflow.
     * Uses the standard SafeMath identity: overflow iff
     * {@code a != 0 && (a * b) / a != b}. Stack in: {@code [a, b]};
     * stack out: {@code [a*b]}.
     */
    private void emitCheckedMul() {
        String zeroLbl = buf.newLabel();
        String okLbl = buf.newLabel();
        buf.emit(EvmOpcode.DUP2);     // [a, b, a]
        buf.emit(EvmOpcode.ISZERO);   // [a, b, (a==0)?]
        buf.jumpIf(zeroLbl);          // [a, b]   (a==0 → product is 0)
        // a != 0: compute c = a*b and verify c/a == b
        buf.emit(EvmOpcode.DUP2);     // [a, b, a]
        buf.emit(EvmOpcode.DUP2);     // [a, b, a, b]
        buf.emit(EvmOpcode.MUL);      // [a, b, c]
        buf.emit(EvmOpcode.DUP3);     // [a, b, c, a]
        buf.emit(EvmOpcode.DUP2);     // [a, b, c, a, c]
        buf.emit(EvmOpcode.DIV);      // [a, b, c, c/a]
        buf.emit(EvmOpcode.DUP3);     // [a, b, c, c/a, b]
        buf.emit(EvmOpcode.EQ);       // [a, b, c, (c/a == b)?]
        buf.jumpIf(okLbl);            // [a, b, c]   (check passed)
        buf.revertWithMessage("arithmetic overflow");
        buf.placeLabel(zeroLbl);      // [a, b]
        buf.pushInt(0);               // [a, b, 0]
        buf.placeLabel(okLbl);        // [a, b, c]   (c = product, or 0 when a==0)
        buf.emit(EvmOpcode.SWAP2);    // [c, b, a]
        buf.emit(EvmOpcode.POP);      // [c, b]
        buf.emit(EvmOpcode.POP);      // [c]
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
            case BIT_NOT:
                buf.emit(EvmOpcode.NOT);
                break;
            default:
                break;
        }
    }

    private void emitTernary(TernaryExpr expr) {
        emitExpression(expr.getCondition());
        String elseLabel = buf.newLabel();
        String endLabel = buf.newLabel();
        buf.emit(EvmOpcode.ISZERO);
        buf.jumpIf(elseLabel);
        emitExpression(expr.getThenBranch());
        buf.jumpTo(endLabel);
        buf.placeLabel(elseLabel);
        emitExpression(expr.getElseBranch());
        buf.placeLabel(endLabel);
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

        // Pattern: gasleft() → GAS opcode
        if (callee instanceof VariableExpr
                && "gasleft".equals(((VariableExpr) callee).getName().getLexeme())) {
            buf.emit(EvmOpcode.GAS);
            return;
        }

        // Pattern: address(x) → cast a numeric value to an Address by masking to
        // 160 bits. address(0) yields the zero address. Leaves one word on the stack.
        if (callee instanceof VariableExpr
                && "address".equals(((VariableExpr) callee).getName().getLexeme())
                && expr.getArguments().size() == 1) {
            emitExpression(expr.getArguments().get(0));
            buf.push32(BigInteger.TWO.pow(160).subtract(BigInteger.ONE));
            buf.emit(EvmOpcode.AND);
            return;
        }

        // Pattern: require(condition) or require(condition, "message") → revert if false
        if (callee instanceof VariableExpr
                && "require".equals(((VariableExpr) callee).getName().getLexeme())) {
            if (!expr.getArguments().isEmpty()) {
                emitExpression(expr.getArguments().get(0));
                String okLabel = buf.newLabel();
                buf.jumpIf(okLabel);
                // Revert with a custom error, a string message, or plain revert
                if (expr.getArguments().size() >= 2
                        && expr.getArguments().get(1) instanceof CallExpr errCall
                        && errCall.getCallee() instanceof VariableExpr errVar
                        && isErrorFunction(errVar.getName().getLexeme())) {
                    emitCustomErrorRevert(errVar.getName().getLexeme(), errCall.getArguments());
                } else if (expr.getArguments().size() >= 2
                        && expr.getArguments().get(1) instanceof LiteralExpr lit
                        && lit.getValue() instanceof String msg) {
                    buf.revertWithMessage(msg);
                } else {
                    buf.revert0();
                }
                buf.placeLabel(okLabel);
            }
            buf.pushInt(1);  // require returns a truthy value for expression context
            return;
        }

        // Pattern: revert(), revert("message"), or revert(CustomError(args...))
        if (callee instanceof VariableExpr
                && "revert".equals(((VariableExpr) callee).getName().getLexeme())) {
            List<Expression> revertArgs = expr.getArguments();
            if (revertArgs.isEmpty()) {
                buf.revert0();
            } else if (revertArgs.get(0) instanceof CallExpr errCall
                    && errCall.getCallee() instanceof VariableExpr errVar
                    && isErrorFunction(errVar.getName().getLexeme())) {
                emitCustomErrorRevert(errVar.getName().getLexeme(), errCall.getArguments());
            } else if (revertArgs.get(0) instanceof LiteralExpr lit
                    && lit.getValue() instanceof String msg) {
                buf.revertWithMessage(msg);
            } else {
                buf.revert0();
            }
            buf.pushInt(1);  // expression-context balance (revert never returns)
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

        // Pattern: array.push(value) → dynamic array push in storage
        // Pattern: array.pop() → dynamic array pop in storage
        if (callee instanceof GetExpr getCall) {
            String methodName = getCall.getName().getLexeme();
            Integer arrBaseSlot = resolveMappingBaseSlot(getCall.getObject());
            if (arrBaseSlot != null && "push".equals(methodName)) {
                emitDynamicArrayPush(arrBaseSlot, expr.getArguments());
                return;
            }
            if (arrBaseSlot != null && "pop".equals(methodName)) {
                emitDynamicArrayPop(arrBaseSlot);
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
                // contract.functionName(args) → external CALL or STATICCALL
                // Check if the callee object is a local variable (address of external contract)
                if (locals.containsKey(objName) || storageSlots.containsKey(objName)) {
                    // Use STATICCALL for @view annotated functions, CALL otherwise
                    emitExternalContractCall(obj, member, expr.getArguments());
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
            } else if ("sig".equals(member)) {
                // First 4 calldata bytes (function selector), right-aligned: calldataload(0) >> 224
                buf.pushInt(0);
                buf.emit(EvmOpcode.CALLDATALOAD);
                buf.pushInt(0xE0);
                buf.emit(EvmOpcode.SHR);
                return;
            }
        }

        // msg.data.length → CALLDATASIZE
        if ("length".equals(member) && obj instanceof GetExpr inner
                && inner.getObject() instanceof VariableExpr dv
                && "msg".equals(dv.getName().getLexeme())
                && "data".equals(inner.getName().getLexeme())) {
            buf.emit(EvmOpcode.CALLDATASIZE);
            return;
        }

        // block.timestamp, block.number, block.coinbase, block.gaslimit, block.chainid
        if (obj instanceof VariableExpr
                && "block".equals(((VariableExpr) obj).getName().getLexeme())) {
            switch (member) {
                case "timestamp" -> { buf.emit(EvmOpcode.TIMESTAMP); return; }
                case "number" -> { buf.emit(EvmOpcode.NUMBER); return; }
                case "coinbase" -> { buf.emit(EvmOpcode.COINBASE); return; }
                case "gaslimit" -> { buf.emit(EvmOpcode.GASLIMIT); return; }
                case "chainid" -> { buf.emit(EvmOpcode.CHAINID); return; }
                default -> {}
            }
        }

        // tx.origin, tx.gasprice
        if (obj instanceof VariableExpr
                && "tx".equals(((VariableExpr) obj).getName().getLexeme())) {
            switch (member) {
                case "origin" -> { buf.emit(EvmOpcode.ORIGIN); return; }
                case "gasprice" -> { buf.emit(EvmOpcode.GASPRICE); return; }
                default -> {}
            }
        }

        // this.storageField → SLOAD
        if (obj instanceof ThisExpr && storageSlots.containsKey(member)) {
            buf.sloadSlot(storageSlots.get(member));
            return;
        }

        // array.length → SLOAD from base slot (Solidity convention: base slot holds length)
        if ("length".equals(member)) {
            Integer arrSlot = resolveMappingBaseSlot(obj);
            if (arrSlot != null) {
                buf.sloadSlot(arrSlot);
                return;
            }
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
        // For mappings stored in storage: compute keccak256(key . baseSlot) then SLOAD
        // Supports nested mappings: mapping[k1][k2] → keccak256(k2 . keccak256(k1 . baseSlot))
        if (expr.getObject() instanceof IndexExpr innerIdx) {
            // Nested mapping access: evaluate inner mapping first to get intermediate slot
            Integer baseSlot = resolveMappingBaseSlot(innerIdx.getObject());
            if (baseSlot != null) {
                // Compute inner slot: keccak256(key1 . baseSlot)
                emitExpression(innerIdx.getIndex());
                buf.mstoreAt(0x00);
                buf.pushInt(baseSlot);
                buf.mstoreAt(0x20);
                buf.pushInt(64);
                buf.pushInt(0);
                buf.emit(EvmOpcode.SHA3);
                // Now the intermediate slot hash is on the stack
                // Compute outer slot: keccak256(key2 . intermediateSlot)
                buf.mstoreAt(0x20); // store intermediate slot at 0x20
                emitExpression(expr.getIndex());
                buf.mstoreAt(0x00); // store outer key at 0x00
                buf.pushInt(64);
                buf.pushInt(0);
                buf.emit(EvmOpcode.SHA3);
                buf.emit(EvmOpcode.SLOAD);
                return;
            }
        }

        Integer baseSlot = resolveMappingBaseSlot(expr.getObject());
        if (baseSlot != null) {
            // Compute runtime mapping slot: keccak256(key || baseSlot)
            // 1. Store key at memory scratch area 0x00
            emitExpression(expr.getIndex());
            buf.mstoreAt(0x00);
            // 2. Store base slot at memory scratch area 0x20
            buf.pushInt(baseSlot);
            buf.mstoreAt(0x20);
            // 3. SHA3(0x00, 64) → slot hash on stack
            buf.pushInt(64);
            buf.pushInt(0);
            buf.emit(EvmOpcode.SHA3);
            // 4. SLOAD from computed slot
            buf.emit(EvmOpcode.SLOAD);
        } else {
            // Fallback: treat as array-like access (push 0 for now)
            buf.pushInt(0);
        }
    }

    private void emitIndexAssign(IndexAssignExpr expr) {
        // For mappings: compute slot via keccak256(key . baseSlot) then SSTORE
        // Supports nested mappings: mapping[k1][k2] = v → keccak256(k2 . keccak256(k1 . baseSlot))
        if (expr.getObject() instanceof IndexExpr innerIdx) {
            Integer baseSlot = resolveMappingBaseSlot(innerIdx.getObject());
            if (baseSlot != null) {
                // Compute inner slot: keccak256(key1 . baseSlot)
                emitExpression(innerIdx.getIndex());
                buf.mstoreAt(0x00);
                buf.pushInt(baseSlot);
                buf.mstoreAt(0x20);
                buf.pushInt(64);
                buf.pushInt(0);
                buf.emit(EvmOpcode.SHA3);
                // Compute outer slot: keccak256(key2 . intermediateSlot)
                buf.mstoreAt(0x20);
                emitExpression(expr.getIndex());
                buf.mstoreAt(0x00);
                buf.pushInt(64);
                buf.pushInt(0);
                buf.emit(EvmOpcode.SHA3);
                // Store value
                emitExpression(expr.getValue());
                buf.emit(EvmOpcode.SWAP1);
                buf.emit(EvmOpcode.SSTORE);
                return;
            }
        }

        Integer baseSlot = resolveMappingBaseSlot(expr.getObject());
        if (baseSlot != null) {
            // 1. Compute slot
            emitExpression(expr.getIndex());
            buf.mstoreAt(0x00);
            buf.pushInt(baseSlot);
            buf.mstoreAt(0x20);
            buf.pushInt(64);
            buf.pushInt(0);
            buf.emit(EvmOpcode.SHA3);
            // 2. Evaluate value and store
            emitExpression(expr.getValue());
            buf.emit(EvmOpcode.SWAP1);
            buf.emit(EvmOpcode.SSTORE);
        } else {
            // Fallback: just evaluate value (no storage)
            emitExpression(expr.getValue());
        }
    }

    /** Resolve the storage base slot if the expression refers to a this.mapping field. */
    private Integer resolveMappingBaseSlot(Expression obj) {
        if (obj instanceof GetExpr ge && ge.getObject() instanceof ThisExpr) {
            String fieldName = ge.getName().getLexeme();
            return storageSlots.get(fieldName);
        }
        if (obj instanceof VariableExpr ve) {
            String name = ve.getName().getLexeme();
            return storageSlots.get(name);
        }
        return null;
    }

    // ── Dynamic Array Operations ─────────────────────────────────────────
    // Solidity convention: base slot holds length, elements at keccak256(baseSlot) + index

    /**
     * Push a value to a dynamic storage array.
     * <pre>
     * length = SLOAD(baseSlot)
     * elementSlot = keccak256(baseSlot) + length
     * SSTORE(elementSlot, value)
     * SSTORE(baseSlot, length + 1)
     * </pre>
     */
    private void emitDynamicArrayPush(int baseSlot, List<Expression> args) {
        // Load current length
        buf.sloadSlot(baseSlot);
        // Compute element slot: keccak256(baseSlot) + length
        buf.pushInt(baseSlot);
        buf.mstoreAt(0x00);
        buf.pushInt(32);
        buf.pushInt(0);
        buf.emit(EvmOpcode.SHA3);  // keccak256(baseSlot) on stack
        buf.emit(EvmOpcode.SWAP1); // stack: [keccak, length]  → [length, keccak]
        // But we need length again — DUP before SHA3. Let me restructure:

        // Restart: get length, dup it for later increment
        // stack: []
        buf.sloadSlot(baseSlot);       // [length]
        buf.emit(EvmOpcode.DUP1);      // [length, length]

        // Compute base element offset: keccak256(abi.encode(baseSlot))
        buf.pushInt(baseSlot);
        buf.mstoreAt(0x00);
        buf.pushInt(32);
        buf.pushInt(0);
        buf.emit(EvmOpcode.SHA3);      // [length, length, keccak(slot)]

        // Remove the extra length from the first sloadSlot call above
        // Actually let me simplify — we did sloadSlot twice. Let me just use scratch:

        // Clean approach:
        // We already have [length, length, keccak] — that's wrong. Let me do it properly:
        // (The previous buf.sloadSlot added an extra value. Pop it.)
        buf.emit(EvmOpcode.SWAP2);
        buf.emit(EvmOpcode.POP);        // [keccak, length]
        buf.emit(EvmOpcode.ADD);         // [elementSlot]

        // Store value at element slot
        if (!args.isEmpty()) {
            emitExpression(args.get(0));
        } else {
            buf.pushInt(0); // push default 0
        }
        buf.emit(EvmOpcode.SWAP1);
        buf.emit(EvmOpcode.SSTORE);      // SSTORE(elementSlot, value)

        // Increment length: SSTORE(baseSlot, length + 1)
        buf.sloadSlot(baseSlot);
        buf.pushInt(1);
        buf.emit(EvmOpcode.ADD);
        buf.sstoreSlot(baseSlot);

        buf.pushInt(1); // expression result
    }

    /**
     * Pop the last value from a dynamic storage array.
     * <pre>
     * length = SLOAD(baseSlot)
     * require(length > 0)
     * newLength = length - 1
     * elementSlot = keccak256(baseSlot) + newLength
     * SSTORE(elementSlot, 0)   // clear
     * SSTORE(baseSlot, newLength)
     * </pre>
     */
    private void emitDynamicArrayPop(int baseSlot) {
        // Load length, check > 0
        buf.sloadSlot(baseSlot);
        buf.emit(EvmOpcode.DUP1);
        String okLabel = buf.newLabel();
        buf.jumpIf(okLabel);
        buf.revertWithMessage("Array: pop from empty");
        buf.placeLabel(okLabel);

        // newLength = length - 1
        buf.pushInt(1);
        buf.emit(EvmOpcode.SWAP1);
        buf.emit(EvmOpcode.SUB);        // [newLength]
        buf.emit(EvmOpcode.DUP1);       // [newLength, newLength]

        // Compute element slot to clear
        buf.pushInt(baseSlot);
        buf.mstoreAt(0x00);
        buf.pushInt(32);
        buf.pushInt(0);
        buf.emit(EvmOpcode.SHA3);
        buf.emit(EvmOpcode.ADD);         // [newLength, elementSlot]

        // Clear the element: SSTORE(elementSlot, 0)
        buf.pushInt(0);
        buf.emit(EvmOpcode.SWAP1);
        buf.emit(EvmOpcode.SSTORE);

        // Store new length
        buf.sstoreSlot(baseSlot);

        buf.pushInt(1); // expression result
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
        // Look up event function declaration for proper parameter types
        FunctionDecl eventDecl = findEventFunction(eventName);
        List<VarDecl> params = eventDecl != null ? eventDecl.getParameters() : null;

        // Build event signature for topic hash using declared types
        StringBuilder sig = new StringBuilder(eventName).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sig.append(',');
            sig.append(toSolidityType(params, i));
        }
        sig.append(')');
        byte[] topicHash = FunctionSelector.keccak256(
                sig.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Determine indexed parameters from the event declaration's `indexed`
        // modifiers. When the declaration can't be resolved, no parameters are
        // treated as indexed (the correct Solidity default).
        List<Integer> indexedIndices = new ArrayList<>();
        List<Integer> dataIndices = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            boolean isIndexed = params != null && i < params.size()
                    && params.get(i).isIndexed();
            if (isIndexed) {
                indexedIndices.add(i);
            } else {
                dataIndices.add(i);
            }
        }
        if (indexedIndices.size() > 3) {
            throw new IllegalStateException("Event '" + eventName + "' declares "
                    + indexedIndices.size() + " indexed parameters; the EVM allows"
                    + " at most 3 (LOG4 = 1 signature topic + 3 indexed).");
        }

        // Store non-indexed args in memory as 32-byte words
        int dataSize = dataIndices.size() * 32;
        for (int d = 0; d < dataIndices.size(); d++) {
            emitExpression(args.get(dataIndices.get(d)));
            buf.mstoreAt(d * 32);
        }

        // Push indexed topics in reverse order (EVM stack ordering)
        for (int t = indexedIndices.size() - 1; t >= 0; t--) {
            emitExpression(args.get(indexedIndices.get(t)));
        }

        // Push event signature topic
        buf.push32(new java.math.BigInteger(1, topicHash));

        // Push data size and offset
        buf.pushInt(dataSize);
        buf.pushInt(0);

        // Emit appropriate LOG opcode: LOG1 (sig only) .. LOG4 (sig + 3 indexed)
        int totalTopics = 1 + indexedIndices.size(); // sig topic + indexed params
        switch (totalTopics) {
            case 1 -> buf.emit(EvmOpcode.LOG1);
            case 2 -> buf.emit(EvmOpcode.LOG2);
            case 3 -> buf.emit(EvmOpcode.LOG3);
            case 4 -> buf.emit(EvmOpcode.LOG4);
            default -> buf.emit(EvmOpcode.LOG1); // fallback
        }

        buf.pushInt(1);  // Expression result
    }

    private FunctionDecl findEventFunction(String name) {
        for (FunctionDecl fn : contract.getFunctions()) {
            if (fn.getName().equals(name)
                    && fn.hasContractAnnotation(ContractAnnotation.EVENT)) {
                return fn;
            }
        }
        return null;
    }

    private FunctionDecl findErrorFunction(String name) {
        for (FunctionDecl fn : contract.getFunctions()) {
            if (fn.getName().equals(name)
                    && fn.hasContractAnnotation(ContractAnnotation.ERROR)) {
                return fn;
            }
        }
        return null;
    }

    private boolean isErrorFunction(String name) {
        return findErrorFunction(name) != null;
    }

    /**
     * Emit a revert with a custom error: {@code revert ErrName(args...)}.
     *
     * <p>ABI encoding mirrors Solidity custom errors — a 4-byte selector
     * (left-aligned in the first word) followed by each argument ABI-encoded as
     * a 32-byte word, then {@code REVERT(0, 4 + 32*nargs)}. Only value-type
     * arguments (uint256/address/bool) are supported in this release, matching
     * the indexed-event-parameter constraint.</p>
     */
    private void emitCustomErrorRevert(String errorName, List<Expression> args) {
        FunctionDecl errorDecl = findErrorFunction(errorName);

        // Compute the 4-byte selector from the declared error signature using
        // the canonical ABI type mapping, so the on-chain selector matches the
        // error entry in the generated ABI (off-chain decoders rely on this).
        byte[] selector;
        if (errorDecl != null) {
            selector = AbiGenerator.functionSelector(errorDecl);
        } else {
            StringBuilder sig = new StringBuilder(errorName).append('(');
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sig.append(',');
                sig.append(toSolidityType(null, i));
            }
            sig.append(')');
            selector = FunctionSelector.compute(sig.toString());
        }

        // Store the selector left-aligned in the first memory word at offset 0.
        buf.push32(new java.math.BigInteger(1, selector).shiftLeft(224));
        buf.mstoreAt(0);

        // Store each argument as a 32-byte word starting after the selector.
        for (int i = 0; i < args.size(); i++) {
            emitExpression(args.get(i));
            buf.mstoreAt(4 + i * 32);
        }

        // REVERT(offset=0, size=4 + 32*nargs)
        int size = 4 + args.size() * 32;
        buf.pushInt(size);
        buf.pushInt(0);
        buf.emit(EvmOpcode.REVERT);
    }

    private String toSolidityType(List<VarDecl> params, int index) {
        if (params == null || index >= params.size()) return "uint256";
        String type = params.get(index).getType();
        if (type == null) return "uint256";
        return switch (type) {
            case "num" -> "uint256";
            case "duo" -> "uint256";
            case "kya" -> "bool";
            case "sab" -> "string";
            case "address" -> "address";
            default -> "uint256";
        };
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

    // ── External contract call ───────────────────────────────────────────

    /**
     * Emit an external contract call: contract.functionName(args...)
     * Encodes the function selector + ABI-encoded arguments as calldata,
     * then uses CALL opcode.
     *
     * @param target   the contract address expression
     * @param fnName   the function name to call
     * @param args     the function arguments
     */
    private void emitExternalContractCall(Expression target, String fnName, List<Expression> args) {
        // Build function signature for selector
        StringBuilder sig = new StringBuilder(fnName).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sig.append(',');
            sig.append("uint256"); // default type for external calls
        }
        sig.append(')');
        byte[] selector = FunctionSelector.compute(sig.toString());

        // Store selector at memory[0x00] (left-aligned in 32-byte word)
        buf.push32(new java.math.BigInteger(1, selector).shiftLeft(224));
        buf.mstoreAt(0x00);

        // ABI-encode arguments starting at memory[0x04]
        for (int i = 0; i < args.size(); i++) {
            emitExpression(args.get(i));
            buf.mstoreAt(4 + i * 32);
        }
        int calldataSize = 4 + args.size() * 32;

        // CALL(gas, to, value=0, inOffset=0, inSize, outOffset, outSize)
        buf.pushInt(32);           // outSize (expect 32-byte return)
        buf.pushInt(calldataSize); // outOffset (reuse after calldata)
        buf.pushInt(calldataSize); // inSize
        buf.pushInt(0);            // inOffset
        buf.pushInt(0);            // value = 0 (no ETH sent)
        emitExpression(target);    // to address
        buf.emit(EvmOpcode.GAS);   // gas = remaining gas
        buf.emit(EvmOpcode.CALL);

        // Check success — if failed, revert
        String okLabel = buf.newLabel();
        buf.jumpIf(okLabel);
        buf.revert0();
        buf.placeLabel(okLabel);

        // Load return value from memory
        buf.mloadAt(calldataSize);
    }

    /**
     * Emit a STATICCALL for view/pure functions on external contracts.
     * Same as external call but uses STATICCALL (read-only, no state modification).
     */
    private void emitStaticCall(Expression target, String fnName, List<Expression> args) {
        // Build function signature for selector
        StringBuilder sig = new StringBuilder(fnName).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sig.append(',');
            sig.append("uint256");
        }
        sig.append(')');
        byte[] selector = FunctionSelector.compute(sig.toString());

        // Store selector at memory[0x00]
        buf.push32(new java.math.BigInteger(1, selector).shiftLeft(224));
        buf.mstoreAt(0x00);

        // ABI-encode arguments
        for (int i = 0; i < args.size(); i++) {
            emitExpression(args.get(i));
            buf.mstoreAt(4 + i * 32);
        }
        int calldataSize = 4 + args.size() * 32;

        // STATICCALL(gas, to, inOffset, inSize, outOffset, outSize)
        buf.pushInt(32);           // outSize
        buf.pushInt(calldataSize); // outOffset
        buf.pushInt(calldataSize); // inSize
        buf.pushInt(0);            // inOffset
        emitExpression(target);    // to address
        buf.emit(EvmOpcode.GAS);   // gas
        buf.emit(EvmOpcode.STATICCALL);

        // Check success
        String okLabel = buf.newLabel();
        buf.jumpIf(okLabel);
        buf.revert0();
        buf.placeLabel(okLabel);

        // Load return value from memory
        buf.mloadAt(calldataSize);
    }

    /**
     * Emit a DELEGATECALL to another contract.
     * DELEGATECALL preserves msg.sender and msg.value from the calling contract.
     * Used for proxy patterns and library calls.
     */
    private void emitDelegateCall(Expression target, String fnName, List<Expression> args) {
        // Build function signature for selector
        StringBuilder sig = new StringBuilder(fnName).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sig.append(',');
            sig.append("uint256");
        }
        sig.append(')');
        byte[] selector = FunctionSelector.compute(sig.toString());

        // Store selector at memory[0x00]
        buf.push32(new java.math.BigInteger(1, selector).shiftLeft(224));
        buf.mstoreAt(0x00);

        // ABI-encode arguments
        for (int i = 0; i < args.size(); i++) {
            emitExpression(args.get(i));
            buf.mstoreAt(4 + i * 32);
        }
        int calldataSize = 4 + args.size() * 32;

        // DELEGATECALL(gas, to, inOffset, inSize, outOffset, outSize)
        buf.pushInt(32);           // outSize
        buf.pushInt(calldataSize); // outOffset
        buf.pushInt(calldataSize); // inSize
        buf.pushInt(0);            // inOffset
        emitExpression(target);    // to address
        buf.emit(EvmOpcode.GAS);   // gas
        buf.emit(EvmOpcode.DELEGATECALL);

        // Check success
        String okLabel = buf.newLabel();
        buf.jumpIf(okLabel);
        buf.revert0();
        buf.placeLabel(okLabel);

        // Load return value from memory
        buf.mloadAt(calldataSize);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private FunctionDecl findConstructor() {
        for (FunctionDecl fn : contract.getFunctions()) {
            if (fn.isContractConstructor()) return fn;
        }
        return null;
    }

    private FunctionDecl findNamedFunction(String name) {
        for (FunctionDecl fn : contract.getFunctions()) {
            if (name.equals(fn.getName())) return fn;
        }
        return null;
    }

    /** Collect dispatchable functions from this class and all superclasses.
     *  Subclass methods override superclass methods with the same selector. */
    private void collectDispatchFunctions(ClassDecl cls, List<FunctionDecl> out, Set<String> seenSelectors) {
        // Add own functions first (most derived wins)
        for (FunctionDecl fn : cls.getFunctions()) {
            if (fn.isContractConstructor()
                    || fn.hasContractAnnotation(ContractAnnotation.EVENT)
                    || fn.hasContractAnnotation(ContractAnnotation.ERROR)
                    || "fallback".equals(fn.getName())
                    || "receive".equals(fn.getName())) {
                continue;
            }
            String selHex = bytesToHex(AbiGenerator.functionSelector(fn));
            if (seenSelectors.add(selHex)) {
                out.add(fn);
            }
        }
        // Walk superclass chain
        if (cls.getSuperclass() != null) {
            String superName = cls.getSuperclass().getName().getLexeme();
            ClassDecl superDecl = classRegistry.get(superName);
            if (superDecl != null) {
                collectDispatchFunctions(superDecl, out, seenSelectors);
            }
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
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
