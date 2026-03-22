package dhrlang.ir.opt;

import dhrlang.ir.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constant folding: when both operands of a binary/unary/compare op are known
 * constants, replace the op with a single IrConst holding the computed result.
 *
 * Tracks which slots hold known constants (from IrConst and IrStoreLocal of
 * constants). Invalidates knowledge at control flow merge points (labels).
 */
public class ConstantFoldingPass implements IrPass {

    @Override
    public String name() {
        return "ConstantFolding";
    }

    @Override
    public void run(IrFunction fn) {
        List<IrInstruction> insns = fn.instructions;
        // slot -> known constant value (null key means "not constant")
        Map<Integer, Object> constants = new HashMap<>();

        for (int i = 0; i < insns.size(); i++) {
            IrInstruction ins = insns.get(i);

            if (ins instanceof IrConst c) {
                constants.put(c.targetSlot, c.value);
            } else if (ins instanceof IrStoreLocal sl) {
                Object val = constants.get(sl.sourceSlot);
                if (val != null) {
                    constants.put(sl.destSlot, val);
                } else {
                    constants.remove(sl.destSlot);
                }
            } else if (ins instanceof IrLoadLocal ll) {
                Object val = constants.get(ll.slot);
                if (val != null) {
                    constants.put(ll.targetSlot, val);
                } else {
                    constants.remove(ll.targetSlot);
                }
            } else if (ins instanceof IrBinOp b) {
                Object left = constants.get(b.leftSlot);
                Object right = constants.get(b.rightSlot);
                if (left instanceof Number nl && right instanceof Number nr) {
                    Object result = foldBinOp(b.op, nl, nr);
                    if (result != null) {
                        insns.set(i, new IrConst(b.targetSlot, result));
                        constants.put(b.targetSlot, result);
                        continue;
                    }
                }
                // String concatenation
                if (b.op == IrBinOp.Op.ADD && left instanceof String && right instanceof String) {
                    String result = (String) left + (String) right;
                    insns.set(i, new IrConst(b.targetSlot, result));
                    constants.put(b.targetSlot, result);
                    continue;
                }
                constants.remove(b.targetSlot);
            } else if (ins instanceof IrUnaryOp u) {
                Object val = constants.get(u.sourceSlot);
                if (val != null) {
                    Object result = foldUnaryOp(u.op, val);
                    if (result != null) {
                        insns.set(i, new IrConst(u.targetSlot, result));
                        constants.put(u.targetSlot, result);
                        continue;
                    }
                }
                constants.remove(u.targetSlot);
            } else if (ins instanceof IrCompare cmp) {
                Object left = constants.get(cmp.leftSlot);
                Object right = constants.get(cmp.rightSlot);
                if (left != null && right != null) {
                    Object result = foldCompare(cmp.op, left, right);
                    if (result != null) {
                        insns.set(i, new IrConst(cmp.targetSlot, result));
                        constants.put(cmp.targetSlot, result);
                        continue;
                    }
                }
                constants.remove(cmp.targetSlot);
            } else if (ins instanceof IrLabel) {
                // Control flow merge point — invalidate all constant knowledge
                constants.clear();
            } else {
                // Any other instruction that writes to a slot invalidates it
                invalidateTarget(ins, constants);
            }
        }
    }

    private Object foldBinOp(IrBinOp.Op op, Number left, Number right) {
        boolean isDouble = left instanceof Double || right instanceof Double;
        return switch (op) {
            case ADD -> {
                if (isDouble) yield (Object) (left.doubleValue() + right.doubleValue());
                yield (Object) (left.longValue() + right.longValue());
            }
            case SUB -> {
                if (isDouble) yield (Object) (left.doubleValue() - right.doubleValue());
                yield (Object) (left.longValue() - right.longValue());
            }
            case MUL -> {
                if (isDouble) yield (Object) (left.doubleValue() * right.doubleValue());
                yield (Object) (left.longValue() * right.longValue());
            }
            case DIV -> {
                double divisor = right.doubleValue();
                if (divisor == 0.0) yield null; // don't fold division by zero
                yield (Object) (left.doubleValue() / divisor);
            }
            case MOD -> {
                double divisor = right.doubleValue();
                if (divisor == 0.0) yield null;
                if (isDouble) yield (Object) (left.doubleValue() % divisor);
                yield (Object) (left.longValue() % right.longValue());
            }
            case BIT_AND -> { yield (Object) (left.longValue() & right.longValue()); }
            case BIT_OR -> { yield (Object) (left.longValue() | right.longValue()); }
            case BIT_XOR -> { yield (Object) (left.longValue() ^ right.longValue()); }
            case LSHIFT -> { yield (Object) (left.longValue() << right.longValue()); }
            case RSHIFT -> { yield (Object) (left.longValue() >> right.longValue()); }
        };
    }

    private Object foldUnaryOp(IrUnaryOp.Op op, Object val) {
        return switch (op) {
            case NEG -> {
                if (val instanceof Long l) yield -l;
                if (val instanceof Double d) yield -d;
                yield null;
            }
            case NOT -> {
                if (val instanceof Boolean b) yield !b;
                yield null;
            }
            case BIT_NOT -> {
                if (val instanceof Long l) yield ~l;
                yield null;
            }
        };
    }

    private Object foldCompare(IrCompare.Op op, Object left, Object right) {
        if (op == IrCompare.Op.EQ) return java.util.Objects.equals(left, right);
        if (op == IrCompare.Op.NEQ) return !java.util.Objects.equals(left, right);
        if (left instanceof Number nl && right instanceof Number nr) {
            double ld = nl.doubleValue();
            double rd = nr.doubleValue();
            return switch (op) {
                case LT -> ld < rd;
                case LE -> ld <= rd;
                case GT -> ld > rd;
                case GE -> ld >= rd;
                default -> null;
            };
        }
        return null;
    }

    private void invalidateTarget(IrInstruction ins, Map<Integer, Object> constants) {
        // Remove constant tracking for any slot this instruction writes to
        if (ins instanceof IrCall c && c.destSlot >= 0) constants.remove(c.destSlot);
        else if (ins instanceof IrCallNative c && c.destSlot >= 0) constants.remove(c.destSlot);
        else if (ins instanceof IrInvokeMethod m && m.destSlot >= 0) constants.remove(m.destSlot);
        else if (ins instanceof IrGetField gf) constants.remove(gf.targetSlot);
        else if (ins instanceof IrGetStatic gs) constants.remove(gs.targetSlot);
        else if (ins instanceof IrNewObject no) constants.remove(no.targetSlot);
        else if (ins instanceof IrNewArray na) constants.remove(na.targetSlot);
        else if (ins instanceof IrLoadElement le) constants.remove(le.targetSlot);
        else if (ins instanceof IrArrayLength al) constants.remove(al.targetSlot);
        else if (ins instanceof IrCatchBind cb) constants.remove(cb.targetSlot);
    }
}
