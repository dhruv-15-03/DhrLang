package dhrlang.ir;

/** Simple field metadata for IR object allocation/default initialization. */
public class IrFieldDef {
    public final String name;
    public final String type;

    public IrFieldDef(String name, String type) {
        this.name = name;
        this.type = type;
    }
}