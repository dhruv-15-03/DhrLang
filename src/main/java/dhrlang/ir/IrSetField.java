package dhrlang.ir;

/** Write an instance field: objectSlot.fieldName = valueSlot */
public class IrSetField implements IrInstruction {
    public final int objectSlot;
    public final String fieldName;
    public final int valueSlot;
    public IrSetField(int objectSlot, String fieldName, int valueSlot){
        this.objectSlot = objectSlot; this.fieldName = fieldName; this.valueSlot = valueSlot;
    }
    @Override public String toString(){ return "SET_FIELD s"+objectSlot+"."+fieldName+" = s"+valueSlot; }
}
