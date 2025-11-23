package dhrlang.ir;

import java.util.ArrayList;
import java.util.List;

/** Minimal function shell for IR backend scaffolding. */
public class IrFunction {
    public final String name;
    public final List<IrInstruction> instructions = new ArrayList<>();
    public IrFunction(String name){ this.name = name; }
}
