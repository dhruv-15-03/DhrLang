package dhrlang.ir;

/** Allocate a new one-dimensional array with the given size. */
public class IrNewArray implements IrInstruction {
    public final int sizeSlot;   // slot holding array size (Long)
    public final int targetSlot; // slot to receive Object[]
    public final String elementType; // element type name (e.g., "num", "sab") for default fill; may be null

    public IrNewArray(int sizeSlot, int targetSlot, String elementType){
        this.sizeSlot = sizeSlot;
        this.targetSlot = targetSlot;
        this.elementType = elementType;
    }
}
