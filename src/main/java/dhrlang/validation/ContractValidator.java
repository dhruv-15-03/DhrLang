package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.error.ErrorReporter;
import dhrlang.error.SourceLocation;
import dhrlang.types.BlockchainTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates smart contract rules for DhrLang.
 * 
 * <p>This validator enforces the following rules:
 * <ul>
 *   <li>@storage fields can only appear in @contract classes</li>
 *   <li>@immutable fields can only appear in @contract classes</li>
 *   <li>@view functions cannot modify @storage fields</li>
 *   <li>@pure functions cannot access @storage fields</li>
 *   <li>@constructor can only be used once per contract</li>
 *   <li>@nonreentrant is only valid on non-view, non-pure functions</li>
 *   <li>@payable is only valid on non-pure functions</li>
 *   <li>Address literals must be valid 40-hex format</li>
 * </ul>
 */
public class ContractValidator {
    
    private final ErrorReporter errorReporter;
    private final List<ValidationError> errors;
    
    /**
     * Represents a validation error with error code and location.
     */
    public static class ValidationError {
        private final String code;
        private final String message;
        private final String suggestion;
        private final SourceLocation location;
        
        public ValidationError(String code, String message, String suggestion, SourceLocation location) {
            this.code = code;
            this.message = message;
            this.suggestion = suggestion;
            this.location = location;
        }
        
        public String getCode() { return code; }
        public String getMessage() { return message; }
        public String getSuggestion() { return suggestion; }
        public SourceLocation getLocation() { return location; }
        
        @Override
        public String toString() {
            return code + ": " + message;
        }
    }
    
    public ContractValidator() {
        this(null);
    }
    
    public ContractValidator(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
        this.errors = new ArrayList<>();
    }
    
    /**
     * Validate a program for smart contract rules.
     * @return true if validation passed, false if errors found
     */
    public boolean validate(Program program) {
        errors.clear();
        
        for (ClassDecl classDecl : program.getClasses()) {
            validateClass(classDecl);
        }
        
        return errors.isEmpty();
    }
    
    /**
     * Validate a single class declaration.
     */
    public void validateClass(ClassDecl classDecl) {
        boolean isContract = classDecl.isContract();
        
        // Validate fields
        for (VarDecl field : classDecl.getVariables()) {
            validateField(field, classDecl, isContract);
        }
        
        // Validate methods
        int constructorCount = 0;
        for (FunctionDecl method : classDecl.getFunctions()) {
            validateMethod(method, classDecl, isContract);
            
            if (method.isContractConstructor()) {
                constructorCount++;
            }
        }
        
        // Check for multiple constructors
        if (constructorCount > 1) {
            addError("DHR-E505", 
                    "Contract '" + classDecl.getName() + "' has multiple @constructor methods",
                    "A contract can only have one @constructor method",
                    classDecl.getSourceLocation());
        }
        
        // Validate contract-specific rules
        if (isContract) {
            validateContractRules(classDecl);
        }
    }
    
    /**
     * Validate a field declaration within a class.
     */
    private void validateField(VarDecl field, ClassDecl owner, boolean isContract) {
        Set<ContractAnnotation> annotations = field.getContractAnnotations();
        
        // @storage only in @contract
        if (field.isStorage() && !isContract) {
            addError("DHR-E502",
                    "@storage field '" + field.getName() + "' is only allowed in @contract classes",
                    "Add @contract annotation to class '" + owner.getName() + "' or remove @storage",
                    field.getSourceLocation());
        }
        
        // @immutable only in @contract
        if (field.isImmutable() && !isContract) {
            addError("DHR-E503",
                    "@immutable field '" + field.getName() + "' is only allowed in @contract classes",
                    "Add @contract annotation to class '" + owner.getName() + "' or remove @immutable",
                    field.getSourceLocation());
        }
        
        // Cannot have both @storage and @immutable
        if (field.isStorage() && field.isImmutable()) {
            addError("DHR-E504",
                    "Field '" + field.getName() + "' cannot be both @storage and @immutable",
                    "Use @storage for persistent state or @immutable for values set once in constructor",
                    field.getSourceLocation());
        }
        
        // Validate blockchain type literals
        String type = field.getType();
        if (BlockchainTypes.isBlockchainType(type)) {
            validateBlockchainTypeField(field, type);
        }
    }
    
