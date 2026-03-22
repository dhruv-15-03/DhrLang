package dhrlang.ir.opt;

import dhrlang.ir.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copy propagation: when a slot is just a copy of another slot (via IrLoadLocal
 * or IrStoreLocal), replace subsequent reads of the copy with the original.
 * This enables further dead store elimination.
 *
 * Operates conservatively: invalidates copies at labels and calls.
 */
public class CopyPropagationPass implements IrPass {

    @Override
    public String name() {
        return "CopyPropagation";
    }

    @Override
    public void run(IrFunction fn) {
        List<IrInstruction> insns = fn.instructions;
        // targetSlot -> sourceSlot: means targetSlot is a copy of sourceSlot
        Map<Integer, Integer> copies = new HashMap<>();

        for (int i = 0; i < insns.size(); i++) {
            IrInstruction ins = insns.get(i);

            if (ins instanceof IrLoadLocal ll) {
                copies.put(ll.targetSlot, resolve(copies, ll.slot));
            } else if (ins instanceof IrStoreLocal sl) {
                copies.put(sl.destSlot, resolve(copies, sl.sourceSlot));
            } else if (ins instanceof IrBinOp b) {
                int newLeft = resolve(copies, b.leftSlot);
                int newRight = resolve(copies, b.rightSlot);
                if (newLeft != b.leftSlot || newRight != b.rightSlot) {
                    insns.set(i, new IrBinOp(b.op, newLeft, newRight, b.targetSlot));
                }
                copies.remove(b.targetSlot);
            } else if (ins instanceof IrUnaryOp u) {
                int newSrc = resolve(copies, u.sourceSlot);
                if (newSrc != u.sourceSlot) {
                    insns.set(i, new IrUnaryOp(u.op, newSrc, u.targetSlot));
                }
                copies.remove(u.targetSlot);
            } else if (ins instanceof IrCompare cmp) {
                int newLeft = resolve(copies, cmp.leftSlot);
                int newRight = resolve(copies, cmp.rightSlot);
                if (newLeft != cmp.leftSlot || newRight != cmp.rightSlot) {
                    insns.set(i, new IrCompare(cmp.op, newLeft, newRight, cmp.targetSlot));
                }
                copies.remove(cmp.targetSlot);
            } else if (ins instanceof IrPrint p) {
                int newSlot = resolve(copies, p.slot);
                if (newSlot != p.slot) {
                    insns.set(i, new IrPrint(newSlot, p.newline));
                }
            } else if (ins instanceof IrReturn r) {
                if (r.slot != null) {
                    int newSlot = resolve(copies, r.slot);
                    if (newSlot != r.slot) {
                        insns.set(i, new IrReturn(newSlot));
                    }
                }
            } else if (ins instanceof IrJumpIfFalse jf) {
                int newCond = resolve(copies, jf.condSlot);
                if (newCond != jf.condSlot) {
                    insns.set(i, new IrJumpIfFalse(newCond, jf.label));
                }
            } else if (ins instanceof IrSetStatic ss) {
                int newVal = resolve(copies, ss.valueSlot);
                if (newVal != ss.valueSlot) {
                    insns.set(i, new IrSetStatic(ss.className, ss.fieldName, newVal));
                }
            } else if (ins instanceof IrSetField sf) {
                int newObj = resolve(copies, sf.objectSlot);
                int newVal = resolve(copies, sf.valueSlot);
                if (newObj != sf.objectSlot || newVal != sf.valueSlot) {
                    insns.set(i, new IrSetField(newObj, sf.fieldName, newVal));
                }
            } else if (ins instanceof IrLabel) {
                copies.clear();
            } else {
                // For any other instruction with a target slot, invalidate that slot
                invalidateTarget(ins, copies);
            }
        }
    }

    /** Follow the copy chain to find the root slot. */
    private int resolve(Map<Integer, Integer> copies, int slot) {
        int current = slot;
        int steps = 0;
        while (copies.containsKey(current) && steps < 100) {
            current = copies.get(current);
            steps++;
        }
        return current;
    }

    private void invalidateTarget(IrInstruction ins, Map<Integer, Integer> copies) {
        if (ins instanceof IrConst c) copies.remove(c.targetSlot);
        else if (ins instanceof IrCall c && c.destSlot >= 0) copies.remove(c.destSlot);
        else if (ins instanceof IrCallNative c && c.destSlot >= 0) copies.remove(c.destSlot);
        else if (ins instanceof IrInvokeMethod m && m.destSlot >= 0) copies.remove(m.destSlot);
        else if (ins instanceof IrGetField gf) copies.remove(gf.targetSlot);
        else if (ins instanceof IrGetStatic gs) copies.remove(gs.targetSlot);
        else if (ins instanceof IrNewObject no) copies.remove(no.targetSlot);
        else if (ins instanceof IrNewArray na) copies.remove(na.targetSlot);
        else if (ins instanceof IrLoadElement le) copies.remove(le.targetSlot);
        else if (ins instanceof IrArrayLength al) copies.remove(al.targetSlot);
        else if (ins instanceof IrCatchBind cb) copies.remove(cb.targetSlot);
    }
}
