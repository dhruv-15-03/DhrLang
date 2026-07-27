package dhrlang.ir;

/** Placeholder builder; later will traverse AST and emit instructions. */
public class IrBuilder {
    public IrProgram buildStubProgram() {
        IrProgram p = new IrProgram();
        IrFunction f = new IrFunction("<stub>");
        p.functions.add(f);
        return p;
    }
}
