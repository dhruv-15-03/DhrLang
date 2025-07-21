package dhrlang.ast;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class FunctionDecl extends Statement {
    private final String returnType;
    private final String name;
    private final List<VarDecl> parameters;
    private final Block body;
    private final Set<Modifier> modifiers;

    public FunctionDecl(String returnType, String name, List<VarDecl> parameters, Block body) {
        this.returnType = returnType;
        this.name = name;
        this.parameters = parameters;
        this.body = body;
        this.modifiers = new HashSet<>();
    }
    
    public FunctionDecl(String returnType, String name, List<VarDecl> parameters, Block body, Set<Modifier> modifiers) {
        this.returnType = returnType;
        this.name = name;
        this.parameters = parameters;
        this.body = body;
        this.modifiers = modifiers != null ? modifiers : new HashSet<>();
    }

    public String getReturnType() {
        return returnType;
    }

    public String getName() {
        return name;
    }

    public List<VarDecl> getParameters() {
        return parameters;
    }

    public Block getBody() {
        return body;
    }
    
    public Set<Modifier> getModifiers() {
        return modifiers;
    }
    
    public boolean hasModifier(Modifier modifier) {
        return modifiers.contains(modifier);
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitFunctionDecl(this);
    }
}