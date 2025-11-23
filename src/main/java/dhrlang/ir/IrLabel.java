package dhrlang.ir;

public class IrLabel implements IrInstruction {
    public final String name;
    public IrLabel(String name){ this.name=name; }
    public String toString(){ return name+":"; }
}
