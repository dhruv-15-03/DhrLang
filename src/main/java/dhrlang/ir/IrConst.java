package dhrlang.ir;

public class IrConst implements IrInstruction {
    public final int targetSlot;
    public final Object value; // primitive boxed or String (Phase 1 simplicity)
    public IrConst(int targetSlot, Object value){ this.targetSlot = targetSlot; this.value = value; }
    @Override public String toString(){ return "CONST s"+targetSlot+"="+value; }
}