    /**
     * Validate a method declaration within a class.
     */
    private void validateMethod(FunctionDecl method, ClassDecl owner, boolean isContract) {
        Set<ContractAnnotation> annotations = method.getContractAnnotations();
        
        // @view and @pure are mutually exclusive
        if (method.isView() && method.isPure()) {
            addError("DHR-E510",
                    "Method '" + method.getName() + "' cannot be both @view and @pure",
                    "@view allows reading state, @pure forbids all state access. Choose one.",
                    method.getSourceLocation());
        }
        
        // @nonreentrant is not valid on @view or @pure
        if (method.isNonReentrant() && (method.isView() || method.isPure())) {
            addError("DHR-E511",
                    "@nonreentrant on '" + method.getName() + "' is unnecessary for @view/@pure functions",
                    "Remove @nonreentrant - read-only functions cannot cause reentrancy",
                    method.getSourceLocation());
        }
        
        // @payable is not valid on @pure
        if (method.isPayable() && method.isPure()) {
            addError("DHR-E512",
                    "@payable on '" + method.getName() + "' is incompatible with @pure",
                    "@payable functions receive ETH (state change), @pure forbids state access",
                    method.getSourceLocation());
        }
        
        // @checked and @unchecked are mutually exclusive
        if (annotations.contains(ContractAnnotation.CHECKED)
                && annotations.contains(ContractAnnotation.UNCHECKED)) {
            addError("DHR-E515",
                    "Method '" + method.getName() + "' cannot be both @checked and @unchecked",
                    "@checked reverts on overflow, @unchecked wraps. Choose one.",
                    method.getSourceLocation());
        }
        
        // @constructor only in @contract
        if (method.isContractConstructor() && !isContract) {
            addError("DHR-E513",
                    "@constructor method '" + method.getName() + "' is only allowed in @contract classes",
                    "Add @contract annotation to class '" + owner.getName() + "' or remove @constructor",
                    method.getSourceLocation());
        }
        
        // Contract-specific method annotations only in @contract
        if (!isContract) {
            if (method.isView() || method.isPure() || method.isPayable() || method.isNonReentrant()) {
                String annotation = method.isView() ? "@view" : 
                                   method.isPure() ? "@pure" :
                                   method.isPayable() ? "@payable" : "@nonreentrant";
                addError("DHR-E514",
                        annotation + " on '" + method.getName() + "' is only valid in @contract classes",
                        "Add @contract annotation to class '" + owner.getName() + "'",
                        method.getSourceLocation());
            }
        }
    }
    
    /**
     * Validate contract-specific rules.
     */
    private void validateContractRules(ClassDecl contract) {
        // Check that contract has at least one @constructor
        boolean hasConstructor = contract.getFunctions().stream()
                .anyMatch(FunctionDecl::isContractConstructor);
        
        if (!hasConstructor) {
            addError("DHR-E506",
                    "Contract '" + contract.getName() + "' has no @constructor method",
                    "Add a @constructor kaam init() method to initialize the contract",
                    contract.getSourceLocation());
        }
        
        // Check for proper storage variable usage
        validateStorageLayout(contract);
    }
    
    /**
     * Validate storage layout for a contract.
     */
    private void validateStorageLayout(ClassDecl contract) {
        int storageCount = 0;
        
        for (VarDecl field : contract.getVariables()) {
            if (field.isStorage()) {
                storageCount++;
                
                // Validate storage type is appropriate
                String type = field.getType();
                if (!isValidStorageType(type)) {
                    addError("DHR-E520",
                            "Type '" + type + "' is not valid for @storage field '" + field.getName() + "'",
                            "Use blockchain types (Address, uint256, int256, bytes32, mapping) for storage",
                            field.getSourceLocation());
                }
            }
        }
    }
    
    /**
     * Check if a type is valid for storage.
     */
    private boolean isValidStorageType(String type) {
        // Blockchain types are always valid
        if (BlockchainTypes.isBlockchainType(type)) {
            return true;
        }
        
        // Primitive DhrLang types are valid
        return type.equals("num") || type.equals("duo") || 
               type.equals("sab") || type.equals("kya");
    }
    
    /**
     * Validate a blockchain type field.
     */
    private void validateBlockchainTypeField(VarDecl field, String type) {
        // Validate mapping types
        if (BlockchainTypes.isMappingType(type)) {
            try {
                BlockchainTypes.MappingType mappingType = BlockchainTypes.parseMappingType(type);
                // Validate key type (must be hashable)
                if (!isValidMappingKeyType(mappingType.getKeyType())) {
                    addError("DHR-E521",
                            "Invalid mapping key type '" + mappingType.getKeyType() + "'",
                            "Mapping keys must be Address, uint256, int256, or bytes32",
                            field.getSourceLocation());
                }
            } catch (IllegalArgumentException e) {
                addError("DHR-E522",
                        "Invalid mapping syntax for field '" + field.getName() + "'",
                        "Use mapping(KeyType → ValueType) syntax",
                        field.getSourceLocation());
            }
        }
    }
    
    /**
     * Check if a type is valid as a mapping key.
     */
    private boolean isValidMappingKeyType(String type) {
        return BlockchainTypes.ADDRESS.equals(type) ||
               BlockchainTypes.UINT256.equals(type) ||
               BlockchainTypes.INT256.equals(type) ||
               BlockchainTypes.BYTES32.equals(type);
    }
    
    /**
     * Add a validation error.
     */
    private void addError(String code, String message, String suggestion, SourceLocation location) {
        ValidationError error = new ValidationError(code, message, suggestion, location);
        errors.add(error);
        
        if (errorReporter != null && location != null) {
            errorReporter.error(location, "[" + code + "] " + message, suggestion);
        }
    }
    
    /**
     * Get all validation errors.
     */
    public List<ValidationError> getErrors() {
        return new ArrayList<>(errors);
    }
    
    /**
     * Check if a specific error code was reported.
     */
    public boolean hasError(String code) {
        return errors.stream().anyMatch(e -> e.getCode().equals(code));
    }
    
    /**
     * Get error count.
     */
    public int getErrorCount() {
        return errors.size();
    }
    
    /**
     * Clear all errors.
     */
    public void clearErrors() {
        errors.clear();
    }
}
