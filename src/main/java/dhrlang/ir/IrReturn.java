package dhrlang.ir;

public class IrReturn implements IrInstruction {
    public final Integer slot; // nullable: null means void
    public IrReturn(Integer slot){ this.slot = slot; }
    @Override public String toString(){ return slot==null?"RETURN":"RETURN s"+slot; }
}
