package dhrlang.ast;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;

/**
 * Generic-aware function/method declaration
 * Examples: <T> T max(T a, T b), <K, V> V get(K key)
 */
public class GenericFunctionDecl extends FunctionDecl {
    private final List<TypeParameter> typeParameters;
    
    // Non-generic constructor (maintains backward compatibility)
    public GenericFunctionDecl(String returnType, String name, List<VarDecl> parameters, 
                              Block body, Set<Modifier> modifiers) {
        this(new ArrayList<>(), returnType, name, parameters, body, modifiers);
    }
    
    // Generic constructor
    public GenericFunctionDecl(List<TypeParameter> typeParameters, String returnType, String name, 
                              List<VarDecl> parameters, Block body, Set<Modifier> modifiers) {
        super(returnType, name, parameters, body, modifiers);
        this.typeParameters = typeParameters != null ? typeParameters : new ArrayList<>();
    }
    
    public List<TypeParameter> getTypeParameters() {
        return typeParameters;
    }
    
    public boolean isGeneric() {
        return !typeParameters.isEmpty();
    }
    
    public int getTypeParameterCount() {
        return typeParameters.size();
    }
    
    public TypeParameter getTypeParameter(String name) {
        for (TypeParameter param : typeParameters) {
            if (param.getNameString().equals(name)) {
                return param;
            }
        }
        return null;
    }
    
    public boolean hasTypeParameter(String name) {
        return getTypeParameter(name) != null;
    }
    
    public List<String> getTypeParameterNames() {
        List<String> names = new ArrayList<>();
        for (TypeParameter param : typeParameters) {
            names.add(param.getNameString());
        }
        return names;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Add type parameters if generic
        if (isGeneric()) {
            sb.append("<");
            for (int i = 0; i < typeParameters.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(typeParameters.get(i));
            }
            sb.append("> ");
        }
        
        sb.append(getReturnType()).append(" ").append(getName()).append("(");
        
        List<VarDecl> params = getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getType()).append(" ").append(params.get(i).getName());
        }
        
        sb.append(")");
        
        return sb.toString();
    }
}
