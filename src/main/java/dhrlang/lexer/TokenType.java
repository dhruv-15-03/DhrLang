package dhrlang.lexer;

/**
 * Enumeration of all token types supported by DhrLang.
 * These tokens are produced by the Lexer and consumed by the Parser.
 */
public enum TokenType {

    // ===============================
    //          KEYWORDS
    // ===============================
    NUM,
    DUO,// num     - Integer type declaration
    EK,             // ek      - Character type declaration
    SAB,            // sab     - String type declaration
    KYA,            // kya     - Boolean type declaration
    KAAM,           // kaam    - Function declaration
    CLASS,          // class   - Class declaration
    RETURN,         // return  - Return statement
    IF,             // if      - If condition
    ELSE,           // else    - Else block
    WHILE,          // while   - While loop
    FOR,            // for     - For loop
    BREAK,
    CONTINUE,
    NEW,
    DOT,
    THIS,
    EXTENDS,
    SUPER,
    INTERFACE,      // interface - Interface declaration
    IMPLEMENTS,     // implements - Interface implementation
    OVERRIDE,       // @Override - Method override annotation
    
    // ===============================
    //    SMART CONTRACT ANNOTATIONS
    // ===============================
    CONTRACT,       // @contract - Smart contract class marker
    STORAGE,        // @storage - Persistent on-chain variable
    VIEW,           // @view - Read-only function (no state modification)
    PURE,           // @pure - No state access at all
    PAYABLE,        // @payable - Function can receive ETH
    NONREENTRANT,   // @nonreentrant - Reentrancy guard
    CONSTRUCTOR,    // @constructor - Contract constructor
    EVENT,          // @event - Event emission
    IMMUTABLE,      // @immutable - Set once in constructor
    INVARIANT,      // @invariant - Formal verification invariant
    TEST,           // @test - Contract test method
    BEFORE_EACH,    // @beforeEach - Test setup hook
    AFTER_EACH,     // @afterEach - Test teardown hook
    
    // ===============================
    //    BLOCKCHAIN TYPES
    // ===============================
    ADDRESS,        // Address - 20-byte Ethereum address
    UINT256,        // uint256 - 256-bit unsigned integer
    INT256,         // int256 - 256-bit signed integer
    BYTES32,        // bytes32 - 32-byte fixed-size array
    WEI,            // wei - ETH denomination type
    MAPPING,        // mapping - On-chain key-value mapping
    
    PRIVATE,        // private - Private access modifier
    PROTECTED,      // protected - Protected access modifier  
    PUBLIC,         // public - Public access modifier
    STATIC,         // static - Static modifier
    ABSTRACT,       // abstract - Abstract modifier
    FINAL,          // final - Final modifier (cannot be overridden/inherited)

    // Exception handling keywords
    TRY,            // try     - Try block for exception handling
    CATCH,          // catch   - Catch block for exception handling  
    FINALLY,        // finally - Finally block (always executes)
    THROW,          // throw   - Throw an exception

    // ===============================
    //          LITERALS
    // ===============================
    NUMBER,         // Integer literal (e.g., 42)
    STRING,         // String literal (e.g., "hello")
    CHAR,           // Character literal (e.g., 'a')
    BOOLEAN,        // Boolean literal (true / false)

    // ===============================
    //          IDENTIFIERS
    // ===============================
    IDENTIFIER,     // Variable, function, or class name

    // ===============================
    //          OPERATORS
    // ===============================
    PLUS,           // +
    MOD,
    MINUS,          // -
    STAR,           // *
    SLASH,          // /
    ASSIGN,         // =      (assignment)
    EQUALITY,       // ==     (equality check)
    NEQ,            // !=     (not equal)
    GREATER,        // >
    LESS,           // <
    GEQ,            // >=
    LEQ,            // <=
    NOT,            // !      (logical not)
    AND,            // &&     (logical and)
    OR,             // ||     (logical or)
    INCREMENT,      // ++     (increment operator)
    DECREMENT,      // --     (decrement operator)
    QUESTION,       // ?      (wildcard in generics)


    // ===============================
    //          SYMBOLS & PUNCTUATION
    // ===============================
    LPAREN,         // (
    RPAREN,         // )
    LBRACE,         // {
    RBRACE,        // }
    LBRACKET,    // [
    RBRACKET,    // ]
    COMMA,          // ,
    SEMICOLON,      // ;

    // ===============================
    //          END OF FILE
    // ===============================
    EOF             // End of source code
}
