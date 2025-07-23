package dhrlang.ast;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;

/**
 * Generic-aware interface declaration that extends the original InterfaceDecl
 * Examples: interface Comparable<T>, interface Map<K, V>
 */
public class GenericInterfaceDecl extends InterfaceDecl {
    private final List<TypeParameter> typeParameters;
    
    // Non-generic constructor (maintains backward compatibility)
    public GenericInterfaceDecl(String name, List<VariableExpr> parentInterfaces, 
                               List<FunctionDecl> methods, Set<Modifier> modifiers) {
        this(name, new ArrayList<>(), parentInterfaces, methods, modifiers);
    }
    
    // Generic constructor
    public GenericInterfaceDecl(String name, List<TypeParameter> typeParameters, 
                               List<VariableExpr> parentInterfaces, List<FunctionDecl> methods, 
                               Set<Modifier> modifiers) {
        super(name, parentInterfaces, methods, modifiers);
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
        sb.append("interface ").append(getName());
        
        if (isGeneric()) {
            sb.append("<");
            for (int i = 0; i < typeParameters.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(typeParameters.get(i));
            }
            sb.append(">");
        }
        
        if (!getParentInterfaces().isEmpty()) {
            sb.append(" extends ");
            for (int i = 0; i < getParentInterfaces().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(getParentInterfaces().get(i).getName().getLexeme());
            }
        }
        
        return sb.toString();
    }
}
