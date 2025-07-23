package dhrlang.ast;

import dhrlang.error.SourceLocation;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

public class InterfaceDecl implements ASTNode {
    private final String name;
    private final List<VariableExpr> parentInterfaces; // Support for interface inheritance
    private final List<FunctionDecl> methods;
    private final Set<Modifier> modifiers;
    private SourceLocation sourceLocation;
    private boolean isBeingResolved = false;
    private boolean isResolved = false;

    public InterfaceDecl(String name, List<FunctionDecl> methods) {
        this(name, new ArrayList<>(), methods, new HashSet<>());
    }
    
    public InterfaceDecl(String name, List<FunctionDecl> methods, Set<Modifier> modifiers) {
        this(name, new ArrayList<>(), methods, modifiers);
    }
    
    public InterfaceDecl(String name, List<VariableExpr> parentInterfaces, List<FunctionDecl> methods, Set<Modifier> modifiers) {
        this.name = name;
        this.parentInterfaces = parentInterfaces != null ? parentInterfaces : new ArrayList<>();
        this.methods = methods;
        this.modifiers = modifiers != null ? modifiers : new HashSet<>();
    }

    public String getName() {
        return name;
    }

    public List<VariableExpr> getParentInterfaces() {
        return parentInterfaces;
    }

    public List<FunctionDecl> getMethods() {
        return methods;
    }
    
    public Set<Modifier> getModifiers() {
        return modifiers;
    }
    
    public boolean hasModifier(Modifier modifier) {
        return modifiers.contains(modifier);
    }
    
    public FunctionDecl findMethod(String methodName) {
        for (FunctionDecl method : methods) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
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
    
    public void setSourceLocation(SourceLocation location) {
        this.sourceLocation = location;
    }
    
    @Override
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }
    
    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitInterfaceDecl(this);
    }

    @Override
    public String toString() {
        return "InterfaceDecl{" +
                "name='" + name + '\'' +
                ", methods=" + methods +
                '}';
    }
}
