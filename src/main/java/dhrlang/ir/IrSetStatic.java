package dhrlang.ir;

/** Write a static field: className.fieldName = valueSlot */
public class IrSetStatic implements IrInstruction {
    public final String className;
    public final String fieldName;
    public final int valueSlot;
    public IrSetStatic(String className, String fieldName, int valueSlot){
        this.className = className; this.fieldName = fieldName; this.valueSlot = valueSlot;
    }
    @Override public String toString(){ return "SET_STATIC " + className + "." + fieldName + " = s"+valueSlot; }
}
