package dhrlang.ir;

import dhrlang.error.ErrorFactory;

import java.util.HashMap;
import java.util.Map;

/** Executes the IR including arrays and basic function calls. */
public class IrInterpreter {
    // Exception handler descriptor (pc within frame and optional catch type)
    private static class Handler {
        final int pc; final String type;
        Handler(int pc, String type){ this.pc = pc; this.type = type; }
    }
    private static class Frame {
        final IrFunction fn;
        final Object[] slots = new Object[256];
        final Map<String,Integer> labelPc;
        int pc = 0;
        Integer retDestSlot; // slot in caller to receive return value; null means ignore; -1 treated as ignore
        // Exception handling: stack of catch PCs (within this frame)
        final java.util.Deque<Handler> handlerStack = new java.util.ArrayDeque<>();
        Object pendingException; // exception value to bind at catch
        Frame(IrFunction fn){
            this.fn = fn;
            this.labelPc = new HashMap<>();
            for(int i=0;i<fn.instructions.size();i++){
                if(fn.instructions.get(i) instanceof IrLabel lab){ labelPc.put(lab.name, i); }
            }
        }
    }

    public void execute(IrProgram program){
        if(program.functions.isEmpty()) return;
        // Build function table by name
        Map<String, IrFunction> fnTable = new HashMap<>();
        for(IrFunction f: program.functions){ fnTable.put(f.name, f); }
        // Very simple static storage: className -> (fieldName -> value)
        Map<String, java.util.Map<String,Object>> statics = new HashMap<>();

        int safetyCounter = 0;
        int maxSteps = Integer.getInteger("dhrlang.backend.maxSteps", 50_000_000);

        // Start at entrypoint (prefer Main.main, otherwise first *.main)
        IrFunction entry = findEntryFunction(program);
        java.util.Deque<Frame> callStack = new java.util.ArrayDeque<>();
        callStack.push(new Frame(entry));

        Object bubblingException = null; // cross-frame exception being unwound
        while(!callStack.isEmpty()){
            Frame frame = callStack.peek();
            if(frame.pc < 0 || frame.pc >= frame.fn.instructions.size()){
                // Implicit return
                callStack.pop();
                // If returning while an exception is bubbling, continue unwinding
                continue;
            }
            if(++safetyCounter > maxSteps){
                throw ErrorFactory.runtimeError("Execution aborted: exceeded max instruction steps ("+maxSteps+") - possible infinite loop.", (dhrlang.error.SourceLocation) null);
            }
            IrInstruction ins = frame.fn.instructions.get(frame.pc);
            boolean advance = true;
            // If an exception is bubbling, attempt to transfer to handler in this frame
            if(bubblingException != null){
                if(!frame.handlerStack.isEmpty()){
                    // Match by type filter if any
                    Handler target = null;
                    java.util.Iterator<Handler> it = frame.handlerStack.iterator();
                    while(it.hasNext()){
                        Handler h = it.next();
                        if(matchesCatch(h.type, bubblingException)) { target = h; it.remove(); break; }
                    }
                    if(target!=null){
                        frame.pendingException = bubblingException;
                        bubblingException = null;
                        frame.pc = target.pc;
                        advance = false;
                    } else {
                        // no matching handler in this frame
                        callStack.pop();
                        continue;
                    }
                } else {
                    // No handler here, pop frame and continue unwinding
                    callStack.pop();
                    continue;
                }
            } else
            if(ins instanceof IrConst c){
                frame.slots[c.targetSlot] = c.value;
            } else if(ins instanceof IrLoadLocal ll){
                frame.slots[ll.targetSlot] = frame.slots[ll.slot];
            } else if(ins instanceof IrStoreLocal sl){
                frame.slots[sl.destSlot] = frame.slots[sl.sourceSlot];
            } else if(ins instanceof IrBinOp b){
                Object left = frame.slots[b.leftSlot];
                Object right = frame.slots[b.rightSlot];
                switch(b.op){
                    case ADD -> {
                        if(left instanceof String || right instanceof String){
                            frame.slots[b.targetSlot] = String.valueOf(left) + String.valueOf(right);
                        } else if(left instanceof Number && right instanceof Number){
                            if(left instanceof Double || right instanceof Double){
                                frame.slots[b.targetSlot] = ((Number)left).doubleValue() + ((Number)right).doubleValue();
                            } else {
                                frame.slots[b.targetSlot] = ((Number)left).longValue() + ((Number)right).longValue();
                            }
                        } else {
                            throw ErrorFactory.typeError("Operands for '+' must be two numbers or at least one string for concatenation.", (dhrlang.error.SourceLocation) null);
                        }
                    }
                    case SUB -> {
                        requireNumbers(left, right, "-");
                        if(left instanceof Double || right instanceof Double) frame.slots[b.targetSlot] = ((Number)left).doubleValue() - ((Number)right).doubleValue();
                        else frame.slots[b.targetSlot] = ((Number)left).longValue() - ((Number)right).longValue();
                    }
                    case MUL -> {
                        requireNumbers(left, right, "*");
                        if(left instanceof Double || right instanceof Double) frame.slots[b.targetSlot] = ((Number)left).doubleValue() * ((Number)right).doubleValue();
                        else frame.slots[b.targetSlot] = ((Number)left).longValue() * ((Number)right).longValue();
                    }
                    case DIV -> {
                        requireNumbers(left, right, "/");
                        double divisor = ((Number)right).doubleValue();
                        if(divisor==0.0) throw ErrorFactory.arithmeticError("Division by zero.", (dhrlang.error.SourceLocation) null);
                        frame.slots[b.targetSlot] = ((Number)left).doubleValue() / divisor;
                    }
                }
            } else if(ins instanceof IrCompare cmp){
                Object left = frame.slots[cmp.leftSlot];
                Object right = frame.slots[cmp.rightSlot];
                boolean bool;
                switch(cmp.op){
                    case EQ -> bool = java.util.Objects.equals(left, right);
                    case NEQ -> bool = !java.util.Objects.equals(left, right);
                    case LT, LE, GT, GE -> {
                        requireNumbers(left, right, cmp.op.name());
                        double ld = ((Number)left).doubleValue();
                        double rd = ((Number)right).doubleValue();
                        bool = switch(cmp.op){
                            case LT -> ld < rd;
                            case LE -> ld <= rd;
                            case GT -> ld > rd;
                            case GE -> ld >= rd;
                            default -> false;
                        };
                    }
                    default -> bool = false;
                }
                frame.slots[cmp.targetSlot] = bool;
            } else if(ins instanceof IrJump j){
                Integer dest = frame.labelPc.get(j.label);
                frame.pc = dest==null? frame.pc : dest;
                advance = false;
            } else if(ins instanceof IrJumpIfFalse jf){
                Object v = frame.slots[jf.condSlot];
                boolean isFalse = (v==null) || (v instanceof Boolean b && !b);
                if(isFalse){
                    Integer dest = frame.labelPc.get(jf.label);
                    frame.pc = dest==null? frame.pc : dest;
                    advance = false;
                }
            } else if(ins instanceof IrLabel){
                // no-op
            } else if(ins instanceof IrPrint p){
                Object v = frame.slots[p.slot];
                if(p.newline) System.out.println(String.valueOf(v)); else System.out.print(String.valueOf(v));
            } else if(ins instanceof IrUnaryOp u){
                Object v = frame.slots[u.sourceSlot];
                Object result;
                switch(u.op){
                    case NEG -> {
                        if(v instanceof Long l) result = -l;
                        else if(v instanceof Integer i) result = -i.longValue();
                        else if(v instanceof Double d) result = -d;
                        else throw ErrorFactory.typeError("Operand for '-' must be a number.", (dhrlang.error.SourceLocation) null);
                    }
                    case NOT -> {
                        result = !isTruthy(v);
                    }
                    default -> result = null;
                }
                frame.slots[u.targetSlot] = result;
            } else if(ins instanceof IrNewArray na){
                Object sz = frame.slots[na.sizeSlot];
                if(!(sz instanceof Long) && !(sz instanceof Integer)) throw ErrorFactory.typeError("Array size must be a number.", (dhrlang.error.SourceLocation) null);
                int n = ((Number)sz).intValue();
                if(n < 0) throw ErrorFactory.validationError("Array size cannot be negative.", (dhrlang.error.SourceLocation) null);
                if(n > 1_000_000) throw ErrorFactory.validationError("Array size too large (max: 1,000,000).", (dhrlang.error.SourceLocation) null);
                Object[] arr = new Object[n];
                Object def = dhrlang.runtime.RuntimeDefaults.getDefaultValue(na.elementType);
                if(def != null) java.util.Arrays.fill(arr, def);
                frame.slots[na.targetSlot] = arr;
            } else if(ins instanceof IrLoadElement le){
                Object arrObj = frame.slots[le.arraySlot];
                Object idxObj = frame.slots[le.indexSlot];
                if(!(arrObj instanceof Object[] arr)) throw ErrorFactory.typeError("Can only index arrays.", (dhrlang.error.SourceLocation) null);
                if(!(idxObj instanceof Long) && !(idxObj instanceof Integer)) throw ErrorFactory.typeError("Array index must be a number.", (dhrlang.error.SourceLocation) null);
                int i = ((Number)idxObj).intValue();
                if(i<0 || i>=arr.length) throw ErrorFactory.indexError("Array index "+i+" out of bounds for array of length "+arr.length+".", (dhrlang.error.SourceLocation) null);
                frame.slots[le.targetSlot] = arr[i];
            } else if(ins instanceof IrStoreElement se){
                Object arrObj = frame.slots[se.arraySlot];
                Object idxObj = frame.slots[se.indexSlot];
                if(!(arrObj instanceof Object[] arr)) throw ErrorFactory.typeError("Can only assign to array elements.", (dhrlang.error.SourceLocation) null);
                if(!(idxObj instanceof Long) && !(idxObj instanceof Integer)) throw ErrorFactory.typeError("Array index must be a number.", (dhrlang.error.SourceLocation) null);
                int i = ((Number)idxObj).intValue();
                if(i<0 || i>=arr.length) throw ErrorFactory.indexError("Array index "+i+" out of bounds for array of length "+arr.length+".", (dhrlang.error.SourceLocation) null);
                arr[i] = frame.slots[se.valueSlot];
            } else if(ins instanceof IrArrayLength al){
                Object arrObj = frame.slots[al.arraySlot];
                if(!(arrObj instanceof Object[] a)) throw ErrorFactory.typeError("Can only call arrayLength on arrays.", (dhrlang.error.SourceLocation) null);
                frame.slots[al.targetSlot] = (long) a.length;
            } else if(ins instanceof IrGetStatic gsf){
                java.util.Map<String,Object> map = statics.computeIfAbsent(gsf.className, k-> new HashMap<>());
                frame.slots[gsf.targetSlot] = map.get(gsf.fieldName);
            } else if(ins instanceof IrSetStatic ssf){
                java.util.Map<String,Object> map = statics.computeIfAbsent(ssf.className, k-> new HashMap<>());
                map.put(ssf.fieldName, frame.slots[ssf.valueSlot]);
            } else if(ins instanceof IrGetField gf){
                Object obj = frame.slots[gf.objectSlot];
                Object val = null;
                if(obj instanceof java.util.Map<?,?> m){ val = ((java.util.Map<?,?>)m).get(gf.fieldName); }
                frame.slots[gf.targetSlot] = val;
            } else if(ins instanceof IrSetField sf){
                Object obj = frame.slots[sf.objectSlot];
                if(obj instanceof java.util.Map<?,?>){
                    @SuppressWarnings("unchecked")
                    java.util.Map<Object,Object> m = (java.util.Map<Object,Object>) obj;
                    m.put(sf.fieldName, frame.slots[sf.valueSlot]);
                }
            } else if(ins instanceof IrTryPush tp){
                Integer dest = frame.labelPc.get(tp.catchLabel);
                if(dest!=null) frame.handlerStack.push(new Handler(dest, tp.catchType));
            } else if(ins instanceof IrTryPop){
                if(!frame.handlerStack.isEmpty()) frame.handlerStack.pop();
            } else if(ins instanceof IrCatchBind cb){
                frame.slots[cb.targetSlot] = frame.pendingException;
                frame.pendingException = null;
            } else if(ins instanceof IrThrow thr){
                Object ex = frame.slots[thr.valueSlot];
                // Begin unwinding: find nearest handler in current or outer frames
                // Try to match in current frame stack first
                if(!frame.handlerStack.isEmpty()){
                    Handler target = null;
                    java.util.Iterator<Handler> it = frame.handlerStack.iterator();
                    while(it.hasNext()){
                        Handler h = it.next();
                        if(matchesCatch(h.type, ex)) { target = h; it.remove(); break; }
                    }
                    if(target!=null){
                        frame.pendingException = ex;
                        frame.pc = target.pc;
                        advance = false;
                    } else {
                        bubblingException = ex;
                        callStack.pop();
                        continue;
                    }
                } else {
                    // bubble to caller frames
                    bubblingException = ex;
                    callStack.pop();
                    continue;
                }
            } else if(ins instanceof IrCall call){
                IrFunction callee = fnTable.get(call.functionName);
                if(callee==null){
                    // Unknown function: set null return (if any) and advance
                    if(call.destSlot>=0) frame.slots[call.destSlot] = null;
                } else {
                    Frame newFrame = new Frame(callee);
                    // Pass args into slots 0..k-1
                    for(int i=0;i<call.argSlots.length && i< newFrame.slots.length;i++){
                        int src = call.argSlots[i];
                        newFrame.slots[i] = (src>=0 && src<frame.slots.length) ? frame.slots[src] : null;
                    }
                    newFrame.retDestSlot = call.destSlot;
                    callStack.push(newFrame);
                    advance = false; // don't advance caller PC now; resume after return
                }
            } else if(ins instanceof IrReturn r){
                // Pop current frame and write return into caller dest if requested
                Object retVal = (r.slot==null)? null : frame.slots[r.slot];
                callStack.pop();
                if(!callStack.isEmpty()){
                    Frame caller = callStack.peek();
                    if(frame.retDestSlot!=null && frame.retDestSlot>=0){
                        caller.slots[frame.retDestSlot] = retVal;
                    }
                    // After returning, advance caller PC
                    caller.pc++;
                    continue;
                } else {
                    // Returned from entry function -> stop execution
                    return;
                }
            }
            if(advance){ frame.pc++; }
        }
    }

    private IrFunction findEntryFunction(IrProgram program){
        for(IrFunction f : program.functions){
            if("Main.main".equals(f.name)) return f;
        }
        for(IrFunction f : program.functions){
            if(f.name != null && f.name.endsWith(".main")) return f;
        }
        return program.functions.get(0);
    }

    private void requireNumbers(Object left, Object right, String op){
        if(left==null || right==null) throw ErrorFactory.typeError("Null operand for operator: "+op, (dhrlang.error.SourceLocation) null);
        if(!(left instanceof Number) || !(right instanceof Number)){
            throw ErrorFactory.typeError("Operands must be numbers for operator: "+op+".", (dhrlang.error.SourceLocation) null);
        }
    }

    private boolean isTruthy(Object v){
        if(v==null) return false;
        if(v instanceof Boolean b) return b;
        return true;
    }

    // Match semantics aligned with Evaluator.canCatch
    private boolean matchesCatch(String catchType, Object exceptionValue){
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
        // As IR doesn't carry category, restrict remaining types to DhrException umbrella
        return false;
    }
}
