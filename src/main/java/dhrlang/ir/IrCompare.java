package dhrlang.ir;

public class IrCompare implements IrInstruction {
    public enum Op { EQ, NEQ, LT, LE, GT, GE }
    public final Op op; public final int leftSlot; public final int rightSlot; public final int targetSlot;
    public IrCompare(Op op, int leftSlot, int rightSlot, int targetSlot){ this.op=op; this.leftSlot=leftSlot; this.rightSlot=rightSlot; this.targetSlot=targetSlot; }
    public String toString(){ return op+" s"+targetSlot+"=s"+leftSlot+opSymbol()+"s"+rightSlot; }
    private String opSymbol(){ return switch(op){ case EQ->"=="; case NEQ->"!="; case LT->"<"; case LE->"<="; case GT->">"; case GE->">="; }; }
}
