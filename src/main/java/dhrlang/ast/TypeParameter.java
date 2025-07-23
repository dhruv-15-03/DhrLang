package dhrlang.ast;

import dhrlang.lexer.Token;
import dhrlang.error.SourceLocation;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a type parameter in generic declarations.
 * Example: T, U, E extends Comparable<E>
 */
public class TypeParameter implements ASTNode {
    private final Token name;
    private final List<GenericType> bounds; // extends bounds
    private SourceLocation sourceLocation;
    
    public TypeParameter(Token name, List<GenericType> bounds) {
        this.name = name;
        this.bounds = bounds != null ? bounds : new ArrayList<>();
        if (name != null) {
            this.sourceLocation = name.getLocation();
        }
    }
    
    public Token getName() {
        return name;
    }
    
    public List<GenericType> getBounds() {
        return bounds;
    }
    
    public boolean hasBounds() {
        return !bounds.isEmpty();
    }
    
    public String getNameString() {
        return name != null ? name.getLexeme() : "unknown";
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
        return visitor.visitTypeParameter(this);
    }
    
    @Override
    public String toString() {
        if (bounds.isEmpty()) {
            return getNameString();
        }
        StringBuilder sb = new StringBuilder(getNameString());
        sb.append(" extends ");
        for (int i = 0; i < bounds.size(); i++) {
            if (i > 0) sb.append(" & ");
            sb.append(bounds.get(i));
        }
        return sb.toString();
    }
}
