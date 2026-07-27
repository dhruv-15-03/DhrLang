package dhrlang.ir;

import java.util.Arrays;

/** Invoke an instance method on an object slot using runtime dispatch. */
public class IrInvokeMethod implements IrInstruction {
    public final int objectSlot;
    public final String methodName;
    public final int[] argSlots;
    public final int destSlot;

    public IrInvokeMethod(int objectSlot, String methodName, int[] argSlots, int destSlot) {
        this.objectSlot = objectSlot;
        this.methodName = methodName;
        this.argSlots = (argSlots == null) ? new int[0] : argSlots.clone();
        this.destSlot = destSlot;
    }

    @Override public String toString() {
        return "INVOKE s" + objectSlot + "." + methodName + " args=" + Arrays.toString(argSlots)
                + (destSlot >= 0 ? (" -> s" + destSlot) : "");
    }
}