package dhrlang.ast;

public enum Modifier {
    PUBLIC,
    PRIVATE, 
    PROTECTED,
    STATIC,
    ABSTRACT;
    
    public static Modifier fromTokenType(dhrlang.lexer.TokenType tokenType) {
        switch (tokenType) {
            case PUBLIC: return PUBLIC;
            case PRIVATE: return PRIVATE;
            case PROTECTED: return PROTECTED;
            case STATIC: return STATIC;
            case ABSTRACT: return ABSTRACT;
            default: throw new IllegalArgumentException("Invalid modifier token: " + tokenType);
        }
    }
}
