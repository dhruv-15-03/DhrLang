package dhrlang.error;

/**
 * DhrLang Error Codes with unique identifiers for easy reference.
 * 
 * Error Code Ranges:
 * - DHR-E001 to DHR-E099: Lexical Errors
 * - DHR-E100 to DHR-E199: Parse Errors  
 * - DHR-E200 to DHR-E299: Type Errors
 * - DHR-E300 to DHR-E399: Runtime Errors
 * - DHR-E400 to DHR-E499: Array Errors
 * - DHR-E500 to DHR-E599: Class/Object Errors
 * - DHR-W001 to DHR-W099: Style Warnings
 * - DHR-W100 to DHR-W199: Code Quality Warnings
 */
public enum ErrorCode {
    // Lexical Errors (DHR-E001 - DHR-E099)
    UNTERMINATED_STRING("DHR-E001", "Unterminated string literal"),
    INVALID_CHARACTER("DHR-E002", "Invalid character in source"),
    INVALID_CHAR_LITERAL("DHR-E003", "Invalid character literal"),
    
    // Parse Errors (DHR-E100 - DHR-E199)
    MISSING_SEMICOLON("DHR-E101", "Missing semicolon"),
    UNMATCHED_BRACE("DHR-E102", "Unmatched brace"),
    MISSING_PARENTHESIS("DHR-E103", "Missing parenthesis"),
    INVALID_SYNTAX("DHR-E104", "Invalid syntax"),
    REDECLARATION("DHR-E105", "Symbol redefined in same scope"),
    
    // Type Errors (DHR-E200 - DHR-E299)
    TYPE_MISMATCH("DHR-E201", "Type mismatch"),
    UNDECLARED_IDENTIFIER("DHR-E202", "Undeclared identifier"),
    GENERIC_ARITY("DHR-E203", "Wrong number of generic type arguments"),
    
    // Runtime Errors (DHR-E300 - DHR-E399)
    NULL_DEREFERENCE("DHR-E301", "Null pointer dereference"),
    DIVISION_BY_ZERO("DHR-E302", "Division by zero"),
    INTERNAL_ERROR("DHR-E399", "Internal compiler/interpreter error"),
    
    // Array Errors (DHR-E400 - DHR-E499)
    BOUNDS_VIOLATION("DHR-E401", "Array index out of bounds"),
    NEGATIVE_ARRAY_SIZE("DHR-E402", "Negative array size"),
    ARRAY_SIZE_TOO_LARGE("DHR-E403", "Array size exceeds maximum"),
    
    // Class/Object Errors (DHR-E500 - DHR-E599)
    ACCESS_MODIFIER("DHR-E501", "Access modifier violation"),
    
    // Native Function Errors (DHR-E600 - DHR-E699)
    NATIVE_ARITY("DHR-E601", "Wrong number of arguments to native function"),
    UNKNOWN_NATIVE("DHR-E602", "Unknown native function"),
    
    // Style Warnings (DHR-W001 - DHR-W099)
    UNUSED_VARIABLE("DHR-W001", "Unused variable"),
    UNUSED_PARAMETER("DHR-W002", "Unused parameter"),
    VARIABLE_SHADOWING("DHR-W003", "Variable shadows outer scope"),
    EMPTY_BLOCK("DHR-W004", "Empty block"),
    
    // Smart Contract Errors (DHR-E530 - DHR-E599)
    VIEW_STATE_MODIFICATION("DHR-E530", "@view function modifies state"),
    PURE_STATE_ACCESS("DHR-E531", "@pure function accesses state"),
    PURE_STATE_MODIFICATION("DHR-E532", "@pure function modifies state"),
    BLOCKCHAIN_TYPE_MISMATCH("DHR-E533", "Blockchain type mismatch"),
    INVALID_MSG_ACCESS("DHR-E534", "Invalid msg/block access outside contract"),
    STORAGE_SLOT_OVERFLOW("DHR-E535", "Too many storage variables"),
    IMMUTABLE_REASSIGNMENT("DHR-E536", "@immutable field assigned outside constructor"),
    
