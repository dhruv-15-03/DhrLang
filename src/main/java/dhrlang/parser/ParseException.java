package dhrlang.parser;

import dhrlang.error.SourceLocation;
import dhrlang.lexer.Token;

public class ParseException extends RuntimeException {
    private final int line;
    private final int column;
    private final Token token;

    public ParseException(String message, int line) {
        this(message, line, 0, null);
    }

    public ParseException(String message, Token token) {
        this(message, token.getLine(), token.getColumn(), token);
    }

    public ParseException(String message, int line, int column, Token token) {
        super(message);
        this.line = line;
        this.column = column;
        this.token = token;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public Token getToken() {
        return token;
    }

    public SourceLocation getLocation() {
        return new SourceLocation(null, line, column);
    }
}
