package dhrlang.lexer;

import dhrlang.error.ErrorReporter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private ErrorReporter errorReporter;

    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int column = 1;
    private int lineStart = 0; 

    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("num", TokenType.NUM);
        keywords.put("duo", TokenType.DUO);
        keywords.put("ek", TokenType.EK);
        keywords.put("sab", TokenType.SAB);
        keywords.put("kya", TokenType.KYA);
        keywords.put("kaam", TokenType.KAAM);
        keywords.put("class", TokenType.CLASS);
        keywords.put("return", TokenType.RETURN);
        keywords.put("true", TokenType.BOOLEAN);
        keywords.put("false", TokenType.BOOLEAN);
        keywords.put("if", TokenType.IF);
        keywords.put("else", TokenType.ELSE);
        keywords.put("while", TokenType.WHILE);
        keywords.put("for", TokenType.FOR);
        keywords.put("break", TokenType.BREAK);
        keywords.put("continue", TokenType.CONTINUE);
        keywords.put("new",TokenType.NEW);
        keywords.put("this", TokenType.THIS);
        keywords.put("extends", TokenType.EXTENDS);
        keywords.put("super", TokenType.SUPER);
        keywords.put("try", TokenType.TRY);
        keywords.put("catch", TokenType.CATCH);
        keywords.put("finally", TokenType.FINALLY);
        keywords.put("throw", TokenType.THROW);
        keywords.put("private", TokenType.PRIVATE);
        keywords.put("protected", TokenType.PROTECTED);
        keywords.put("public", TokenType.PUBLIC);
        keywords.put("static", TokenType.STATIC);
    }

    public Lexer(String source) {
        this.source = source;
    }

    public Lexer(String source, ErrorReporter errorReporter) {
        this.source = source;
        this.errorReporter = errorReporter;
    }

    public void setErrorReporter(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new Token(TokenType.EOF, "", line, column, current, current));
        return tokens;
    }

    private void scanToken() {
        char c = advance();

        switch (c) {
            case '[': addToken(TokenType.LBRACKET); break;
            case ']': addToken(TokenType.RBRACKET); break;
            case '(': addToken(TokenType.LPAREN); break;
            case ')': addToken(TokenType.RPAREN); break;
            case '{': addToken(TokenType.LBRACE); break;
            case '}': addToken(TokenType.RBRACE); break;
            case ',': addToken(TokenType.COMMA); break;
            case ';': addToken(TokenType.SEMICOLON); break;
            case '+': 
                if (match('+')) {
                    addToken(TokenType.INCREMENT);
                } else {
                    addToken(TokenType.PLUS);
                }
                break;
            case '%': addToken(TokenType.MOD); break;
            case '-': 
                if (match('-')) {
                    addToken(TokenType.DECREMENT);
                } else {
                    addToken(TokenType.MINUS);
                }
                break;
            case '*': addToken(TokenType.STAR); break;
            case '.': addToken(TokenType.DOT); break;
            case '/':
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(TokenType.SLASH);
                }
                break;

            case '=': addToken(match('=') ? TokenType.EQUALITY : TokenType.ASSIGN); break;
            case '!': addToken(match('=') ? TokenType.NEQ : TokenType.NOT); break;
            case '<': addToken(match('=') ? TokenType.LEQ : TokenType.LESS); break;
            case '>': addToken(match('=') ? TokenType.GEQ : TokenType.GREATER); break;
            case '&':
                if (match('&')) {
                    addToken(TokenType.AND);
                } else {
                    if (errorReporter != null) {
                        errorReporter.error(line, "Unexpected character: '&'. Did you mean '&&'?");
                    } else {
                        System.err.println("[Line " + line + "] Unexpected character: '&'. Did you mean '&&'?");
                    }
                }
                break;
            case '|':
                if (match('|')) {
                    addToken(TokenType.OR);
                } else {
                    if (errorReporter != null) {
                        errorReporter.error(line, "Unexpected character: '|'. Did you mean '||'?");
                    } else {
                        System.err.println("[Line " + line + "] Unexpected character: '|'. Did you mean '||'?");
                    }
                }
                break;

            // Whitespace
            case ' ':
            case '\r':
            case '\t':
                break;
            case '\n':
                // Newline is handled in advance() method
                break;

            case '"': string(); break;
            case '\'': character(); break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    if (errorReporter != null) {
                        errorReporter.error(line, "Unexpected character: '" + c + "'");
                    } else {
                        System.err.println("[Line " + line + "] Unexpected character: '" + c + "'");
                    }
                }
        }
    }
    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        TokenType type = keywords.getOrDefault(text, TokenType.IDENTIFIER);
        addToken(type, text);
    }
    private void number() {
        while (isDigit(peek())) advance();
        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek())) advance();
        }
        addToken(TokenType.NUMBER, source.substring(start, current));
    }
    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }
    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            advance(); // advance() handles newlines properly
        }
        if (isAtEnd()) {
            if (errorReporter != null) {
                errorReporter.error(line, "Unterminated string.");
            } else {
                System.err.println("[Line " + line + "] Unterminated string.");
            }
            return;
        }

        advance();

        String value = source.substring(start + 1, current - 1);
        addToken(TokenType.STRING, value);
    }

    private void character() {
        if (peek() == '\\') {
            advance();
            advance();
        } else {
            advance();
        }

        if (peek() != '\'') {
            if (errorReporter != null) {
                errorReporter.error(line, "Invalid char literal.");
            } else {
                System.err.println("[Line " + line + "] Invalid char literal.");
            }
            return;
        }
        advance(); // closing '

        String value = source.substring(start + 1, current - 1);
        addToken(TokenType.CHAR, value);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char advance() {
        char c = source.charAt(current++);
        if (c == '\n') {
            line++;
            lineStart = current;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private void addToken(TokenType type) {
        addToken(type, source.substring(start, current));
    }

    private void addToken(TokenType type, String value) {
        int startColumn = start - lineStart + 1;
        tokens.add(new Token(type, value, line, startColumn, start, current));
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }
    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }
}