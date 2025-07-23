package dhrlang.ast;

import dhrlang.lexer.Token;
import dhrlang.error.SourceLocation;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a generic type reference.
 * Examples: List<T>, Map<String, Integer>, Container<? extends Number>
 */
public class GenericType implements ASTNode {
    private final Token baseName;
    private final List<GenericType> typeArguments;
    private final WildcardType wildcardType;
    private SourceLocation sourceLocation;
    
    // For simple types like T, String, Integer
    public GenericType(Token baseName) {
        this(baseName, new ArrayList<>(), null);
    }
    
    // For parameterized types like List<T>, Map<K, V>
    public GenericType(Token baseName, List<GenericType> typeArguments) {
        this(baseName, typeArguments, null);
    }
    
    // For wildcard types like ? extends Number, ? super Integer
    public GenericType(Token baseName, List<GenericType> typeArguments, WildcardType wildcardType) {
        this.baseName = baseName;
        this.typeArguments = typeArguments != null ? typeArguments : new ArrayList<>();
        this.wildcardType = wildcardType;
        if (baseName != null) {
            this.sourceLocation = baseName.getLocation();
        }
    }
    
    /**
     * Enum for wildcard types
     */
    public enum WildcardType {
        EXTENDS,  // ? extends Type
        SUPER     // ? super Type
    }
    
    public Token getBaseName() {
        return baseName;
    }
    
    public String getBaseNameString() {
        return baseName != null ? baseName.getLexeme() : "unknown";
    }
    
    public List<GenericType> getTypeArguments() {
        return typeArguments;
    }
    
    public boolean hasTypeArguments() {
        return !typeArguments.isEmpty();
    }
    
    public WildcardType getWildcardType() {
        return wildcardType;
    }
    
    public boolean isWildcard() {
        return wildcardType != null;
    }
    
    public boolean isPrimitive() {
        String name = getBaseNameString();
        return "num".equals(name) || "duo".equals(name) || "ek".equals(name) || 
               "sab".equals(name) || "kya".equals(name) || "kaam".equals(name);
    }
    
    public void setSourceLocation(SourceLocation location) {
        this.sourceLocation = location;
    }
    
    @Override
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }
    
    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitGenericType(this);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        if (isWildcard()) {
            sb.append("?");
            if (wildcardType == WildcardType.EXTENDS) {
                sb.append(" extends ").append(getBaseNameString());
            } else if (wildcardType == WildcardType.SUPER) {
                sb.append(" super ").append(getBaseNameString());
            }
        } else {
            sb.append(getBaseNameString());
        }
        
        if (hasTypeArguments()) {
            sb.append("<");
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(typeArguments.get(i));
            }
            sb.append(">");
        }
        
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GenericType)) return false;
        GenericType other = (GenericType) obj;
        return getBaseNameString().equals(other.getBaseNameString()) &&
               typeArguments.equals(other.typeArguments) &&
               wildcardType == other.wildcardType;
    }
    
    @Override
    public int hashCode() {
        return getBaseNameString().hashCode() + typeArguments.hashCode() * 31;
    }
}
