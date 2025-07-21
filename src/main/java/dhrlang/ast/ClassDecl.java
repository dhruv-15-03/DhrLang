package dhrlang.ast;

import java.util.List;

/**
 * Represents a class declaration in DhrLang.
 */
public class ClassDecl {
    private final String name;
    private final VariableExpr superclass;
    private final List<FunctionDecl> functions;
    private final List<VarDecl> variables;
    private boolean isBeingResolved = false;
    private boolean isResolved = false;

    public ClassDecl(String name,VariableExpr superclass,  List<FunctionDecl> functions, List<VarDecl> variables) {
        this.name = name;
        this.functions = functions;
        this.variables = variables;
        this.superclass = superclass;
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
