package dhrlang.ast;

import dhrlang.error.SourceLocation;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.EnumSet;


public class ClassDecl implements ASTNode {
    private final String name;
    private final VariableExpr superclass;
    private final List<VariableExpr> interfaces;
    private final List<FunctionDecl> functions;
    private final List<VarDecl> variables;
    private final Set<Modifier> modifiers;
    private final Set<ContractAnnotation> contractAnnotations;
    /** Contract-level design-by-contract invariants ({@code @invariant(expr)}). */
    private final List<Expression> invariants = new ArrayList<>();
    private boolean isBeingResolved = false;
    private boolean isResolved = false;
    private SourceLocation sourceLocation;

    public ClassDecl(String name, VariableExpr superclass, List<FunctionDecl> functions, List<VarDecl> variables) {
        this(name, superclass, new ArrayList<>(), functions, variables, new HashSet<>(), EnumSet.noneOf(ContractAnnotation.class));
    }
    
    public ClassDecl(String name, VariableExpr superclass, List<FunctionDecl> functions, List<VarDecl> variables, Set<Modifier> modifiers) {
        this(name, superclass, new ArrayList<>(), functions, variables, modifiers, EnumSet.noneOf(ContractAnnotation.class));
    }
    
    public ClassDecl(String name, VariableExpr superclass, List<VariableExpr> interfaces, List<FunctionDecl> functions, List<VarDecl> variables, Set<Modifier> modifiers) {
        this(name, superclass, interfaces, functions, variables, modifiers, EnumSet.noneOf(ContractAnnotation.class));
    }
    
    public ClassDecl(String name, VariableExpr superclass, List<VariableExpr> interfaces, List<FunctionDecl> functions, List<VarDecl> variables, Set<Modifier> modifiers, Set<ContractAnnotation> contractAnnotations) {
        this.name = name;
        this.functions = functions;
        this.variables = variables;
        this.superclass = superclass;
        this.interfaces = interfaces != null ? interfaces : new ArrayList<>();
        this.modifiers = modifiers != null ? modifiers : new HashSet<>();
        this.contractAnnotations = contractAnnotations != null ? contractAnnotations : EnumSet.noneOf(ContractAnnotation.class);
    }
    public VariableExpr getSuperclass() {
        return superclass;
    }
    
    public List<VariableExpr> getInterfaces() {
        return interfaces;
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
    
    /**
     * Get all contract annotations on this class.
     */
    public Set<ContractAnnotation> getContractAnnotations() {
        return contractAnnotations;
    }
    
    /**
     * Check if this class has a specific contract annotation.
     */
    public boolean hasContractAnnotation(ContractAnnotation annotation) {
        return contractAnnotations.contains(annotation);
    }
    
    /**
     * Check if this class is marked as a smart contract (@contract).
     */
    public boolean isContract() {
        return hasContractAnnotation(ContractAnnotation.CONTRACT);
    }

    /**
     * Contract-level design-by-contract invariants declared via
     * {@code @invariant(expr)}. Each is a boolean expression over storage
     * fields, re-checked at the exit of every public state-changing function;
     * a false result reverts. Empty when the contract has no invariants.
     */
    public List<Expression> getInvariants() {
        return invariants;
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
