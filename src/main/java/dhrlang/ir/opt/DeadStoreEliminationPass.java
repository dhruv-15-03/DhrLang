package dhrlang.ir.opt;

import dhrlang.ir.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dead store elimination: removes instructions that write to slots which are
 * never subsequently read. This is a simple backward pass that collects "used"
 * slots, then marks unused writes as dead.
 *
 * Conservative: keeps all instructions with side effects (print, call, throw,
 * field/static writes, try/catch, return, jumps, labels).
 */
public class DeadStoreEliminationPass implements IrPass {

    @Override
    public String name() {
        return "DeadStoreElimination";
    }

    @Override
    public void run(IrFunction fn) {
        List<IrInstruction> insns = fn.instructions;
        // Collect all slots that are read by any instruction
        Set<Integer> usedSlots = new HashSet<>();
        for (IrInstruction ins : insns) {
            collectReads(ins, usedSlots);
        }

        // Remove pure write-only instructions whose target is never read
        insns.removeIf(ins -> isDeadWrite(ins, usedSlots));
    }

    private boolean isDeadWrite(IrInstruction ins, Set<Integer> usedSlots) {
        // Only remove pure computations (no side effects) writing to unused slots
        if (ins instanceof IrConst c) return !usedSlots.contains(c.targetSlot);
        if (ins instanceof IrBinOp b) return !usedSlots.contains(b.targetSlot);
        if (ins instanceof IrUnaryOp u) return !usedSlots.contains(u.targetSlot);
        if (ins instanceof IrCompare cmp) return !usedSlots.contains(cmp.targetSlot);
        if (ins instanceof IrLoadLocal ll) return !usedSlots.contains(ll.targetSlot);
        // Don't remove StoreLocal — those may be writing to named locals used later
        // Don't remove anything with side effects
        return false;
    }

    private void collectReads(IrInstruction ins, Set<Integer> reads) {
        if (ins instanceof IrBinOp b) { reads.add(b.leftSlot); reads.add(b.rightSlot); }
        else if (ins instanceof IrUnaryOp u) { reads.add(u.sourceSlot); }
        else if (ins instanceof IrCompare cmp) { reads.add(cmp.leftSlot); reads.add(cmp.rightSlot); }
        else if (ins instanceof IrLoadLocal ll) { reads.add(ll.slot); }
        else if (ins instanceof IrStoreLocal sl) { reads.add(sl.sourceSlot); }
        else if (ins instanceof IrPrint p) { reads.add(p.slot); }
        else if (ins instanceof IrReturn r) { if (r.slot != null) reads.add(r.slot); }
        else if (ins instanceof IrJumpIfFalse jf) { reads.add(jf.condSlot); }
        else if (ins instanceof IrSetStatic ss) { reads.add(ss.valueSlot); }
        else if (ins instanceof IrSetField sf) { reads.add(sf.objectSlot); reads.add(sf.valueSlot); }
        else if (ins instanceof IrGetField gf) { reads.add(gf.objectSlot); }
        else if (ins instanceof IrGetStatic gs) { /* no reads */ }
        else if (ins instanceof IrNewArray na) { reads.add(na.sizeSlot); }
        else if (ins instanceof IrLoadElement le) { reads.add(le.arraySlot); reads.add(le.indexSlot); }
        else if (ins instanceof IrStoreElement se) { reads.add(se.arraySlot); reads.add(se.indexSlot); reads.add(se.valueSlot); }
        else if (ins instanceof IrArrayLength al) { reads.add(al.arraySlot); }
        else if (ins instanceof IrThrow thr) { reads.add(thr.valueSlot); }
        else if (ins instanceof IrCall c) { for (int s : c.argSlots) reads.add(s); }
        else if (ins instanceof IrCallNative c) { for (int s : c.argSlots) reads.add(s); }
        else if (ins instanceof IrInvokeMethod m) { reads.add(m.objectSlot); for (int s : m.argSlots) reads.add(s); }
        else if (ins instanceof IrNewObject no) { /* no reads */ }
    }
}
