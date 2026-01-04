package dhrlang.bytecode;

import java.io.*;

/** Tiny VM executing DhrLang bytecode for the current IR subset. */
public class BytecodeVM {
    private static final int MAGIC = 0x44484243; // 'DHBC'
    private static final int VERSION = 2;
    private static final Object NO_EXCEPTION = new Object();

    // Exception handler descriptor for this VM
    private static class Handler {
        final int pc;
        final String type;
        Handler(int pc, String type){ this.pc = pc; this.type = type; }
    }

    private static class Func {
        String name;
        int insCount;
        BytecodeOpcode[] op;
        int[][] args;
        boolean[] printNl;
    }

    public void execute(byte[] code){
        try{
            boolean untrusted = Boolean.getBoolean("dhrlang.bytecode.untrusted");

            int maxBytecodeBytes = Integer.getInteger(
                "dhrlang.bytecode.maxBytes",
                untrusted ? (10 * 1024 * 1024) : (50 * 1024 * 1024)
            );
            if(code == null) throw new IllegalArgumentException("Bytecode is null");
            if(code.length > maxBytecodeBytes) throw new IllegalArgumentException("Bytecode too large: "+code.length+" bytes (max: "+maxBytecodeBytes+")");

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(code));
            if(in.readInt()!=MAGIC) throw new IllegalArgumentException("Bad magic");
            if(in.readInt()!=VERSION) throw new IllegalArgumentException("Bad version");
            // Read constants
            int cpCount = in.readInt();
            if(cpCount < 0) throw new IllegalArgumentException("Invalid constant pool size: "+cpCount);
            int maxCp = Integer.getInteger("dhrlang.bytecode.maxConstPool", untrusted ? 10_000 : 50_000);
            if(cpCount > maxCp) throw new IllegalArgumentException("Constant pool too large: "+cpCount+" (max: "+maxCp+")");
            Object[] cp = new Object[cpCount];
            for(int i=0;i<cpCount;i++){
                int tag = in.readByte();
                switch(tag){
                    case 0 -> cp[i] = null;
                    case 1 -> cp[i] = in.readLong();
                    case 2 -> cp[i] = in.readDouble();
                    case 3 -> cp[i] = in.readUTF();
                    case 4 -> cp[i] = in.readBoolean();
                    default -> throw new IllegalArgumentException("Unknown const tag "+tag);
                }
            }
            int fnCount = in.readInt();
            if(fnCount < 0) throw new IllegalArgumentException("Invalid function count: "+fnCount);
            int maxFns = Integer.getInteger("dhrlang.bytecode.maxFunctions", untrusted ? 2_000 : 10_000);
            if(fnCount > maxFns) throw new IllegalArgumentException("Too many functions: "+fnCount+" (max: "+maxFns+")");
            if(fnCount<=0) return;
            Func[] funcs = new Func[fnCount];
            for(int f=0; f<fnCount; f++){
                Func fn = new Func();
                fn.name = in.readUTF();
                fn.insCount = in.readInt();
                if(fn.insCount < 0) throw new IllegalArgumentException("Invalid instruction count in function "+fn.name+": "+fn.insCount);
                int maxIns = Integer.getInteger("dhrlang.bytecode.maxInstructionsPerFunction", untrusted ? 200_000 : 500_000);
                if(fn.insCount > maxIns) throw new IllegalArgumentException("Too many instructions in function "+fn.name+": "+fn.insCount+" (max: "+maxIns+")");
                fn.op = new BytecodeOpcode[fn.insCount];
                fn.args = new int[fn.insCount][];
                fn.printNl = new boolean[fn.insCount];
                for(int i=0;i<fn.insCount;i++){
                    int rawOpcode = in.readInt();
                    BytecodeOpcode opc = BytecodeOpcode.from(rawOpcode);
                    fn.op[i] = opc;
                    switch(opc){
                        case CONST -> fn.args[i] = new int[]{ in.readInt(), in.readInt() };
                        case LOAD_LOCAL, STORE_LOCAL -> fn.args[i] = new int[]{ in.readInt(), in.readInt() };
                        case ADD, SUB, MUL, DIV -> fn.args[i] = new int[]{ in.readInt(), in.readInt(), in.readInt() };
                        case EQ, NEQ, LT, LE, GT, GE -> fn.args[i] = new int[]{ in.readInt(), in.readInt(), in.readInt() };
                        case JUMP -> fn.args[i] = new int[]{ in.readInt() };
                        case JUMP_IF_FALSE -> fn.args[i] = new int[]{ in.readInt(), in.readInt() };
                        case PRINT -> { fn.args[i] = new int[]{ in.readInt() }; fn.printNl[i] = in.readBoolean(); }
                        case RETURN -> fn.args[i] = new int[]{ in.readInt() };
                        case NEG, NOT -> fn.args[i] = new int[]{ in.readInt(), in.readInt() };
                        case NEW_ARRAY -> fn.args[i] = new int[]{ in.readInt(), in.readInt(), in.readInt() };
                        case LOAD_ELEM -> fn.args[i] = new int[]{ in.readInt(), in.readInt(), in.readInt() };
                        case STORE_ELEM -> fn.args[i] = new int[]{ in.readInt(), in.readInt(), in.readInt() };
                        case ARRAY_LENGTH -> fn.args[i] = new int[]{ in.readInt(), in.readInt() };
                        case CALL -> fn.args[i] = new int[]{ in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt() };
                        case GET_STATIC -> fn.args[i] = new int[]{ in.readInt(), in.readInt(), in.readInt() }; // classNameIdx, fieldNameIdx, targetSlot
                        case SET_STATIC -> fn.args[i] = new int[]{ in.readInt(), in.readInt(), in.readInt() }; // classNameIdx, fieldNameIdx, valueSlot
                        case GET_FIELD -> fn.args[i] = new int[]{ in.readInt(), in.readInt(), in.readInt() }; // objectSlot, fieldNameIdx, targetSlot
                        case SET_FIELD -> fn.args[i] = new int[]{ in.readInt(), in.readInt(), in.readInt() }; // objectSlot, fieldNameIdx, valueSlot
                        case TRY_PUSH -> fn.args[i] = new int[]{ in.readInt(), in.readInt() }; // catchPc, catchTypeIdx
                        case TRY_POP -> fn.args[i] = new int[]{};
                        case THROW -> fn.args[i] = new int[]{ in.readInt() }; // valueSlot
                        case CATCH_BIND -> fn.args[i] = new int[]{ in.readInt() }; // targetSlot
                    }
                }
                funcs[f] = fn;
            }

            // Validate bytecode (bounds, indices, types) before executing.
            validateBytecode(cp, funcs);

            // Build name->index map
            java.util.Map<String,Integer> fnIndex = new java.util.HashMap<>();
            for(int i=0;i<fnCount;i++) fnIndex.put(funcs[i].name, i);

            // Call stack
            java.util.Deque<Integer> stackFunc = new java.util.ArrayDeque<>();
            java.util.Deque<Integer> stackPc = new java.util.ArrayDeque<>();
            java.util.Deque<Object[]> stackSlots = new java.util.ArrayDeque<>();
            java.util.Deque<Integer> stackRetDest = new java.util.ArrayDeque<>();
            // Exception handling stacks (per frame)
            java.util.Deque<java.util.Deque<Handler>> stackHandlers = new java.util.ArrayDeque<>();
            java.util.Deque<Object> stackPendingEx = new java.util.ArrayDeque<>();

            int curFunc = 0;
            Integer entryIdx = fnIndex.get("Main.main");
            if(entryIdx == null){
                for(int i=0;i<funcs.length;i++){
                    if(funcs[i] != null && funcs[i].name != null && funcs[i].name.endsWith(".main")){
                        entryIdx = i;
                        break;
                    }
                }
            }

            boolean strictEntry = getBooleanProperty("dhrlang.bytecode.strictEntry", untrusted);
            if(strictEntry && entryIdx == null){
                throw new IllegalArgumentException("Invalid bytecode: no entrypoint found (expected Main.main or any *.main). Set -Ddhrlang.bytecode.strictEntry=false to allow defaulting to function index 0.");
            }
            if(entryIdx != null) curFunc = entryIdx;

            int pc = 0; Object[] slots = new Object[256];
            java.util.Deque<Handler> handlers = new java.util.ArrayDeque<>();
            Object pendingEx = null; // bubbling exception (dispatch)
            Object catchValue = null; // value to be bound by CATCH_BIND
            java.util.Map<String, java.util.Map<String,Object>> statics = new java.util.HashMap<>();
            Func cur = funcs[curFunc];
            int safetyCounter = 0;
            int maxSteps = Integer.getInteger("dhrlang.backend.maxSteps", untrusted ? 5_000_000 : 50_000_000);
            int maxCallDepth = Integer.getInteger("dhrlang.bytecode.maxCallDepth", untrusted ? 2_000 : 10_000);
            int maxHandlersPerFrame = Integer.getInteger("dhrlang.bytecode.maxHandlersPerFrame", untrusted ? 512 : 2_048);
            while(true){
                if(++safetyCounter > maxSteps){
                    throw dhrlang.error.ErrorFactory.runtimeError("Execution aborted: exceeded max instruction steps ("+maxSteps+") - possible infinite loop.", (dhrlang.error.SourceLocation) null);
                }
                if(pc >= cur.insCount){
                    // Implicit return
                    if(stackFunc.isEmpty()) return; else {
                        stackRetDest.pop();
                        int prevFunc = stackFunc.pop();
                        int prevPc = stackPc.pop();
                        Object[] prevSlots = stackSlots.pop();
                        handlers = stackHandlers.pop();
                        {
                            Object pe = stackPendingEx.pop();
                            pendingEx = (pe==NO_EXCEPTION)? null : pe;
                        }
                        // No value on implicit return
                        slots = prevSlots; curFunc = prevFunc; cur = funcs[curFunc]; pc = prevPc; continue;
                    }
                }
                BytecodeOpcode opc = cur.op[pc];
                int[] a = cur.args[pc];
                // If an exception is pending, attempt to transfer to nearest matching handler
                if(pendingEx != null){
                    if(!handlers.isEmpty()){
                        Handler target = null;
                        java.util.Iterator<Handler> it = handlers.iterator();
                        while(it.hasNext()){
                            Handler h = it.next();
                            if(matchesCatch(h.type, pendingEx)) { target = h; it.remove(); break; }
                        }
                        if(target!=null){
                            catchValue = pendingEx;
                            pendingEx = null;
                            pc = target.pc;
                            continue;
                        }
                    }
                    // Unwind: pop frame
                    if(stackFunc.isEmpty()) return; else {
                        stackRetDest.pop();
                        int prevFunc = stackFunc.pop();
                        int prevPc = stackPc.pop();
                        Object[] prevSlots = stackSlots.pop();
                        handlers = stackHandlers.pop();
                        Object prevPendingObj = stackPendingEx.pop();
                        Object prevPending = (prevPendingObj==NO_EXCEPTION)? null : prevPendingObj;
                        // bubble exception to caller (prefer caller's pending if any)
                        pendingEx = (prevPending!=null)? prevPending : pendingEx;
                        slots = prevSlots; curFunc = prevFunc; cur = funcs[curFunc]; pc = prevPc; continue;
                    }
                }
                switch(opc){
                    case CONST -> slots[a[0]] = cp[a[1]];
                    case LOAD_LOCAL -> slots[a[1]] = slots[a[0]];
                    case STORE_LOCAL -> slots[a[1]] = slots[a[0]];
                    case ADD, SUB, MUL, DIV -> {
                        Object lv = slots[a[0]], rv = slots[a[1]];
                        if(opc==BytecodeOpcode.ADD && (!(lv instanceof Number) || !(rv instanceof Number))){
                            if(lv instanceof String || rv instanceof String) slots[a[2]] = String.valueOf(lv) + String.valueOf(rv);
                            else throw dhrlang.error.ErrorFactory.typeError("Operands for '+' must be two numbers or at least one string for concatenation.", (dhrlang.error.SourceLocation) null);
                        } else {
                            if(!(lv instanceof Number) || !(rv instanceof Number)) throw dhrlang.error.ErrorFactory.typeError("Operands must be numbers for operator: "+opc.name(), (dhrlang.error.SourceLocation) null);
                            Number l = (Number) lv;
                            Number r = (Number) rv;
                            if(opc==BytecodeOpcode.DIV){
                                double divisor = r.doubleValue();
                                if(divisor==0.0) throw dhrlang.error.ErrorFactory.arithmeticError("Division by zero.", (dhrlang.error.SourceLocation) null);
                                slots[a[2]] = l.doubleValue()/divisor;
                            } else if(l instanceof Double || r instanceof Double){
                                double v = switch(opc){
                                    case ADD -> l.doubleValue()+r.doubleValue();
                                    case SUB -> l.doubleValue()-r.doubleValue();
                                    case MUL -> l.doubleValue()*r.doubleValue();
                                    default -> 0;
                                };
                                slots[a[2]] = v;
                            } else {
                                long lvv = l.longValue(), rvv = r.longValue();
                                slots[a[2]] = switch(opc){
                                    case ADD -> lvv+rvv;
                                    case SUB -> lvv-rvv;
                                    case MUL -> lvv*rvv;
                                    default -> 0;
                                };
                            }
                        }
                    }
                    case EQ, NEQ, LT, LE, GT, GE -> {
                        Object left = slots[a[0]];
                        Object right = slots[a[1]];
                        boolean bool;
                        switch(opc){
                            case EQ -> bool = java.util.Objects.equals(left, right);
                            case NEQ -> bool = !java.util.Objects.equals(left, right);
                            case LT, LE, GT, GE -> {
                                if(!(left instanceof Number) || !(right instanceof Number)) throw dhrlang.error.ErrorFactory.typeError("Operands must be numbers for operator: "+opc.name(), (dhrlang.error.SourceLocation) null);
                                double ld = ((Number)left).doubleValue();
                                double rd = ((Number)right).doubleValue();
                                bool = switch(opc){
                                    case LT -> ld < rd;
                                    case LE -> ld <= rd;
                                    case GT -> ld > rd;
                                    case GE -> ld >= rd;
                                    default -> false;
                                };
                            }
                            default -> bool = false;
                        }
                        slots[a[2]] = bool;
                    }
                    case JUMP -> { pc = a[0]-1; }
                    case JUMP_IF_FALSE -> { if(!truthy(slots[a[0]])) pc = a[1]-1; }
                    case PRINT -> { Object v = slots[a[0]]; if(cur.printNl[pc]) System.out.println(String.valueOf(v)); else System.out.print(String.valueOf(v)); }
                    case RETURN -> {
                        int retSlot = a[0]; Object retVal = (retSlot>=0? slots[retSlot] : null);
                        if(stackFunc.isEmpty()) return; else {
                            int dest = stackRetDest.pop();
                            int prevFunc = stackFunc.pop();
                            int prevPc = stackPc.pop();
                            Object[] prevSlots = stackSlots.pop();
                            java.util.Deque<Handler> prevHandlers = stackHandlers.pop();
                            Object prevPendingObj = stackPendingEx.pop();
                            Object prevPending = (prevPendingObj==NO_EXCEPTION)? null : prevPendingObj;
                            if(dest>=0) prevSlots[dest] = retVal;
                            slots = prevSlots; handlers = prevHandlers; pendingEx = prevPending; curFunc = prevFunc; cur = funcs[curFunc]; pc = prevPc;
                            continue;
                        }
                    }
                    case NEG -> {
                        Object v = slots[a[0]]; Object r;
                        if(v instanceof Integer i) r = -i.longValue();
                        else if(v instanceof Long l) r = -l;
                        else if(v instanceof Double d) r = -d;
                        else throw dhrlang.error.ErrorFactory.typeError("Operand for '-' must be a number.", (dhrlang.error.SourceLocation) null);
                        slots[a[1]] = r;
                    }
                    case NOT -> { slots[a[1]] = !truthy(slots[a[0]]); }
                    case NEW_ARRAY -> {
                        Object sz = slots[a[0]];
                        if(!(sz instanceof Long) && !(sz instanceof Integer)) throw dhrlang.error.ErrorFactory.typeError("Array size must be a number.", (dhrlang.error.SourceLocation) null);
                        int n = ((Number)sz).intValue();
                        if(n < 0) throw dhrlang.error.ErrorFactory.validationError("Array size cannot be negative.", (dhrlang.error.SourceLocation) null);
                        if(n > 1_000_000) throw dhrlang.error.ErrorFactory.validationError("Array size too large (max: 1,000,000).", (dhrlang.error.SourceLocation) null);
                        Object[] arr = new Object[n];
                        int typeIdx = a[2];
                        String elementType = typeIdx >= 0 ? (String) cp[typeIdx] : null;
                        Object def = dhrlang.runtime.RuntimeDefaults.getDefaultValue(elementType);
                        if(def != null) java.util.Arrays.fill(arr, def);
                        slots[a[1]] = arr;
                    }
                    case LOAD_ELEM -> {
                        Object arrObj = slots[a[0]];
                        Object idxObj = slots[a[1]];
                        if(!(arrObj instanceof Object[] arr)) throw dhrlang.error.ErrorFactory.typeError("Can only index arrays.", (dhrlang.error.SourceLocation) null);
                        if(!(idxObj instanceof Long) && !(idxObj instanceof Integer)) throw dhrlang.error.ErrorFactory.typeError("Array index must be a number.", (dhrlang.error.SourceLocation) null);
                        int i = ((Number)idxObj).intValue();
                        if(i<0 || i>=arr.length) throw dhrlang.error.ErrorFactory.indexError("Array index "+i+" out of bounds for array of length "+arr.length+".", (dhrlang.error.SourceLocation) null);
                        slots[a[2]] = arr[i];
                    }
                    case STORE_ELEM -> {
                        Object arrObj = slots[a[0]];
                        Object idxObj = slots[a[1]];
                        if(!(arrObj instanceof Object[] arr)) throw dhrlang.error.ErrorFactory.typeError("Can only assign to array elements.", (dhrlang.error.SourceLocation) null);
                        if(!(idxObj instanceof Long) && !(idxObj instanceof Integer)) throw dhrlang.error.ErrorFactory.typeError("Array index must be a number.", (dhrlang.error.SourceLocation) null);
                        int i = ((Number)idxObj).intValue();
                        if(i<0 || i>=arr.length) throw dhrlang.error.ErrorFactory.indexError("Array index "+i+" out of bounds for array of length "+arr.length+".", (dhrlang.error.SourceLocation) null);
                        arr[i] = slots[a[2]];
                    }
                    case ARRAY_LENGTH -> {
                        Object arrObj = slots[a[0]];
                        if(!(arrObj instanceof Object[] arr)) throw dhrlang.error.ErrorFactory.typeError("Can only call arrayLength on arrays.", (dhrlang.error.SourceLocation) null);
                        slots[a[1]] = (long) arr.length;
                    }
                    case GET_STATIC -> {
                        String cls = (String) cp[a[0]]; String field = (String) cp[a[1]];
                        java.util.Map<String,Object> map = statics.computeIfAbsent(cls, k-> new java.util.HashMap<>());
                        slots[a[2]] = map.get(field);
                    }
                    case SET_STATIC -> {
                        String cls = (String) cp[a[0]]; String field = (String) cp[a[1]];
                        java.util.Map<String,Object> map = statics.computeIfAbsent(cls, k-> new java.util.HashMap<>());
                        map.put(field, slots[a[2]]);
                    }
                    case GET_FIELD -> {
                        Object obj = slots[a[0]]; String field = (String) cp[a[1]];
                        Object val = null; if(obj instanceof java.util.Map<?,?> m){ val = ((java.util.Map<?,?>)m).get(field); }
                        slots[a[2]] = val;
                    }
                    case SET_FIELD -> {
                        Object obj = slots[a[0]]; String field = (String) cp[a[1]];
                        if(obj instanceof java.util.Map<?,?>){ @SuppressWarnings("unchecked") java.util.Map<Object,Object> m = (java.util.Map<Object,Object>) obj; m.put(field, slots[a[2]]); }
                    }
                    case CALL -> {
                        int callee = a[0];
                        if(stackFunc.size() >= maxCallDepth){
                            throw dhrlang.error.ErrorFactory.runtimeError("Execution aborted: exceeded max call depth ("+maxCallDepth+").", (dhrlang.error.SourceLocation) null);
                        }
                        // Save current state; next instruction will resume after call returns
                        stackFunc.push(curFunc); stackPc.push(pc+1); stackSlots.push(slots); stackRetDest.push(a[5]);
                        stackHandlers.push(handlers); stackPendingEx.push(pendingEx==null? NO_EXCEPTION : pendingEx);
                        // Switch to callee
                        curFunc = callee; cur = funcs[curFunc]; pc = 0; slots = new Object[256];
                        handlers = new java.util.ArrayDeque<>(); pendingEx = null; catchValue = null;
                        // args: a1..a4
                        Object[] callerSlots = stackSlots.peek();
                        if(a[1] >= 0) slots[0] = callerSlots[a[1]];
                        if(a[2] >= 0) slots[1] = callerSlots[a[2]];
                        if(a[3] >= 0) slots[2] = callerSlots[a[3]];
                        if(a[4] >= 0) slots[3] = callerSlots[a[4]];
                        continue;
                    }
                    case TRY_PUSH -> { handlers.push(new Handler(a[0], (String) cp[a[1]])); }
                    case TRY_POP -> {
                        if(handlers.isEmpty()) throw new IllegalArgumentException("Invalid bytecode in "+cur.name+" @pc="+pc+": TRY_POP with empty handler stack");
                        handlers.pop();
                    }
                    case THROW -> { pendingEx = slots[a[0]]; }
                    case CATCH_BIND -> { slots[a[0]] = catchValue; catchValue = null; }
                }
                if(handlers.size() > maxHandlersPerFrame){
                    throw dhrlang.error.ErrorFactory.runtimeError("Execution aborted: exceeded max try-handler depth ("+maxHandlersPerFrame+").", (dhrlang.error.SourceLocation) null);
                }
                pc++;
            }
        }catch(IOException e){ throw new RuntimeException(e); }
    }

    private static boolean getBooleanProperty(String key, boolean defaultValue){
        String v = System.getProperty(key);
        if(v == null) return defaultValue;
        return Boolean.parseBoolean(v);
    }

    private static void validateBytecode(Object[] cp, Func[] funcs){
        boolean verifyControlFlow = Boolean.parseBoolean(System.getProperty("dhrlang.bytecode.verifyControlFlow", "true"));
        int fnCount = funcs.length;
        for(int f=0; f<fnCount; f++){
            Func fn = funcs[f];
            if(fn == null) throw new IllegalArgumentException("Invalid bytecode: null function at index "+f);
            if(fn.name == null) throw new IllegalArgumentException("Invalid bytecode: function name is null at index "+f);
            boolean[] isHandlerEntry = new boolean[fn.insCount];
            for(int pc=0; pc<fn.insCount; pc++){
                BytecodeOpcode opc = fn.op[pc];
                int[] a = fn.args[pc];
                switch(opc){
                    case CONST -> { verifySlot(a[0], fn.name, pc, "targetSlot"); verifyCpIndex(a[1], cp.length, fn.name, pc, "constIndex"); }
                    case LOAD_LOCAL, STORE_LOCAL -> { verifySlot(a[0], fn.name, pc, "sourceSlot"); verifySlot(a[1], fn.name, pc, "targetSlot"); }
                    case ADD, SUB, MUL, DIV, EQ, NEQ, LT, LE, GT, GE -> {
                        verifySlot(a[0], fn.name, pc, "leftSlot"); verifySlot(a[1], fn.name, pc, "rightSlot"); verifySlot(a[2], fn.name, pc, "targetSlot");
                    }
                    case JUMP -> verifyPcTarget(a[0], fn.insCount, fn.name, pc, "jumpTarget");
                    case JUMP_IF_FALSE -> { verifySlot(a[0], fn.name, pc, "condSlot"); verifyPcTarget(a[1], fn.insCount, fn.name, pc, "jumpTarget"); }
                    case PRINT -> verifySlot(a[0], fn.name, pc, "valueSlot");
                    case RETURN -> verifySlotAllowMinusOne(a[0], fn.name, pc, "returnSlot");
                    case NEG, NOT -> { verifySlot(a[0], fn.name, pc, "sourceSlot"); verifySlot(a[1], fn.name, pc, "targetSlot"); }
                    case NEW_ARRAY -> {
                        verifySlot(a[0], fn.name, pc, "sizeSlot"); verifySlot(a[1], fn.name, pc, "targetSlot");
                        if(a[2] != -1) verifyCpString(a[2], cp, fn.name, pc, "elementType");
                    }
                    case LOAD_ELEM -> { verifySlot(a[0], fn.name, pc, "arraySlot"); verifySlot(a[1], fn.name, pc, "indexSlot"); verifySlot(a[2], fn.name, pc, "targetSlot"); }
                    case STORE_ELEM -> { verifySlot(a[0], fn.name, pc, "arraySlot"); verifySlot(a[1], fn.name, pc, "indexSlot"); verifySlot(a[2], fn.name, pc, "valueSlot"); }
                    case ARRAY_LENGTH -> { verifySlot(a[0], fn.name, pc, "arraySlot"); verifySlot(a[1], fn.name, pc, "targetSlot"); }
                    case CALL -> {
                        int callee = a[0];
                        if(callee < 0 || callee >= fnCount) throw new IllegalArgumentException("Invalid bytecode in "+fn.name+" @pc="+pc+": invalid callee function index "+callee);
                        verifySlotAllowMinusOne(a[1], fn.name, pc, "arg0");
                        verifySlotAllowMinusOne(a[2], fn.name, pc, "arg1");
                        verifySlotAllowMinusOne(a[3], fn.name, pc, "arg2");
                        verifySlotAllowMinusOne(a[4], fn.name, pc, "arg3");
                        verifySlotAllowMinusOne(a[5], fn.name, pc, "destSlot");
                    }
                    case GET_STATIC -> { verifyCpString(a[0], cp, fn.name, pc, "className"); verifyCpString(a[1], cp, fn.name, pc, "fieldName"); verifySlot(a[2], fn.name, pc, "targetSlot"); }
                    case SET_STATIC -> { verifyCpString(a[0], cp, fn.name, pc, "className"); verifyCpString(a[1], cp, fn.name, pc, "fieldName"); verifySlot(a[2], fn.name, pc, "valueSlot"); }
                    case GET_FIELD -> { verifySlot(a[0], fn.name, pc, "objectSlot"); verifyCpString(a[1], cp, fn.name, pc, "fieldName"); verifySlot(a[2], fn.name, pc, "targetSlot"); }
                    case SET_FIELD -> { verifySlot(a[0], fn.name, pc, "objectSlot"); verifyCpString(a[1], cp, fn.name, pc, "fieldName"); verifySlot(a[2], fn.name, pc, "valueSlot"); }
                    case TRY_PUSH -> { verifyPcTarget(a[0], fn.insCount, fn.name, pc, "catchPc"); verifyCpString(a[1], cp, fn.name, pc, "catchType"); }
                    case TRY_POP -> {}
                    case THROW -> verifySlot(a[0], fn.name, pc, "valueSlot");
                    case CATCH_BIND -> verifySlot(a[0], fn.name, pc, "targetSlot");
                }

                if(opc == BytecodeOpcode.TRY_PUSH){
                    int catchPc = a[0];
                    if(catchPc >= 0 && catchPc < fn.insCount) isHandlerEntry[catchPc] = true;
                }
            }

            if(verifyControlFlow){
                validateTryStackControlFlow(fn, isHandlerEntry);
            }
        }
    }

    private static void validateTryStackControlFlow(Func fn, boolean[] isHandlerEntry){
        // Enforce that catch blocks begin with CATCH_BIND and are not reachable via normal control-flow.
        // Also enforce that TRY_PUSH/TRY_POP stack depth is consistent on all merges and balanced on returns/exits.

        int n = fn.insCount;
        java.util.ArrayList<int[]> preds = new java.util.ArrayList<>(n);
        for(int i=0;i<n;i++) preds.add(new int[0]);

        java.util.ArrayList<java.util.ArrayList<Integer>> predLists = new java.util.ArrayList<>(n);
        for(int i=0;i<n;i++) predLists.add(new java.util.ArrayList<>());

        for(int pc=0; pc<n; pc++){
            BytecodeOpcode opc = fn.op[pc];
            int[] a = fn.args[pc];
            int[] succ;
            switch(opc){
                case JUMP -> succ = new int[]{ a[0] };
                case JUMP_IF_FALSE -> {
                    int t = a[1];
                    int fall = pc + 1;
                    if(fall < n) succ = new int[]{ fall, t };
                    else succ = new int[]{ t };
                }
                case RETURN -> succ = new int[]{};
                default -> {
                    int fall = pc + 1;
                    succ = (fall < n) ? new int[]{ fall } : new int[]{};
                }
            }
            for(int s : succ){
                if(s < 0 || s >= n) continue;
                predLists.get(s).add(pc);
            }
        }

        for(int i=0;i<n;i++){
            var list = predLists.get(i);
            int[] p = new int[list.size()];
            for(int k=0;k<list.size();k++) p[k] = list.get(k);
            preds.set(i, p);
        }

        for(int pc=0; pc<n; pc++){
            if(isHandlerEntry[pc]){
                if(fn.op[pc] != BytecodeOpcode.CATCH_BIND){
                    throw new IllegalArgumentException("Invalid bytecode in "+fn.name+" @pc="+pc+": catch entry must start with CATCH_BIND");
                }
                if(preds.get(pc).length != 0){
                    throw new IllegalArgumentException("Invalid bytecode in "+fn.name+" @pc="+pc+": catch entry is reachable via normal control flow");
                }
            }
        }

        int[] depthAt = new int[n];
        java.util.Arrays.fill(depthAt, Integer.MIN_VALUE);
        java.util.ArrayDeque<Integer> work = new java.util.ArrayDeque<>();
        depthAt[0] = 0;
        work.add(0);

        while(!work.isEmpty()){
            int pc = work.removeFirst();
            int inDepth = depthAt[pc];
            if(inDepth < 0) throw new IllegalArgumentException("Invalid bytecode in "+fn.name+" @pc="+pc+": negative try-stack depth");

            BytecodeOpcode opc = fn.op[pc];
            int[] a = fn.args[pc];
            if(opc == BytecodeOpcode.TRY_POP && inDepth == 0){
                throw new IllegalArgumentException("Invalid bytecode in "+fn.name+" @pc="+pc+": TRY_POP underflow");
            }
            if(opc == BytecodeOpcode.RETURN && inDepth != 0){
                throw new IllegalArgumentException("Invalid bytecode in "+fn.name+" @pc="+pc+": RETURN with non-empty try-handler stack (depth="+inDepth+")");
            }

            int outDepth = inDepth;
            if(opc == BytecodeOpcode.TRY_PUSH) outDepth = inDepth + 1;
            else if(opc == BytecodeOpcode.TRY_POP) outDepth = inDepth - 1;

            // Determine successors
            int[] succ;
            switch(opc){
                case JUMP -> succ = new int[]{ a[0] };
                case JUMP_IF_FALSE -> {
                    int t = a[1];
                    int fall = pc + 1;
                    if(fall < n) succ = new int[]{ fall, t };
                    else succ = new int[]{ t };
                }
                case RETURN -> succ = new int[]{};
                default -> {
                    int fall = pc + 1;
                    succ = (fall < n) ? new int[]{ fall } : new int[]{};
                }
            }

            if(succ.length == 0){
                // exit edge
                if(outDepth != 0){
                    throw new IllegalArgumentException("Invalid bytecode in "+fn.name+" @pc="+pc+": function exit with non-empty try-handler stack (depth="+outDepth+")");
                }
                continue;
            }

            for(int s : succ){
                if(s < 0 || s >= n) continue;
                int existing = depthAt[s];
                if(existing == Integer.MIN_VALUE){
                    depthAt[s] = outDepth;
                    work.add(s);
                } else if(existing != outDepth){
                    throw new IllegalArgumentException("Invalid bytecode in "+fn.name+" @pc="+s+": inconsistent try-handler stack depth on merge ("+existing+" vs "+outDepth+")");
                }
            }
        }
    }

    private static void verifySlot(int slot, String fn, int pc, String label){
        if(slot < 0 || slot >= 256) throw new IllegalArgumentException("Invalid bytecode in "+fn+" @pc="+pc+": "+label+" out of range: "+slot);
    }

    private static void verifySlotAllowMinusOne(int slot, String fn, int pc, String label){
        if(slot == -1) return;
        verifySlot(slot, fn, pc, label);
    }

    private static void verifyPcTarget(int target, int insCount, String fn, int pc, String label){
        if(target < 0 || target >= insCount) throw new IllegalArgumentException("Invalid bytecode in "+fn+" @pc="+pc+": "+label+" out of range: "+target+" (insCount="+insCount+")");
    }

    private static void verifyCpIndex(int idx, int cpCount, String fn, int pc, String label){
        if(idx < 0 || idx >= cpCount) throw new IllegalArgumentException("Invalid bytecode in "+fn+" @pc="+pc+": "+label+" out of range: "+idx+" (cpCount="+cpCount+")");
    }

    private static void verifyCpString(int idx, Object[] cp, String fn, int pc, String label){
        verifyCpIndex(idx, cp.length, fn, pc, label);
        Object v = cp[idx];
        if(!(v instanceof String)){
            String type = (v==null? "null" : v.getClass().getSimpleName());
            throw new IllegalArgumentException("Invalid bytecode in "+fn+" @pc="+pc+": "+label+" must be STRING constant, got "+type+" at cp["+idx+"]");
        }
    }

    // Typed catch matching similar to IR interpreter
    private static boolean matchesCatch(String catchType, Object exceptionValue){
        if("any".equals(catchType)) return true;
        Object payload = exceptionValue;
        if(payload instanceof dhrlang.stdlib.exceptions.ErrorException){
            if("Error".equals(catchType) || "DhrException".equals(catchType)) return true;
        }
        if(payload instanceof dhrlang.stdlib.exceptions.DhrException dhrEx){
            String simple = dhrEx.getExceptionType();
            if(simple!=null && (simple.equals(catchType) || (catchType.endsWith("Exception") && simple.endsWith(catchType)))) return true;
            if("DhrException".equals(catchType)) return true;
        }
        return false;
    }

    private static boolean truthy(Object v){ if(v==null) return false; if(v instanceof Boolean b) return b; return true; }
}