    // Iteration 2: Safety Features (DHR-E537 - DHR-E560)
    // Reentrancy protection
    REENTRANCY_VIOLATION("DHR-E537", "Reentrant call in @nonreentrant function"),
    REENTRANCY_NESTED("DHR-E538", "@nonreentrant function calls another @nonreentrant function in same contract"),
    
    // CEI pattern enforcement
    CEI_VIOLATION("DHR-E539", "Checks-Effects-Interactions pattern violation"),
    INTERACTION_BEFORE_EFFECT("DHR-E540", "External call (interaction) before state modification (effect)"),
    CHECK_AFTER_EFFECT("DHR-E541", "Condition check after state modification"),
    
    // Checked arithmetic
    ARITHMETIC_OVERFLOW("DHR-E542", "Potential arithmetic overflow on uint256/int256"),
    ARITHMETIC_UNDERFLOW("DHR-E543", "Potential arithmetic underflow on uint256/int256"),
    UNCHECKED_DIVISION("DHR-E544", "Potential division by zero in blockchain arithmetic"),
    
    // Access control
    MISSING_OWNER_CHECK("DHR-E545", "@onlyOwner function missing owner verification"),
    REQUIRE_FAILED("DHR-E546", "require() condition statically evaluates to false"),
    REQUIRE_MISSING_MESSAGE("DHR-E547", "require() should include error message"),
    
    // Events
    EVENT_OUTSIDE_CONTRACT("DHR-E548", "emit statement only valid in @contract classes"),
    EVENT_UNDECLARED("DHR-E549", "Emitting undeclared event"),
    
    // Payable
    PAYABLE_NO_VALUE_CHECK("DHR-E550", "@payable function should validate msg.value"),
    NON_PAYABLE_VALUE_ACCESS("DHR-E551", "msg.value accessed in non-@payable function"),
    
    // Smart Contract Warnings (DHR-W200 - DHR-W299)
    UNUSED_STORAGE("DHR-W201", "@storage field is never accessed"),
    NONREENTRANT_ON_VIEW("DHR-W202", "@nonreentrant unnecessary on @view/@pure"),
    UNCHECKED_ARITHMETIC("DHR-W203", "Arithmetic in unchecked block bypasses overflow protection"),
    MISSING_EVENT_EMISSION("DHR-W204", "State-modifying function should emit an event"),
    
    // Code Quality Warnings (DHR-W100 - DHR-W199)
    UNREACHABLE_CODE("DHR-W101", "Unreachable code"),
    DEAD_STORE("DHR-W102", "Dead store - value written but never read"),
    CONSTANT_CONDITION("DHR-W103", "Constant condition in control flow"),
    REDUNDANT_NULL_CHECK("DHR-W104", "Redundant null check"),
    POSSIBLE_NULL_DEREFERENCE("DHR-W105", "Possible null dereference"),
    STATIC_FORWARD_REFERENCE("DHR-W106", "Static forward reference"),
    STATIC_INIT_CYCLE("DHR-W107", "Static initialization cycle");
    
    private final String code;
    private final String description;
    
    ErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * Get the unique error code identifier (e.g., "DHR-E201")
     */
    public String getCode() {
        return code;
    }
    
    /**
     * Get the human-readable description of this error
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this is an error (vs warning)
     */
    public boolean isError() {
        return code.startsWith("DHR-E");
    }
    
    /**
     * Check if this is a warning
     */
    public boolean isWarning() {
        return code.startsWith("DHR-W");
    }
    
    @Override
    public String toString() {
        return code;
    }
    
    /**
     * Find an ErrorCode by its string code (e.g., "DHR-E201")
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode ec : values()) {
            if (ec.code.equals(code)) {
                return ec;
            }
        }
        return null;
    }
}
