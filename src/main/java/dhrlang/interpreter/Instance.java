package dhrlang.interpreter;

import dhrlang.lexer.Token;
import dhrlang.error.ErrorFactory;
import java.util.HashMap;
import java.util.Map;

public class Instance {
    private final DhrClass klass;
    private final Map<String, Object> fields = new HashMap<>();
    private String[] genericTypeArguments;

    public Instance(DhrClass klass) {
        this.klass = klass;
    }

    public DhrClass getKlass() {
        return this.klass;
    }
    
    public void setGenericTypeArguments(String[] typeArguments) {
        this.genericTypeArguments = typeArguments;
    }
    
    public String[] getGenericTypeArguments() {
        return this.genericTypeArguments;
    }
    
    public boolean isGenericInstance() {
        return this.genericTypeArguments != null && this.genericTypeArguments.length > 0;
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

        throw ErrorFactory.accessError("Undefined property '" + name.getLexeme() + "'.", name.getLocation());
    }

    @Override
    public String toString() {
        if (isGenericInstance()) {
            StringBuilder sb = new StringBuilder();
            sb.append(klass.name).append("<");
            for (int i = 0; i < genericTypeArguments.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(genericTypeArguments[i]);
            }
            sb.append("> instance");
            return sb.toString();
        }
        return klass.name + " instance";
    }
}