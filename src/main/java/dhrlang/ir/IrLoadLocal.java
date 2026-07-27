package dhrlang.ir;

public class IrLoadLocal implements IrInstruction {
    public final int slot;
    public final int targetSlot;
    public IrLoadLocal(int slot, int targetSlot){ this.slot=slot; this.targetSlot=targetSlot; }
    public String toString(){ return "LOAD_LOCAL s"+targetSlot+"=s"+slot; }
}
