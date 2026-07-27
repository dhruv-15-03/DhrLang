package dhrlang.ir;

/** Bind the current pending exception value into a local slot at catch entry. */
public class IrCatchBind implements IrInstruction {
    public final int targetSlot;
    public IrCatchBind(int targetSlot){ this.targetSlot = targetSlot; }
    @Override public String toString(){ return "CATCH_BIND s"+targetSlot; }
}
