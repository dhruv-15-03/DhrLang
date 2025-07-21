package dhrlang.typechecker;

import java.util.HashMap;
import java.util.Map;

public class TypeEnvironment {
    private final Map<String, String> variables = new HashMap<>();
    private final Map<String, FunctionSignature> functions = new HashMap<>();
    private final TypeEnvironment parent;

    public TypeEnvironment() {
        this.parent = null;
    }

    public TypeEnvironment(TypeEnvironment parent) {
        this.parent = parent;
    }


    public void define(String name, String type) {
        variables.put(name, type);
    }

    public void defineFunction(String name, FunctionSignature signature) {
        functions.put(name, signature);
    }

    public Map<String, String> getAllFields() {
        Map<String, String> allFields = new HashMap<>();
        if (parent != null) {
            allFields.putAll(parent.getAllFields());
        }
        allFields.putAll(this.variables);
        return allFields;
    }

    public Map<String, FunctionSignature> getAllFunctions() {
        Map<String, FunctionSignature> allFunctions = new HashMap<>();
        if (parent != null) {
            allFunctions.putAll(parent.getAllFunctions());
        }
        allFunctions.putAll(this.functions);
        return allFunctions;
    }

    public String get(String name) {
        if (variables.containsKey(name)) return variables.get(name);
        if (parent != null) return parent.get(name);
        throw new TypeException("Undefined variable '" + name + "'");
    }

    public FunctionSignature getFunction(String name) {
        if (functions.containsKey(name)) return functions.get(name);
        if (parent != null) return parent.getFunction(name);
        throw new TypeException("Undefined function '" + name + "'");
    }
    public boolean exists(String name) {
        if (variables.containsKey(name)) {
            return true;
        }

        if (parent != null) {
            return parent.exists(name);
        }

        return false;
    }
    public Map<String, String> getLocalFields() {
        return this.variables;
    }

    public Map<String, FunctionSignature> getLocalFunctions() {
        return this.functions;
    }
}
