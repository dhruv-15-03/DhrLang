package dhrlang.ir;

/** Store array element: array[index] = value */
public class IrStoreElement implements IrInstruction {
    public final int arraySlot;
    public final int indexSlot;
    public final int valueSlot;

    public IrStoreElement(int arraySlot, int indexSlot, int valueSlot){
        this.arraySlot = arraySlot;
        this.indexSlot = indexSlot;
        this.valueSlot = valueSlot;
    }
}
