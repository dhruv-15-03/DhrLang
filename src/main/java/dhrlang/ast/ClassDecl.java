package dhrlang.ast;

import dhrlang.error.SourceLocation;
import java.util.List;
import java.util.Set;
import java.util.HashSet;


public class ClassDecl implements ASTNode {
    private final String name;
    private final VariableExpr superclass;
    private final List<FunctionDecl> functions;
    private final List<VarDecl> variables;
    private final Set<Modifier> modifiers;
    private boolean isBeingResolved = false;
    private boolean isResolved = false;
    private SourceLocation sourceLocation;

    public ClassDecl(String name, VariableExpr superclass, List<FunctionDecl> functions, List<VarDecl> variables) {
        this(name, superclass, functions, variables, new HashSet<>());
    }
    
    public ClassDecl(String name, VariableExpr superclass, List<FunctionDecl> functions, List<VarDecl> variables, Set<Modifier> modifiers) {
        this.name = name;
        this.functions = functions;
        this.variables = variables;
        this.superclass = superclass;
        this.modifiers = modifiers != null ? modifiers : new HashSet<>();
    }
    public VariableExpr getSuperclass() {
        return superclass;
    }
    public FunctionDecl findMethod(String name) {
        for (FunctionDecl function : this.functions) {
            if (function.getName().equals(name)) {
                return function;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }
    public boolean isBeingResolved() {
        return isBeingResolved;
    }

    public void setBeingResolved(boolean beingResolved) {
        isBeingResolved = beingResolved;
    }

    public boolean isResolved() {
        return isResolved;
    }

    public void setResolved(boolean resolved) {
        isResolved = resolved;
    }

    public List<FunctionDecl> getFunctions() {
        return functions;
    }

    public List<VarDecl> getVariables() {
        return variables;
    }
    
    public Set<Modifier> getModifiers() {
        return modifiers;
    }
    
    public boolean hasModifier(Modifier modifier) {
        return modifiers.contains(modifier);
    }
    
    public boolean isAbstract() {
        return hasModifier(Modifier.ABSTRACT);
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
        return visitor.visitClassDecl(this);
    }

    @Override
    public String toString() {
        return "ClassDecl{" +
                "name='" + name + '\'' +
                ",superclass=" + superclass +
                ", functions=" + functions +
                ", variables=" + variables +
                '}';
    }
}
