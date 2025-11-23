package dhrlang.ir;

public class IrJump implements IrInstruction {
    public final String label;
    public IrJump(String label){ this.label=label; }
    public String toString(){ return "JUMP @"+label; }
}
