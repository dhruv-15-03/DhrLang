package dhrlang.typechecker;

import java.util.HashMap;
import java.util.Map;

import dhrlang.error.SourceLocation;

public class TypeEnvironment {
    private final Map<String, TypeDesc> variables = new HashMap<>();
    private final Map<String, Boolean> variableUsed = new HashMap<>(); // false until referenced
    private final Map<String, dhrlang.error.SourceLocation> variableLocations = new HashMap<>();
    private final Map<String, FunctionSignature> functions = new HashMap<>();
    // Dead store tracking
    private final Map<String, Boolean> readSinceLastWrite = new HashMap<>();
    private final Map<String, SourceLocation> lastWriteLocation = new HashMap<>();
    private final TypeEnvironment parent;

    public TypeEnvironment() {
        this.parent = null;
    }

    public TypeEnvironment(TypeEnvironment parent) {
        this.parent = parent;
    }


    public void define(String name, String type) {
        variables.put(name, TypeDesc.parse(type));
        variableUsed.put(name, false);
        readSinceLastWrite.put(name, false);
    }
    public void defineTyped(String name, TypeDesc type) {
        variables.put(name, type);
        variableUsed.put(name, false);
        readSinceLastWrite.put(name, false);
    }
    public void recordLocation(String name, dhrlang.error.SourceLocation loc){
        if(loc!=null) variableLocations.put(name, loc);
    }
    public void recordWrite(String name, SourceLocation loc){
        TypeEnvironment owner = findEnvironment(name);
        if(owner!=null && owner.variables.containsKey(name)){
            owner.readSinceLastWrite.put(name, false);
            if(loc!=null) owner.lastWriteLocation.put(name, loc);
        }
    }
    private TypeEnvironment findEnvironment(String name){
        if(variables.containsKey(name)) return this;
        if(parent!=null) return parent.findEnvironment(name);
        return null;
    }
    public boolean hadUnreadWrite(String name){
        TypeEnvironment owner = findEnvironment(name);
        if(owner==null) return false;
        Boolean b = owner.readSinceLastWrite.get(name);
        return b!=null && !b;
    }
    public SourceLocation getLastWriteLocation(String name){
        TypeEnvironment owner = findEnvironment(name);
        if(owner==null) return null;
        return owner.lastWriteLocation.get(name);
    }

    public void defineFunction(String name, FunctionSignature signature) {
        functions.put(name, signature);
    }

    public Map<String, String> getAllFields() {
        Map<String, String> allFields = new HashMap<>();
        if (parent != null) {
            allFields.putAll(parent.getAllFields());
        }
        for(var e: this.variables.entrySet()) allFields.put(e.getKey(), e.getValue().toString());
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
        if (variables.containsKey(name)) {
            variableUsed.put(name, true);
            readSinceLastWrite.put(name, true);
            return variables.get(name).toString();
        }
        if (parent != null) return parent.get(name);
        throw new TypeException("Undefined variable '" + name + "'");
    }
    public TypeDesc getDesc(String name) {
        if (variables.containsKey(name)) {
            variableUsed.put(name, true);
            readSinceLastWrite.put(name, true);
            return variables.get(name);
        }
        if (parent != null) return parent.getDesc(name);
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
        Map<String,String> m=new HashMap<>();
        for(var e: variables.entrySet()) m.put(e.getKey(), e.getValue().toString());
        return m;
    }

    public Map<String, Boolean> getLocalUsageMap(){
        return this.variableUsed;
    }
    public dhrlang.error.SourceLocation getVariableLocation(String name){
        return variableLocations.get(name);
    }

    public Map<String, Boolean> getAllUsageMap(){
        Map<String, Boolean> all = new HashMap<>();
        if(parent!=null) all.putAll(parent.getAllUsageMap());
        all.putAll(variableUsed);
        return all;
    }

    public Map<String, FunctionSignature> getLocalFunctions() {
        return this.functions;
    }
    public Map<String, Boolean> getLocalReadSinceWriteMap(){
        return this.readSinceLastWrite;
    }
    public Map<String, SourceLocation> getLocalLastWriteLocations(){
        return this.lastWriteLocation;
    }
}
