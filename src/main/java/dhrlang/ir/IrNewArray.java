package dhrlang.ir;

/** Allocate a new one-dimensional array with the given size. */
public class IrNewArray implements IrInstruction {
    public final int sizeSlot;   // slot holding array size (Long)
    public final int targetSlot; // slot to receive Object[]

    public IrNewArray(int sizeSlot, int targetSlot){
        this.sizeSlot = sizeSlot;
        this.targetSlot = targetSlot;
    }
}
