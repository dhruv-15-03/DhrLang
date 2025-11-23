package dhrlang.bytecode;

import java.io.*;

/** Tiny VM executing DhrLang bytecode for the current IR subset. */
public class BytecodeVM {
    private static final int MAGIC = 0x44484243; // 'DHBC'
    private static final int VERSION = 1;
    private static final Object NO_EXCEPTION = new Object();

    // Exception handler descriptor for this VM
    private static class Handler {
        final int pc;
        final String type;
        Handler(int pc, String type){ this.pc = pc; this.type = type; }
    }

    public void execute(byte[] code){
        try{
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(code));
            if(in.readInt()!=MAGIC) throw new IllegalArgumentException("Bad magic");
            if(in.readInt()!=VERSION) throw new IllegalArgumentException("Bad version");
            // Read constants
            int cpCount = in.readInt();
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
            if(fnCount<=0) return;
            class Func { String name; int insCount; int[] op; int[][] args; boolean[] printNl; }
            Func[] funcs = new Func[fnCount];
            for(int f=0; f<fnCount; f++){
                Func fn = new Func();
                fn.name = in.readUTF();
                fn.insCount = in.readInt();
                fn.op = new int[fn.insCount];
                fn.args = new int[fn.insCount][];
                fn.printNl = new boolean[fn.insCount];
                for(int i=0;i<fn.insCount;i++){
                    fn.op[i] = in.readInt();
                    BytecodeOpcode opc = BytecodeOpcode.from(fn.op[i]);
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
                        case NEW_ARRAY -> fn.args[i] = new int[]{ in.readInt(), in.readInt() };
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

            int curFunc = 0; int pc = 0; Object[] slots = new Object[256];
            java.util.Deque<Handler> handlers = new java.util.ArrayDeque<>();
            Object pendingEx = null;
            java.util.Map<String, java.util.Map<String,Object>> statics = new java.util.HashMap<>();
            Func cur = funcs[curFunc];
            while(true){
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
                BytecodeOpcode opc = BytecodeOpcode.from(cur.op[pc]);
                int[] a = cur.args[pc];
                // If an exception is pending, attempt to transfer to nearest matching handler
                if(pendingEx != null){
                    if(!handlers.isEmpty()){
                        Handler target = null;
                        java.util.Iterator<Handler> it = handlers.iterator();
                        while(it.hasNext()){
                            Handler h = it.next();
                            if(matchesCatch((String) ((Object)h.type), pendingEx)) { target = h; it.remove(); break; }
                        }
                        if(target!=null){ pc = target.pc; continue; }
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
                            slots[a[2]] = String.valueOf(lv) + String.valueOf(rv);
                        } else {
                            Number l = lv instanceof Number ? (Number) lv : 0;
                            Number r = rv instanceof Number ? (Number) rv : 0;
                            double v = switch(opc){
                                case ADD -> l.doubleValue()+r.doubleValue();
                                case SUB -> l.doubleValue()-r.doubleValue();
                                case MUL -> l.doubleValue()*r.doubleValue();
                                case DIV -> r.doubleValue()==0?Double.NaN:l.doubleValue()/r.doubleValue();
                                default -> 0;
                            };
                            if(l instanceof Integer || l instanceof Long){
                                if(r instanceof Integer || r instanceof Long){
                                    long lvv=l.longValue(), rvv=r.longValue();
                                    slots[a[2]] = switch(opc){
                                        case ADD -> lvv+rvv; case SUB -> lvv-rvv; case MUL -> lvv*rvv; case DIV -> (rvv==0?0: lvv/rvv);
                                        default -> 0; };
                                    break;
                                }
                            }
                            slots[a[2]] = v;
                        }
                    }
                    case EQ, NEQ, LT, LE, GT, GE -> {
                        int res = compare(slots[a[0]], slots[a[1]]);
                        boolean bool = switch(opc){
                            case EQ -> res==0; case NEQ -> res!=0; case LT -> res<0; case LE -> res<=0; case GT -> res>0; case GE -> res>=0; default -> false;
                        };
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
                            slots = prevSlots; handlers = prevHandlers; pendingEx = prevPending; curFunc = prevFunc; cur = funcs[curFunc]; pc = prevPc; }
                    }
                    case NEG -> {
                        Object v = slots[a[0]]; Object r;
                        if(v instanceof Number n){ r = (v instanceof Integer || v instanceof Long)? -n.longValue() : -n.doubleValue(); }
                        else if(v instanceof String s){ try{ r = -Double.parseDouble(s);}catch(Exception ex){ r = 0; } }
                        else r = 0; slots[a[1]] = r;
                    }
                    case NOT -> { slots[a[1]] = !truthy(slots[a[0]]); }
                    case NEW_ARRAY -> {
                        Object sz = slots[a[0]]; int n = (sz instanceof Number)? ((Number)sz).intValue() : 0;
                        if(n < 0) n = 0; if(n > 1_000_000) n = 1_000_000;
                        Object[] arr = new Object[n]; slots[a[1]] = arr;
                    }
                    case LOAD_ELEM -> {
                        Object arrObj = slots[a[0]]; Object idxObj = slots[a[1]]; Object val = null;
                        if(arrObj instanceof Object[] arr && idxObj instanceof Number){ int i = ((Number)idxObj).intValue(); if(i>=0 && i<arr.length) val = arr[i]; }
                        slots[a[2]] = val;
                    }
                    case STORE_ELEM -> {
                        Object arrObj = slots[a[0]]; Object idxObj = slots[a[1]];
                        if(arrObj instanceof Object[] arr && idxObj instanceof Number){ int i = ((Number)idxObj).intValue(); if(i>=0 && i<arr.length) arr[i] = slots[a[2]]; }
                    }
                    case ARRAY_LENGTH -> {
                        Object arrObj = slots[a[0]]; int len = (arrObj instanceof Object[] arr)? arr.length : 0; slots[a[1]] = (long) len;
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
                        int callee = a[0]; if(callee < 0 || callee >= fnCount){ pc++; continue; }
                        // Save current state; next instruction will resume after call returns
                        stackFunc.push(curFunc); stackPc.push(pc+1); stackSlots.push(slots); stackRetDest.push(a[5]);
                        stackHandlers.push(handlers); stackPendingEx.push(pendingEx==null? NO_EXCEPTION : pendingEx);
                        // Switch to callee
                        curFunc = callee; cur = funcs[curFunc]; pc = 0; slots = new Object[256];
                        handlers = new java.util.ArrayDeque<>(); pendingEx = null;
                        // args: a1..a4
                        int[] src = new int[]{a[1],a[2],a[3],a[4]}; for(int i=0;i<4;i++){ if(src[i]>=0){ slots[i] = ((Object[])stackSlots.peek())[src[i]]; } }
                        continue;
                    }
                    case TRY_PUSH -> { handlers.push(new Handler(a[0], (String) cp[a[1]])); }
                    case TRY_POP -> { if(!handlers.isEmpty()) handlers.pop(); }
                    case THROW -> { pendingEx = slots[a[0]]; }
                    case CATCH_BIND -> { slots[a[0]] = pendingEx; pendingEx = null; }
                }
                pc++;
            }
        }catch(IOException e){ throw new RuntimeException(e); }
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

    private static int compare(Object a, Object b){
        if(a==b) return 0; if(a==null) return -1; if(b==null) return 1;
        if(a instanceof Number an && b instanceof Number bn){ double diff = an.doubleValue()-bn.doubleValue(); if(diff<0) return -1; if(diff>0) return 1; return 0; }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }
    private static boolean truthy(Object v){ if(v==null) return false; if(v instanceof Boolean b) return b; if(v instanceof Number n) return n.doubleValue()!=0.0; return true; }
}
