package dhrlang.interpreter;

import dhrlang.ast.FunctionDecl;
import dhrlang.ast.InterfaceDecl;
import dhrlang.ast.VarDecl;
import dhrlang.ast.VariableExpr;
import dhrlang.error.ErrorFactory;
import dhrlang.error.SourceLocation;

import java.util.*;

public class DhrInterface {
    private final String name;
    private final InterfaceDecl declaration;
    private final Map<String, FunctionSignature> methods;
    private final Set<String> parentInterfaces;
    
    
    public static class FunctionSignature {
        private final String name;
        private final String returnType;
        private final List<String> parameterTypes;
        private final List<String> parameterNames;
        private final SourceLocation location;
        
        public FunctionSignature(String name, String returnType, List<String> parameterTypes, 
                               List<String> parameterNames, SourceLocation location) {
            this.name = name;
            this.returnType = returnType;
            this.parameterTypes = new ArrayList<>(parameterTypes);
            this.parameterNames = new ArrayList<>(parameterNames);
            this.location = location;
        }
        
        public String getName() { return name; }
        public String getReturnType() { return returnType; }
        public List<String> getParameterTypes() { return new ArrayList<>(parameterTypes); }
        public List<String> getParameterNames() { return new ArrayList<>(parameterNames); }
        public SourceLocation getLocation() { return location; }
        public int getArity() { return parameterTypes.size(); }
        
        
        public boolean matches(FunctionSignature other) {
            if (!name.equals(other.name)) return false;
            if (!returnType.equals(other.returnType)) return false;
            if (parameterTypes.size() != other.parameterTypes.size()) return false;
            
            for (int i = 0; i < parameterTypes.size(); i++) {
                if (!parameterTypes.get(i).equals(other.parameterTypes.get(i))) {
                    return false;
                }
            }
            return true;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(returnType).append(" ").append(name).append("(");
            for (int i = 0; i < parameterTypes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(parameterTypes.get(i));
                if (i < parameterNames.size()) {
                    sb.append(" ").append(parameterNames.get(i));
                }
            }
            sb.append(")");
            return sb.toString();
        }
    }
    
    public DhrInterface(InterfaceDecl declaration) {
        this.name = declaration.getName();
        this.declaration = declaration;
        this.methods = new HashMap<>();
        this.parentInterfaces = new HashSet<>();
        
        for (VariableExpr parentInterface : declaration.getParentInterfaces()) {
            this.parentInterfaces.add(parentInterface.getName().getLexeme());
        }
        
        for (FunctionDecl method : declaration.getMethods()) {
            if (method.getBody() != null) {
                throw ErrorFactory.validationError(
                    "Interface method '" + method.getName() + "' cannot have a body implementation. " +
                    "Interface methods should only declare signatures. Remove the method body and end with a semicolon: '" + 
                    method.getReturnType() + " " + method.getName() + "(" + getParameterSignature(method) + ");'",
                    method.getSourceLocation()
                );
            }
            
            validateMethodSignature(method);
            
            List<String> paramTypes = new ArrayList<>();
            List<String> paramNames = new ArrayList<>();
            
            for (VarDecl param : method.getParameters()) {
                paramTypes.add(param.getType());
                paramNames.add(param.getName());
            }
            
            FunctionSignature signature = new FunctionSignature(
                method.getName(),
                method.getReturnType(),
                paramTypes,
                paramNames,
                method.getSourceLocation()
            );
            
            if (methods.containsKey(method.getName())) {
                FunctionSignature existing = methods.get(method.getName());
                throw ErrorFactory.validationError(
                    "Duplicate method '" + method.getName() + "' in interface '" + name + "'. " +
                    "Interface methods must have unique names. The method '" + method.getName() + 
                    "' is already declared at " + existing.getLocation().toShortString() + 
                    ". Either rename this method or remove the duplicate declaration.",
                    method.getSourceLocation()
                );
            }
            
            methods.put(method.getName(), signature);
        }
    }
    
    private void validateMethodSignature(FunctionDecl method) {
        if (method.hasModifier(dhrlang.ast.Modifier.PRIVATE)) {
            throw ErrorFactory.validationError(
                "Interface method '" + method.getName() + "' cannot be private. " +
                "Interface methods are implicitly public. Remove the 'private' modifier.",
                method.getSourceLocation()
            );
        }
        
        if (method.hasModifier(dhrlang.ast.Modifier.STATIC)) {
            throw ErrorFactory.validationError(
                "Interface method '" + method.getName() + "' cannot be static. " +
                "Interface methods define instance behavior and cannot be static. " +
                "Remove the 'static' modifier or move this to a class.",
                method.getSourceLocation()
            );
        }
        
        if (method.getReturnType() == null || method.getReturnType().trim().isEmpty()) {
            throw ErrorFactory.validationError(
                "Interface method '" + method.getName() + "' must specify a return type. " +
                "Declare the return type explicitly: 'returnType " + method.getName() + "(...);' " +
                "Use 'kaam' for void methods.",
                method.getSourceLocation()
            );
        }
    }
    
