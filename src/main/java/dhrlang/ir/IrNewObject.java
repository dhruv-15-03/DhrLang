package dhrlang.ir;

/** Allocate a new object instance of the given class. */
public class IrNewObject implements IrInstruction {
    public final String className;
    public final int targetSlot;

    public IrNewObject(String className, int targetSlot) {
        this.className = className;
        this.targetSlot = targetSlot;
    }

    @Override public String toString() {
        return "NEW_OBJECT " + className + " -> s" + targetSlot;
    }
}