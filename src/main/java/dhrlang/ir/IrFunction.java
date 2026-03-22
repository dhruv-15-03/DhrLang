package dhrlang.ir;

import java.util.ArrayList;
import java.util.List;

/** Minimal function shell for IR backend scaffolding. */
public class IrFunction {
    public final String name;
    public final String ownerClassName;
    public final String simpleName;
    public final boolean isStatic;
    public final List<IrInstruction> instructions = new ArrayList<>();

    public IrFunction(String name){
        this(name, null, name, true);
    }

    public IrFunction(String name, String ownerClassName, String simpleName, boolean isStatic){
        this.name = name;
        this.ownerClassName = ownerClassName;
        this.simpleName = simpleName;
        this.isStatic = isStatic;
    }
}