    private String getParameterSignature(FunctionDecl method) {
        if (method.getParameters().isEmpty()) return "";
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (i > 0) sb.append(", ");
            VarDecl param = method.getParameters().get(i);
            sb.append(param.getType()).append(" ").append(param.getName());
        }
        return sb.toString();
    }
    
    public void validateImplementation(DhrClass implementingClass, SourceLocation location, Map<String, DhrInterface> interfaceRegistry) {
        List<String> missingMethods = new ArrayList<>();
        List<String> incompatibleMethods = new ArrayList<>();
        
        // Get all methods including inherited ones
        Map<String, FunctionSignature> allRequiredMethods = getAllMethods(interfaceRegistry);
        
        for (Map.Entry<String, FunctionSignature> entry : allRequiredMethods.entrySet()) {
            String methodName = entry.getKey();
            FunctionSignature requiredSignature = entry.getValue();
            
            Function classMethod = implementingClass.findMethod(methodName);
            if (classMethod == null) {
                missingMethods.add(methodName + "(" + String.join(", ", requiredSignature.getParameterTypes()) + ")");
                continue;
            }
            
            FunctionDecl classMethodDecl = classMethod.getDeclaration();
            if (!isSignatureCompatible(requiredSignature, classMethodDecl)) {
                incompatibleMethods.add(methodName + " - expected: " + requiredSignature.toString() + 
                                      ", found: " + getMethodSignature(classMethodDecl));
            }
        }
        
        if (!missingMethods.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Class '").append(implementingClass.name)
                   .append("' must implement ").append(missingMethods.size())
                   .append(" method(s) from interface '").append(name).append("': ");
            
            for (int i = 0; i < missingMethods.size(); i++) {
                if (i > 0) message.append(", ");
                message.append(missingMethods.get(i));
            }
            
            message.append(". Add these method implementations to the class.");
            
            throw ErrorFactory.validationError(message.toString(), location);
        }
        
        if (!incompatibleMethods.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Class '").append(implementingClass.name)
                   .append("' has incompatible method signatures for interface '").append(name).append("': ");
            
            for (int i = 0; i < incompatibleMethods.size(); i++) {
                if (i > 0) message.append("; ");
                message.append(incompatibleMethods.get(i));
            }
            
            message.append(". Method signatures must exactly match the interface declaration.");
            
            throw ErrorFactory.validationError(message.toString(), location);
        }
    }
    
    private boolean isSignatureCompatible(FunctionSignature interfaceSignature, FunctionDecl classMethod) {
        if (!interfaceSignature.getReturnType().equals(classMethod.getReturnType())) {
            return false;
        }
        
        if (interfaceSignature.getArity() != classMethod.getParameters().size()) {
            return false;
        }
        
        List<String> interfaceParams = interfaceSignature.getParameterTypes();
        List<VarDecl> classParams = classMethod.getParameters();
        
        for (int i = 0; i < interfaceParams.size(); i++) {
            if (!interfaceParams.get(i).equals(classParams.get(i).getType())) {
                return false;
            }
        }
        
        return true;
    }
    
    private String getMethodSignature(FunctionDecl method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getReturnType()).append(" ").append(method.getName()).append("(");
        
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (i > 0) sb.append(", ");
            VarDecl param = method.getParameters().get(i);
            sb.append(param.getType()).append(" ").append(param.getName());
        }
        sb.append(")");
        return sb.toString();
    }
    
    
    public boolean isAssignableFrom(Object obj) {
        if (obj instanceof Instance instance) {
            return instance.getKlass().implementsInterface(this.name);
        }
        return false;
    }
    
    public String getName() { return name; }
    public InterfaceDecl getDeclaration() { return declaration; }
    public Map<String, FunctionSignature> getMethods() { return new HashMap<>(methods); }
    public Set<String> getParentInterfaces() { return new HashSet<>(parentInterfaces); }
    public boolean hasMethod(String methodName) { return methods.containsKey(methodName); }
    public FunctionSignature getMethod(String methodName) { return methods.get(methodName); }
    
    
    public boolean extendsInterface(String interfaceName, Map<String, DhrInterface> interfaceRegistry) {
        if (parentInterfaces.contains(interfaceName)) {
            return true;
        }
        
        for (String parentName : parentInterfaces) {
            DhrInterface parent = interfaceRegistry.get(parentName);
            if (parent != null && parent.extendsInterface(interfaceName, interfaceRegistry)) {
                return true;
            }
        }
        
        return false;
    }
    
    public Map<String, FunctionSignature> getAllMethods(Map<String, DhrInterface> interfaceRegistry) {
        Map<String, FunctionSignature> allMethods = new HashMap<>();
        
        for (String parentName : parentInterfaces) {
            DhrInterface parent = interfaceRegistry.get(parentName);
            if (parent != null) {
                allMethods.putAll(parent.getAllMethods(interfaceRegistry));
            }
        }
        
        allMethods.putAll(methods);
        
        return allMethods;
    }
    
    
    public void validateInheritance(Map<String, DhrInterface> interfaceRegistry, SourceLocation location) {
        if (hasCircularInheritance(interfaceRegistry, new HashSet<>())) {
            throw ErrorFactory.validationError(
                "Circular interface inheritance detected in interface '" + name + "'. " +
                "Interfaces cannot extend each other in a cycle. Review the inheritance hierarchy and remove circular dependencies.",
                location
            );
        }
        
        for (String parentName : parentInterfaces) {
            if (!interfaceRegistry.containsKey(parentName)) {
                throw ErrorFactory.validationError(
                    "Interface '" + name + "' extends undefined interface '" + parentName + "'. " +
                    "Make sure the parent interface is defined before this interface, or check for typos in the interface name.",
                    location
                );
            }
        }
    }
    
   
    private boolean hasCircularInheritance(Map<String, DhrInterface> interfaceRegistry, Set<String> visited) {
        if (visited.contains(name)) {
            return true;
        }
        
        visited.add(name);
        
        for (String parentName : parentInterfaces) {
            DhrInterface parent = interfaceRegistry.get(parentName);
            if (parent != null && parent.hasCircularInheritance(interfaceRegistry, new HashSet<>(visited))) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public String toString() {
        return "interface " + name;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DhrInterface other)) return false;
        return name.equals(other.name);
    }
    
    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
