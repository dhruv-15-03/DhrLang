package dhrlang.ir;

import java.util.ArrayList;
import java.util.List;

/** Metadata describing a lowered class for the IR runtime. */
public class IrClassDef {
    public final String name;
    public final String superclassName;
    public final List<IrFieldDef> instanceFields = new ArrayList<>();

    public IrClassDef(String name, String superclassName) {
        this.name = name;
        this.superclassName = superclassName;
    }
}