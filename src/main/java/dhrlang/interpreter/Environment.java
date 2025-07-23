package dhrlang.interpreter;

import dhrlang.error.ErrorFactory;
import java.util.HashMap;
import java.util.Map;

public class Environment {

    private final Map<String, Object> values = new HashMap<>();
    private final Environment parent;

    public Environment() {
        this.parent = null;
    }

    public Environment(Environment parent) {
        this.parent = parent;
    }

    public void define(String name, Object value) {
        values.put(name, value);
    }

    public Object get(String name) {
        if (values.containsKey(name)) {
            return values.get(name);
        } else if (parent != null) {
            return parent.get(name);
        } else {
            // Check if this looks like a generic type reference
            String errorMessage = "Undefined variable '" + name + "'.";
            String hint = "Did you mean to access it with 'this." + name + "'?";
            
            if (name.contains("<") && name.contains(">")) {
                errorMessage = "Cannot use generic type '" + name + "' as a variable.";
                hint = "Generic types like '" + name + "' are used for type declarations, not as variables. " +
                       "Use 'new " + name + "(...)' to create an instance.";
            }
            
            throw ErrorFactory.accessError(errorMessage + " " + hint, (dhrlang.error.SourceLocation) null);
        }
    }

    public void assign(String name, Object value) {
        if (values.containsKey(name)) {
            values.put(name, value);
        } else if (parent != null) {
            parent.assign(name, value);
        } else {
            // Check if this looks like a generic type reference
            String errorMessage = "Cannot assign to undefined variable '" + name + "'.";
            String hint = "Did you mean to access it with 'this." + name + "'?";
            
            if (name.contains("<") && name.contains(">")) {
                errorMessage = "Cannot assign to generic type '" + name + "'.";
                hint = "Generic types like '" + name + "' are type declarations, not variables. " +
                       "Declare a variable: " + name + " myVar = new " + name + "(...);";
            }
            
            throw ErrorFactory.accessError(errorMessage + " " + hint, (dhrlang.error.SourceLocation) null);
        }
    }

    public boolean exists(String name) {
        return values.containsKey(name) || (parent != null && parent.exists(name));
    }
}
