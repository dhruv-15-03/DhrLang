package dhrlang.interpreter;

import dhrlang.lexer.Token;
import java.util.HashMap;
import java.util.Map;

public class Instance {
    private final DhrClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    public Instance(DhrClass klass) {
        this.klass = klass;
    }

    public DhrClass getKlass() {
        return this.klass;
    }

    public void set(Token name, Object value) {
        fields.put(name.getLexeme(), value);
    }

    public Object get(Token name) {
        if (fields.containsKey(name.getLexeme())) {
            return fields.get(name.getLexeme());
        }

        Function method = klass.findMethod(name.getLexeme());
        if (method != null) {
            return method.bind(this);
        }

        throw new RuntimeError("Undefined property '" + name.getLexeme() + "'.");
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }
}