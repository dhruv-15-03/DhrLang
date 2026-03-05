package dhrlang.ast;

import dhrlang.lexer.TokenType;

/**
 * Enumeration of all smart contract annotations supported by DhrLang.
 * These annotations are used to mark classes, fields, and methods with
 * blockchain-specific semantics.
 * 
 * <p>Usage examples:
 * <pre>
 * @contract
 * class Token {
 *     @storage Address owner;
 *     @storage uint256 totalSupply;
 *     
 *     @constructor
 *     kaam init() {
 *         owner = msg.sender;
 *     }
 *     
 *     @view
 *     kaam getOwner() -> Address {
 *         return owner;
 *     }
 *     
 *     @nonreentrant
 *     kaam transfer(Address to, uint256 amount) {
 *         // Safe transfer implementation
 *     }
 * }
 * </pre>
 */
public enum ContractAnnotation {
    
    /**
     * Marks a class as a smart contract.
     * Only classes with @contract can have @storage fields.
     */
    CONTRACT(TokenType.CONTRACT, "@contract"),
    
    /**
     * Marks a field as persistent on-chain storage.
     * Storage variables are automatically assigned storage slots.
     */
    STORAGE(TokenType.STORAGE, "@storage"),
    
    /**
     * Marks a function as read-only.
     * View functions cannot modify state and don't cost gas when called externally.
     */
    VIEW(TokenType.VIEW, "@view"),
    
    /**
     * Marks a function as pure (no state access).
     * Pure functions can only use their parameters and local variables.
     */
    PURE(TokenType.PURE, "@pure"),
    
    /**
     * Marks a function as able to receive ETH.
     * Only @payable functions can have msg.value > 0.
     */
    PAYABLE(TokenType.PAYABLE, "@payable"),
    
    /**
     * Adds a reentrancy guard to a function.
     * Prevents reentrant calls to the same contract.
     */
    NONREENTRANT(TokenType.NONREENTRANT, "@nonreentrant"),
    
    /**
     * Marks a function as the contract constructor.
     * Called once during contract deployment.
     */
    CONSTRUCTOR(TokenType.CONSTRUCTOR, "@constructor"),
    
    /**
     * Marks a function as an event emitter.
     * Events are logged on-chain for external consumption.
     */
    EVENT(TokenType.EVENT, "@event"),
    
    /**
     * Marks a storage variable as immutable.
     * Immutable variables can only be set in the constructor.
     */
    IMMUTABLE(TokenType.IMMUTABLE, "@immutable"),
    
    /**
     * Marks a property as an invariant for formal verification.
     * The compiler will attempt to prove the invariant holds.
     */
    INVARIANT(TokenType.INVARIANT, "@invariant"),

    /**
     * Marks a method as a contract test.
     * Test methods are discovered and run by the ContractTestRunner.
     */
    TEST(TokenType.TEST, "@test"),

    /**
     * Marks a method as a setup hook, run before each test.
     */
    BEFORE_EACH(TokenType.BEFORE_EACH, "@beforeEach"),

    /**
     * Marks a method as a teardown hook, run after each test.
     */
    AFTER_EACH(TokenType.AFTER_EACH, "@afterEach");
    
    private final TokenType tokenType;
    private final String syntax;
    
    ContractAnnotation(TokenType tokenType, String syntax) {
        this.tokenType = tokenType;
        this.syntax = syntax;
    }
    
    /**
     * Get the corresponding TokenType for this annotation.
     */
    public TokenType getTokenType() {
        return tokenType;
    }
    
    /**
     * Get the syntax string (e.g., "@contract").
     */
    public String getSyntax() {
        return syntax;
    }
    
    /**
     * Check if this annotation applies to classes.
     */
    public boolean appliesToClass() {
        return this == CONTRACT;
    }
    
    /**
     * Check if this annotation applies to fields.
     */
    public boolean appliesToField() {
        return this == STORAGE || this == IMMUTABLE;
    }
    
    /**
     * Check if this annotation applies to methods.
     */
    public boolean appliesToMethod() {
        return this == VIEW || this == PURE || this == PAYABLE || 
               this == NONREENTRANT || this == CONSTRUCTOR || 
               this == EVENT || this == INVARIANT ||
               this == TEST || this == BEFORE_EACH || this == AFTER_EACH;
    }
    
    /**
     * Check if this annotation implies state modification is forbidden.
     */
    public boolean forbidsStateModification() {
        return this == VIEW || this == PURE;
    }
    
    /**
     * Convert from TokenType to ContractAnnotation.
     * @throws IllegalArgumentException if token type is not a contract annotation
     */
    public static ContractAnnotation fromTokenType(TokenType tokenType) {
        for (ContractAnnotation annotation : values()) {
            if (annotation.tokenType == tokenType) {
                return annotation;
            }
        }
        throw new IllegalArgumentException("Invalid contract annotation token: " + tokenType);
    }
    
    /**
     * Check if a TokenType is a contract annotation.
     */
    public static boolean isContractAnnotation(TokenType tokenType) {
        for (ContractAnnotation annotation : values()) {
            if (annotation.tokenType == tokenType) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public String toString() {
        return syntax;
    }
}
