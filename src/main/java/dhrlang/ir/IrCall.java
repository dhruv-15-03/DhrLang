package dhrlang.ir;

import java.util.Arrays;

/** Call a function by qualified name with up to 4 args. Places return value into destSlot if >=0. */
public class IrCall implements IrInstruction {
    public final String functionName; // e.g., Main.main or Class.func
    public final int[] argSlots;      // length <= 4
    public final int destSlot;        // -1 if void

    public IrCall(String functionName, int[] argSlots, int destSlot){
        this.functionName = functionName;
        this.argSlots = (argSlots==null? new int[0]: argSlots.clone());
        this.destSlot = destSlot;
        if(this.argSlots.length > 4) throw new IllegalArgumentException("IrCall supports up to 4 args");
    }

    @Override public String toString(){
        return "CALL " + functionName + " args=" + Arrays.toString(argSlots) + (destSlot>=0? (" -> "+destSlot):"");
    }
}
