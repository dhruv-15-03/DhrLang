package dhrlang.ir;

public class IrStoreLocal implements IrInstruction {
    public final int sourceSlot;
    public final int destSlot;
    public IrStoreLocal(int sourceSlot, int destSlot){ this.sourceSlot=sourceSlot; this.destSlot=destSlot; }
    public String toString(){ return "STORE_LOCAL s"+destSlot+"=s"+sourceSlot; }
}
