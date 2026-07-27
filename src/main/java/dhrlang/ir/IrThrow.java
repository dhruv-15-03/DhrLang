package dhrlang.ir;

/** Throw an exception-like value from a slot. The interpreter/VM will transfer control to the nearest try handler. */
public class IrThrow implements IrInstruction {
    public final int valueSlot;
    public IrThrow(int valueSlot){ this.valueSlot = valueSlot; }
    @Override public String toString(){ return "THROW s"+valueSlot; }
}
