package dhrlang.ir;

public class IrBinOp implements IrInstruction {
    public enum Op { ADD, SUB, MUL, DIV, MOD, BIT_AND, BIT_OR, BIT_XOR, LSHIFT, RSHIFT }
    public final Op op;
    public final int leftSlot;
    public final int rightSlot;
    public final int targetSlot;
    public IrBinOp(Op op, int leftSlot, int rightSlot, int targetSlot){
        this.op=op; this.leftSlot=leftSlot; this.rightSlot=rightSlot; this.targetSlot=targetSlot;
    }
    @Override public String toString(){ return op+" s"+targetSlot+"=s"+leftSlot+opSymbol()+"s"+rightSlot; }
    private String opSymbol(){ return switch(op){ case ADD->"+"; case SUB->"-"; case MUL->"*"; case DIV->"/"; case MOD->"%"; case BIT_AND->"&"; case BIT_OR->"|"; case BIT_XOR->"^"; case LSHIFT->"<<"; case RSHIFT->">>"; }; }
}
