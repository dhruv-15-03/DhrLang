package dhrlang.ir;

/** Load array element: target = array[index] */
public class IrLoadElement implements IrInstruction {
    public final int arraySlot;
    public final int indexSlot;
    public final int targetSlot;

    public IrLoadElement(int arraySlot, int indexSlot, int targetSlot){
        this.arraySlot = arraySlot;
        this.indexSlot = indexSlot;
        this.targetSlot = targetSlot;
    }
}
