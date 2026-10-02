package dhrlang.ast;

import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;

public class VarDecl extends Statement{
    private final String type;   
    private final String name;
    private final Expression initializer; // can be null
    private final Set<Modifier> modifiers;
    private final Set<ContractAnnotation> contractAnnotations;
    private boolean indexed = false;

    public VarDecl(String type, String name, Expression initializer) {
        this.type = type;
        this.name = name;
        this.initializer = initializer;
        this.modifiers = new HashSet<>();
        this.contractAnnotations = EnumSet.noneOf(ContractAnnotation.class);
    }
    
    public VarDecl(String type, String name, Expression initializer, Set<Modifier> modifiers) {
        this(type, name, initializer, modifiers, EnumSet.noneOf(ContractAnnotation.class));
    }
    
    public VarDecl(String type, String name, Expression initializer, Set<Modifier> modifiers, Set<ContractAnnotation> contractAnnotations) {
        this.type = type;
        this.name = name;
        this.initializer = initializer;
        this.modifiers = modifiers != null ? modifiers : new HashSet<>();
        this.contractAnnotations = contractAnnotations != null ? contractAnnotations : EnumSet.noneOf(ContractAnnotation.class);
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
    
    /**
     * Get all contract annotations on this variable.
     */
    public Set<ContractAnnotation> getContractAnnotations() {
        return contractAnnotations;
    }
    
    /**
     * Check if this variable has a specific contract annotation.
     */
    public boolean hasContractAnnotation(ContractAnnotation annotation) {
        return contractAnnotations.contains(annotation);
    }
    
    /**
     * Check if this is a storage variable (persisted on-chain).
     */
    public boolean isStorage() {
        return hasContractAnnotation(ContractAnnotation.STORAGE);
    }
    
    /**
     * Check if this is an immutable variable (set once in constructor).
     */
    public boolean isImmutable() {
        return hasContractAnnotation(ContractAnnotation.IMMUTABLE);
    }

    /**
     * Whether this (event) parameter is declared {@code indexed}, i.e. emitted
     * as an EVM LOG topic rather than in the data section. The EVM allows at
     * most 3 indexed parameters per event.
     */
    public boolean isIndexed() {
        return indexed;
    }

    public void setIndexed(boolean indexed) {
        this.indexed = indexed;
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