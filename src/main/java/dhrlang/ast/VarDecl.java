package dhrlang.ast;

import java.util.Set;
import java.util.HashSet;

public class VarDecl extends Statement{
    private final String type;   
    private final String name;
    private final Expression initializer; // can be null
    private final Set<Modifier> modifiers; 

    public VarDecl(String type, String name, Expression initializer) {
        this.type = type;
        this.name = name;
        this.initializer = initializer;
        this.modifiers = new HashSet<>();
    }
    
    public VarDecl(String type, String name, Expression initializer, Set<Modifier> modifiers) {
        this.type = type;
        this.name = name;
        this.initializer = initializer;
        this.modifiers = modifiers != null ? modifiers : new HashSet<>();
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Expression getInitializer() {
        return initializer;
    }
    
    public Set<Modifier> getModifiers() {
        return modifiers;
    }
    
    public boolean hasModifier(Modifier modifier) {
        return modifiers.contains(modifier);
    }

    @Override
    public String toString() {
        return "VarDecl{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", initializer=" + initializer +
                '}';
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitVarDecl(this);
    }
}