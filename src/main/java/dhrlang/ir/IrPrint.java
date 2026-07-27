package dhrlang.ir;

public class IrPrint implements IrInstruction {
    public final int slot; public final boolean newline;
    public IrPrint(int slot, boolean newline){ this.slot=slot; this.newline=newline; }
    public String toString(){ return (newline?"PRINTLN":"PRINT")+" s"+slot; }
}
