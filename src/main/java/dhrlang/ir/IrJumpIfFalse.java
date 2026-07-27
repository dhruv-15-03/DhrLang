package dhrlang.ir;

public class IrJumpIfFalse implements IrInstruction {
    public final int condSlot; public final String label;
    public IrJumpIfFalse(int condSlot, String label){ this.condSlot=condSlot; this.label=label; }
    public String toString(){ return "JUMP_IF_FALSE s"+condSlot+" -> @"+label; }
}
