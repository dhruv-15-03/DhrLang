package dhrlang.ir;

/** Unary operation instruction (currently supports NEG and NOT). */
public class IrUnaryOp implements IrInstruction {
    public enum Op { NEG, NOT }
    public final Op op;
    public final int sourceSlot;
    public final int targetSlot;
    public IrUnaryOp(Op op, int sourceSlot, int targetSlot){
        this.op = op; this.sourceSlot = sourceSlot; this.targetSlot = targetSlot;
    }
    @Override public String toString(){ return op+" s"+targetSlot+"= " + opSymbol()+"s"+sourceSlot; }
    private String opSymbol(){ return switch(op){ case NEG -> "-"; case NOT -> "!"; }; }
}
