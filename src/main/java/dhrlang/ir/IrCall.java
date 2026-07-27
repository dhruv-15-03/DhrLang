package dhrlang.ir;

import java.util.Arrays;

/** Call a function by qualified name with arbitrary args. Places return value into destSlot if >=0. */
public class IrCall implements IrInstruction {
    public final String functionName; // e.g., Main.main or Class.func
    public final int[] argSlots;
    public final int destSlot;        // -1 if void

    public IrCall(String functionName, int[] argSlots, int destSlot){
        this.functionName = functionName;
        this.argSlots = (argSlots==null? new int[0]: argSlots.clone());
        this.destSlot = destSlot;
    }

    @Override public String toString(){
        return "CALL " + functionName + " args=" + Arrays.toString(argSlots) + (destSlot>=0? (" -> "+destSlot):"");
    }
}
