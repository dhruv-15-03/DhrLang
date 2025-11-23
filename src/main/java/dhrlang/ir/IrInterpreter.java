package dhrlang.ir;

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
        int maxSteps = 100000; // crude safeguard against accidental infinite loops in IR backend

        // Start at first function (expected Main.main)
        java.util.Deque<Frame> callStack = new java.util.ArrayDeque<>();
        callStack.push(new Frame(program.functions.get(0)));

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
                System.err.println("[IR] Execution aborted: exceeded max instruction steps ("+maxSteps+") - possible infinite loop.");
                return;
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
                Object lv = frame.slots[b.leftSlot];
                Object rv = frame.slots[b.rightSlot];
                if(b.op== IrBinOp.Op.ADD && (!(lv instanceof Number) || !(rv instanceof Number))){
                    frame.slots[b.targetSlot] = String.valueOf(lv) + String.valueOf(rv);
                } else {
                    Number l = lv instanceof Number ? (Number) lv : 0;
                    Number r = rv instanceof Number ? (Number) rv : 0;
                    double v = switch(b.op){
                        case ADD -> l.doubleValue()+r.doubleValue();
                        case SUB -> l.doubleValue()-r.doubleValue();
                        case MUL -> l.doubleValue()*r.doubleValue();
                        case DIV -> r.doubleValue()==0?Double.NaN:l.doubleValue()/r.doubleValue();
                    };
                    if(l instanceof Integer || l instanceof Long){
                        if(r instanceof Integer || r instanceof Long){
                            long lvv=l.longValue(), rvv=r.longValue();
                            frame.slots[b.targetSlot] = switch(b.op){
                                case ADD -> lvv+rvv; case SUB -> lvv-rvv; case MUL -> lvv*rvv; case DIV -> (rvv==0?0: lvv/rvv);
                            }; advance = true; gotoAdvance(frame); continue; }
                    }
                    frame.slots[b.targetSlot] = v;
                }
            } else if(ins instanceof IrCompare cmp){
                Object lv = frame.slots[cmp.leftSlot];
                Object rv = frame.slots[cmp.rightSlot];
                int res = compareValues(lv, rv);
                boolean bool = switch(cmp.op){
                    case EQ -> res==0; case NEQ -> res!=0; case LT -> res<0; case LE -> res<=0; case GT -> res>0; case GE -> res>=0;
                };
                frame.slots[cmp.targetSlot] = bool;
            } else if(ins instanceof IrJump j){
                Integer dest = frame.labelPc.get(j.label);
                frame.pc = dest==null? frame.pc : dest;
                advance = false;
            } else if(ins instanceof IrJumpIfFalse jf){
                Object v = frame.slots[jf.condSlot];
                boolean isFalse = (v==null) || (v instanceof Boolean b && !b) || (v instanceof Number n && n.doubleValue()==0.0);
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
                        if(v instanceof Number n){
                            if(v instanceof Integer || v instanceof Long){
                                result = -((Number)v).longValue();
                            } else {
                                result = -n.doubleValue();
                            }
                        } else if(v instanceof String s){
                            try { result = -Double.parseDouble(s); }
                            catch(Exception ex){ result = 0; }
                        } else { result = 0; }
                    }
                    case NOT -> {
                        boolean bool = !isTruthy(v);
                        result = bool;
                    }
                    default -> result = null;
                }
                frame.slots[u.targetSlot] = result;
            } else if(ins instanceof IrNewArray na){
                Object sz = frame.slots[na.sizeSlot];
                int n = (sz instanceof Number)? ((Number)sz).intValue() : 0;
                if(n < 0) n = 0; if(n > 1_000_000) n = 1_000_000;
                Object[] arr = new Object[n];
                frame.slots[na.targetSlot] = arr;
            } else if(ins instanceof IrLoadElement le){
                Object arrObj = frame.slots[le.arraySlot];
                Object idxObj = frame.slots[le.indexSlot];
                Object val = null;
                if(arrObj instanceof Object[] arr && idxObj instanceof Number){
                    int i = ((Number)idxObj).intValue();
                    if(i >= 0 && i < arr.length) val = arr[i];
                }
                frame.slots[le.targetSlot] = val;
            } else if(ins instanceof IrStoreElement se){
                Object arrObj = frame.slots[se.arraySlot];
                Object idxObj = frame.slots[se.indexSlot];
                if(arrObj instanceof Object[] arr && idxObj instanceof Number){
                    int i = ((Number)idxObj).intValue();
                    if(i >= 0 && i < arr.length){ arr[i] = frame.slots[se.valueSlot]; }
                }
            } else if(ins instanceof IrArrayLength al){
                Object arrObj = frame.slots[al.arraySlot];
                int len = (arrObj instanceof Object[] a) ? a.length : 0;
                frame.slots[al.targetSlot] = (long) len;
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

    private int compareValues(Object a, Object b){
        if(a==b) return 0;
        if(a==null) return -1;
        if(b==null) return 1;
        if(a instanceof Number an && b instanceof Number bn){
            double diff = an.doubleValue()-bn.doubleValue();
            if(diff<0) return -1; if(diff>0) return 1; return 0;
        }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    private boolean isTruthy(Object v){
        if(v==null) return false; if(v instanceof Boolean b) return b; if(v instanceof Number n) return n.doubleValue()!=0.0; return true;
    }

    // Small helper to satisfy earlier continue in transformed code path.
    private void gotoAdvance(Frame frame){ frame.pc++; }

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
