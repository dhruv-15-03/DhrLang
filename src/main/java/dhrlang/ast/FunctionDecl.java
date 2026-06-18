package dhrlang.ast;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.EnumSet;

public class FunctionDecl extends Statement {
    private final String returnType;
    private final String name;
    private final List<VarDecl> parameters;
    private final Block body;
    private final Set<Modifier> modifiers;
    private final Set<ContractAnnotation> contractAnnotations;

    /** Design-by-contract preconditions ({@code @requires(expr)}), runtime-enforced at entry. */
    private final List<Expression> requires = new ArrayList<>();
    /** Design-by-contract postconditions ({@code @ensures(expr)}), runtime-enforced at every exit. */
    private final List<Expression> ensures = new ArrayList<>();

    public FunctionDecl(String returnType, String name, List<VarDecl> parameters, Block body) {
        this.returnType = returnType;
        this.name = name;
        this.parameters = parameters;
        this.body = body;
        this.modifiers = new HashSet<>();
        this.contractAnnotations = EnumSet.noneOf(ContractAnnotation.class);
    }
    
    public FunctionDecl(String returnType, String name, List<VarDecl> parameters, Block body, Set<Modifier> modifiers) {
        this(returnType, name, parameters, body, modifiers, EnumSet.noneOf(ContractAnnotation.class));
    }
    
    public FunctionDecl(String returnType, String name, List<VarDecl> parameters, Block body, Set<Modifier> modifiers, Set<ContractAnnotation> contractAnnotations) {
        this.returnType = returnType;
        this.name = name;
        this.parameters = parameters;
        this.body = body;
        this.modifiers = modifiers != null ? modifiers : new HashSet<>();
        this.contractAnnotations = contractAnnotations != null ? contractAnnotations : EnumSet.noneOf(ContractAnnotation.class);
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
    
    /**
     * Get all contract annotations on this function.
     */
    public Set<ContractAnnotation> getContractAnnotations() {
        return contractAnnotations;
    }

    /**
     * Design-by-contract preconditions declared via {@code @requires(expr)}.
     * Each is a boolean expression checked at function entry; a false result
     * reverts. Empty when the function has no preconditions.
     */
    public List<Expression> getRequires() {
        return requires;
    }

    /**
     * Design-by-contract postconditions declared via {@code @ensures(expr)}.
     * Each is a boolean expression checked at every function exit; a false
     * result reverts. Postconditions may reference {@code result} (the return
     * value). Empty when the function has no postconditions.
     */
    public List<Expression> getEnsures() {
        return ensures;
    }
    
    /**
     * Check if this function has a specific contract annotation.
     */
    public boolean hasContractAnnotation(ContractAnnotation annotation) {
        return contractAnnotations.contains(annotation);
    }
    
    /**
     * Check if this is a view function (read-only, no state modification).
     */
    public boolean isView() {
        return hasContractAnnotation(ContractAnnotation.VIEW);
    }
    
    /**
     * Check if this is a pure function (no state access at all).
     */
    public boolean isPure() {
        return hasContractAnnotation(ContractAnnotation.PURE);
    }
    
    /**
     * Check if this is a payable function (can receive ETH).
     */
    public boolean isPayable() {
        return hasContractAnnotation(ContractAnnotation.PAYABLE);
    }
    
    /**
     * Check if this function has reentrancy protection.
     */
    public boolean isNonReentrant() {
        return hasContractAnnotation(ContractAnnotation.NONREENTRANT);
    }
    
    /**
     * Check if this is the contract constructor.
     */
    public boolean isContractConstructor() {
        return hasContractAnnotation(ContractAnnotation.CONSTRUCTOR);
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitFunctionDecl(this);
    }
}