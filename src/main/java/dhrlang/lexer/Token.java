package dhrlang.lexer;

import dhrlang.error.SourceLocation;
import java.util.Objects;

public final class Token {

    private final TokenType type;
    private final String lexeme;
    private final int line;
    private final int column;
    private final int startOffset;
    private final int endOffset;
    private final String filename;

    public Token(TokenType type, String lexeme, int line) {
        this(type, lexeme, line, 0, -1, -1);
    }

    public Token(TokenType type, String lexeme, int line, int column, int startOffset, int endOffset) {
        this(type, lexeme, line, column, startOffset, endOffset, null);
    }

    private Token(TokenType type, String lexeme, int line, int column, int startOffset, int endOffset, String filename) {
        this.type = Objects.requireNonNull(type, "TokenType cannot be null");
        this.lexeme = Objects.requireNonNull(lexeme, "Lexeme cannot be null");
        this.line = line;
        this.column = column;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.filename = filename;
    }

    public TokenType getType() {
        return type;
    }

    public String getLexeme() {
        return lexeme;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public SourceLocation getLocation() {
        return new SourceLocation(filename, line, column, startOffset, endOffset);
    }

    public Token inFile(String filename) {
        return new Token(type, lexeme, line, column, startOffset, endOffset, filename);
    }

    @Override
    public String toString() {
        return String.format("Token(%s, \"%s\", %d:%d)", type, lexeme, line, column);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Token)) return false;
        Token token = (Token) o;
        return line == token.line &&
                column == token.column &&
                type == token.type &&
                Objects.equals(filename, token.filename) &&
                lexeme.equals(token.lexeme);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, lexeme, line, column, filename);
    }
}
