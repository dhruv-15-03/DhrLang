package dhrlang.ir;

import java.util.Arrays;

/** Invoke a registered native/stdlib function by name. */
public class IrCallNative implements IrInstruction {
    public final String functionName;
    public final int[] argSlots;
    public final int destSlot;

    public IrCallNative(String functionName, int[] argSlots, int destSlot) {
        this.functionName = functionName;
        this.argSlots = argSlots == null ? new int[0] : argSlots.clone();
        this.destSlot = destSlot;
    }

    @Override
    public String toString() {
        return "CALL_NATIVE " + functionName + " args=" + Arrays.toString(argSlots)
                + (destSlot >= 0 ? (" -> s" + destSlot) : "");
    }
}