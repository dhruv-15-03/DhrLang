package dhrlang.ir;

/** Read a static field: className.fieldName -> targetSlot */
public class IrGetStatic implements IrInstruction {
    public final String className;
    public final String fieldName;
    public final int targetSlot;
    public IrGetStatic(String className, String fieldName, int targetSlot){
        this.className = className; this.fieldName = fieldName; this.targetSlot = targetSlot;
    }
    @Override public String toString(){ return "GET_STATIC " + className + "." + fieldName + " -> s"+targetSlot; }
}
