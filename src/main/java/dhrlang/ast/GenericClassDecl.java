package dhrlang.ast;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;

/**
 * Generic-aware class declaration that extends the original ClassDecl
 * Examples: class Container<T>, class Map<K, V extends Comparable<V>>
 */
public class GenericClassDecl extends ClassDecl {
    private final List<TypeParameter> typeParameters;
    
    // Non-generic constructor (maintains backward compatibility)
    public GenericClassDecl(String name, VariableExpr superclass, List<VariableExpr> interfaces, 
                           List<FunctionDecl> functions, List<VarDecl> variables, Set<Modifier> modifiers) {
        this(name, new ArrayList<>(), superclass, interfaces, functions, variables, modifiers);
    }
    
    // Generic constructor
    public GenericClassDecl(String name, List<TypeParameter> typeParameters, VariableExpr superclass, 
                           List<VariableExpr> interfaces, List<FunctionDecl> functions, 
                           List<VarDecl> variables, Set<Modifier> modifiers) {
        super(name, superclass, interfaces, functions, variables, modifiers);
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
        sb.append("class ").append(getName());
        
        if (isGeneric()) {
            sb.append("<");
            for (int i = 0; i < typeParameters.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(typeParameters.get(i));
            }
            sb.append(">");
        }
        
        if (getSuperclass() != null) {
            sb.append(" extends ").append(getSuperclass().getName().getLexeme());
        }
        
        if (!getInterfaces().isEmpty()) {
            sb.append(" implements ");
            for (int i = 0; i < getInterfaces().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(getInterfaces().get(i).getName().getLexeme());
            }
        }
        
        return sb.toString();
    }
}
