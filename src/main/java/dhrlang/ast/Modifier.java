package dhrlang.ast;

public enum Modifier {
    PUBLIC,
    PRIVATE, 
    PROTECTED,
    STATIC;
    
    public static Modifier fromTokenType(dhrlang.lexer.TokenType tokenType) {
        switch (tokenType) {
            case PUBLIC: return PUBLIC;
            case PRIVATE: return PRIVATE;
            case PROTECTED: return PROTECTED;
            case STATIC: return STATIC;
            default: throw new IllegalArgumentException("Invalid modifier token: " + tokenType);
        }
    }
}
