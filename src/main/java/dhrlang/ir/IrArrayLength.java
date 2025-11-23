package dhrlang.ir;

/** Compute array length: target = array.length */
public class IrArrayLength implements IrInstruction {
    public final int arraySlot;
    public final int targetSlot;

    public IrArrayLength(int arraySlot, int targetSlot){
        this.arraySlot = arraySlot;
        this.targetSlot = targetSlot;
    }
}
