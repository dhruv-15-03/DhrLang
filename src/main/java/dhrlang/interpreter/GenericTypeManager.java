package dhrlang.interpreter;

import dhrlang.ast.*;
import dhrlang.error.ErrorFactory;
import dhrlang.error.SourceLocation;
import java.util.*;


public class GenericTypeManager {
    
    private final Map<String, Map<String, String>> classTypeBindings = new HashMap<>();
    private final Map<String, Map<String, String>> methodTypeBindings = new HashMap<>();
    private final Stack<String> contextStack = new Stack<>();
    
    public void enterContext(String contextId) {
        contextStack.push(contextId);
    }
    
    public void exitContext() {
        if (!contextStack.isEmpty()) {
            String contextId = contextStack.pop();
            if (contextId.contains("::")) {
                methodTypeBindings.remove(contextId);
            }
        }
    }
    
    
    public String getCurrentContext() {
        return contextStack.isEmpty() ? null : contextStack.peek();
    }
    
    public void bindClassTypeParameters(String className, List<TypeParameter> typeParams, 
                                       List<GenericType> typeArgs, SourceLocation location) {
        if (typeParams.size() != typeArgs.size()) {
            throw ErrorFactory.validationError(
                String.format("Generic class '%s' expects %d type argument%s but got %d. " +
                             "Provide the correct number of type arguments: %s<%s>",
                             className, typeParams.size(), 
                             typeParams.size() == 1 ? "" : "s",
                             typeArgs.size(),
                             className,
                             getTypeParameterNames(typeParams)),
                location
            );
        }
        
        Map<String, String> bindings = new HashMap<>();
        for (int i = 0; i < typeParams.size(); i++) {
            String paramName = typeParams.get(i).getNameString();
            String argType = resolveGenericType(typeArgs.get(i));
            
            validateTypeBounds(typeParams.get(i), argType, location);
            
            bindings.put(paramName, argType);
        }
        
        classTypeBindings.put(className, bindings);
    }
    
    
    public void bindMethodTypeParameters(String methodId, List<TypeParameter> typeParams, 
                                        List<GenericType> typeArgs, SourceLocation location) {
        if (typeParams.size() != typeArgs.size()) {
            throw ErrorFactory.validationError(
                String.format("Generic method expects %d type argument%s but got %d. " +
                             "Provide explicit type arguments: <%s>methodName(...)",
                             typeParams.size(),
                             typeParams.size() == 1 ? "" : "s", 
                             typeArgs.size(),
                             getTypeParameterNames(typeParams)),
                location
            );
        }
        
        Map<String, String> bindings = new HashMap<>();
        for (int i = 0; i < typeParams.size(); i++) {
            String paramName = typeParams.get(i).getNameString();
            String argType = resolveGenericType(typeArgs.get(i));
            
            validateTypeBounds(typeParams.get(i), argType, location);
            
            bindings.put(paramName, argType);
        }
        
        methodTypeBindings.put(methodId, bindings);
    }
    
    
    public String resolveTypeParameter(String paramName, SourceLocation location) {
        String currentContext = getCurrentContext();
        if (currentContext != null && currentContext.contains("::")) {
            Map<String, String> methodBindings = methodTypeBindings.get(currentContext);
            if (methodBindings != null && methodBindings.containsKey(paramName)) {
                return methodBindings.get(paramName);
            }
        }
        
        for (Map<String, String> classBindings : classTypeBindings.values()) {
            if (classBindings.containsKey(paramName)) {
                return classBindings.get(paramName);
            }
        }
        
        throw ErrorFactory.validationError(
            String.format("Type parameter '%s' is not in scope. " +
                         "Make sure the type parameter is declared in the current class or method: " +
                         "class MyClass<T> or <T> T myMethod()",
                         paramName),
            location
        );
    }
    
    
    private void validateTypeBounds(TypeParameter typeParam, String actualType, SourceLocation location) {
        if (!typeParam.hasBounds()) {
            return; 
        }
        
        for (GenericType bound : typeParam.getBounds()) {
            String boundType = resolveGenericType(bound);
            
            if (!isAssignableFrom(boundType, actualType)) {
                throw ErrorFactory.validationError(
                    String.format("Type argument '%s' does not satisfy the bound '%s' for type parameter '%s'. " +
                                 "The type must extend or implement '%s'. Consider using a subtype or implementing the required interface.",
                                 actualType, boundType, typeParam.getNameString(), boundType),
                    location
                );
            }
        }
    }
    
    
    private boolean isAssignableFrom(String targetType, String sourceType) {
        if (targetType.equals(sourceType)) {
            return true;
        }
        
        if ("duo".equals(targetType) && "num".equals(sourceType)) {
            return true;
        }
        
        return true;
    }
    
    
    public String resolveGenericType(GenericType genericType) {
        if (genericType.isWildcard()) {
            return "Object";
        }
        
        String baseName = genericType.getBaseNameString();
        
        String currentContext = getCurrentContext();
        if (currentContext != null) {
            if (currentContext.contains("::")) {
                Map<String, String> methodBindings = methodTypeBindings.get(currentContext);
                if (methodBindings != null && methodBindings.containsKey(baseName)) {
                    return methodBindings.get(baseName);
                }
            }
            
            for (Map<String, String> classBindings : classTypeBindings.values()) {
                if (classBindings.containsKey(baseName)) {
                    return classBindings.get(baseName);
                }
            }
        }
        
        return baseName;
    }
    
    
    private String getTypeParameterNames(List<TypeParameter> typeParams) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < typeParams.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(typeParams.get(i).getNameString());
        }
        return sb.toString();
    }
    
    
    public boolean isGenericClass(ClassDecl classDecl) {
        return classDecl instanceof GenericClassDecl && ((GenericClassDecl) classDecl).isGeneric();
    }
    
    
    public boolean isGenericInterface(InterfaceDecl interfaceDecl) {
        return interfaceDecl instanceof GenericInterfaceDecl && ((GenericInterfaceDecl) interfaceDecl).isGeneric();
    }
    
    
    public String eraseType(String type) {
        int genericStart = type.indexOf('<');
        if (genericStart != -1) {
            return type.substring(0, genericStart);
        }
        return type;
    }
    
    
    public void clearBindings() {
        classTypeBindings.clear();
        methodTypeBindings.clear();
        contextStack.clear();
    }
    
    
    // Removed unused getDebugInfo() for leaner runtime.
}
