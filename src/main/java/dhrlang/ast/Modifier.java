package dhrlang.ast;

public enum Modifier {
    PUBLIC,
    PRIVATE, 
    PROTECTED,
    STATIC,
    ABSTRACT,
    FINAL,
    OVERRIDE;
    
    public static Modifier fromTokenType(dhrlang.lexer.TokenType tokenType) {
        switch (tokenType) {
            case PUBLIC: return PUBLIC;
            case PRIVATE: return PRIVATE;
            case PROTECTED: return PROTECTED;
            case STATIC: return STATIC;
            case ABSTRACT: return ABSTRACT;
            case FINAL: return FINAL;
            case OVERRIDE: return OVERRIDE;
            default: throw new IllegalArgumentException("Invalid modifier token: " + tokenType);
        }
    }
}
