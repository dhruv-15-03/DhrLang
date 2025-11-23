package dhrlang.ir;

/** Read an instance field: objectSlot.fieldName -> targetSlot */
public class IrGetField implements IrInstruction {
    public final int objectSlot;
    public final String fieldName;
    public final int targetSlot;
    public IrGetField(int objectSlot, String fieldName, int targetSlot){
        this.objectSlot = objectSlot; this.fieldName = fieldName; this.targetSlot = targetSlot;
    }
    @Override public String toString(){ return "GET_FIELD s"+objectSlot+"."+fieldName+" -> s"+targetSlot; }
}
