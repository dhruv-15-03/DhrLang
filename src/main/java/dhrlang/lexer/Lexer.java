package dhrlang.lexer;

import dhrlang.error.ErrorReporter;
import dhrlang.error.SourceLocation;

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
        keywords.put("null", TokenType.NULL);
        keywords.put("if", TokenType.IF);
        keywords.put("else", TokenType.ELSE);
        keywords.put("while", TokenType.WHILE);
        keywords.put("for", TokenType.FOR);
        keywords.put("switch", TokenType.SWITCH);
        keywords.put("case", TokenType.CASE);
        keywords.put("default", TokenType.DEFAULT);
        keywords.put("do", TokenType.DO);
        keywords.put("break", TokenType.BREAK);
        keywords.put("continue", TokenType.CONTINUE);
        keywords.put("new",TokenType.NEW);
        keywords.put("this", TokenType.THIS);
        keywords.put("extends", TokenType.EXTENDS);
        keywords.put("super", TokenType.SUPER);
        keywords.put("interface", TokenType.INTERFACE);
        keywords.put("implements", TokenType.IMPLEMENTS);
        keywords.put("Override", TokenType.OVERRIDE);
    keywords.put("try", TokenType.TRY);
    keywords.put("koshish", TokenType.TRY); // alias
    keywords.put("catch", TokenType.CATCH);
    keywords.put("pakdo", TokenType.CATCH); // alias used in tests
        keywords.put("finally", TokenType.FINALLY);
        keywords.put("throw", TokenType.THROW);
        keywords.put("private", TokenType.PRIVATE);
        keywords.put("protected", TokenType.PROTECTED);
        keywords.put("public", TokenType.PUBLIC);
        keywords.put("static", TokenType.STATIC);
        keywords.put("abstract", TokenType.ABSTRACT);
        keywords.put("final", TokenType.FINAL);
        
        // Smart Contract Annotations (recognized with @ prefix)
        keywords.put("@contract", TokenType.CONTRACT);
        keywords.put("@storage", TokenType.STORAGE);
        keywords.put("@view", TokenType.VIEW);
        keywords.put("@pure", TokenType.PURE);
        keywords.put("@payable", TokenType.PAYABLE);
        keywords.put("@nonreentrant", TokenType.NONREENTRANT);
        keywords.put("@constructor", TokenType.CONSTRUCTOR);
        keywords.put("@event", TokenType.EVENT);
        keywords.put("@error", TokenType.ERROR);
        keywords.put("@checked", TokenType.CHECKED);
        keywords.put("@unchecked", TokenType.UNCHECKED);
        keywords.put("emit", TokenType.EMIT);
        keywords.put("@immutable", TokenType.IMMUTABLE);
        keywords.put("@invariant", TokenType.INVARIANT);
        keywords.put("as", TokenType.AS);
        
        // Blockchain Types
        keywords.put("Address", TokenType.ADDRESS);
        keywords.put("uint256", TokenType.UINT256);
        keywords.put("int256", TokenType.INT256);
        keywords.put("bytes32", TokenType.BYTES32);
        keywords.put("wei", TokenType.WEI);
        keywords.put("mapping", TokenType.MAPPING);
    }

    public Lexer(String source) {
        this.source = source;
    }

    public Lexer(String source, ErrorReporter errorReporter) {
        this.source = source;
        this.errorReporter = errorReporter;
    }

    // Removed unused setErrorReporter to reduce surface area.
    
    private SourceLocation getCurrentLocation() {
        return new SourceLocation(null, line, column, current, current);
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
            case '?': addToken(TokenType.QUESTION); break;
            case ':': addToken(TokenType.COLON); break;
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
                } else if (match('*')) {
                    while (!isAtEnd()) {
                        if (peek() == '*' && peekNext() == '/') {
                            advance(); // consume *
                            advance(); // consume /
                            break;
                        }
                        advance();
                    }
                } else {
                    addToken(TokenType.SLASH);
                }
                break;

            case '=': addToken(match('=') ? TokenType.EQUALITY : TokenType.ASSIGN); break;
            case '!': addToken(match('=') ? TokenType.NEQ : TokenType.NOT); break;
            case '<':
                if (match('<')) {
                    addToken(TokenType.LSHIFT);
                } else {
                    addToken(match('=') ? TokenType.LEQ : TokenType.LESS);
                }
                break;
            case '>':
                if (match('>')) {
                    addToken(TokenType.RSHIFT);
                } else {
                    addToken(match('=') ? TokenType.GEQ : TokenType.GREATER);
                }
                break;
            case '&':
                if (match('&')) {
                    addToken(TokenType.AND);
                } else {
                    addToken(TokenType.BIT_AND);
                }
                break;
            case '|':
                if (match('|')) {
                    addToken(TokenType.OR);
                } else {
                    addToken(TokenType.BIT_OR);
                }
                break;
            case '^': addToken(TokenType.BIT_XOR); break;
            case '~': addToken(TokenType.BIT_NOT); break;

            case ' ':
            case '\r':
            case '\t':
                break;
            case '\n':
                break;

            case '"': string(); break;
            case '\'': character(); break;
            case '@': 
                if (isAlpha(peek())) {
                    identifier(); 
                } else {
                    if (errorReporter != null) {
                        errorReporter.error(getCurrentLocation(), "Unexpected character: '@'",
                                          "Use '@Override' for method override annotations");
                    } else {
                        System.err.println("[Line " + line + "] Unexpected character: '@'");
                    }
                }
                break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    if (errorReporter != null) {
                        errorReporter.error(getCurrentLocation(), "Unexpected character: '" + c + "'",
                                          "Check for typos or unsupported characters. DhrLang supports letters, numbers, and standard operators");
                    } else {
                        System.err.println("[Line " + line + "] Unexpected character: '" + c + "'");
                    }
                }
        }
    }
    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        
        // Check for annotations (text starting with @)
        TokenType type = keywords.get(text);
        if (type != null) {
            addToken(type, text);
        } else if (text.equals("@Override")) {
            addToken(TokenType.OVERRIDE, text);
        } else {
            addToken(keywords.getOrDefault(text, TokenType.IDENTIFIER), text);
        }
    }
    private void number() {
        // Check for hex literal: 0x or 0X
        if (source.charAt(start) == '0' && current < source.length()
                && (peek() == 'x' || peek() == 'X')) {
            advance(); // consume 'x'
            if (!isHexDigit(peek())) {
                if (errorReporter != null) {
                    errorReporter.error(getCurrentLocation(), "Invalid hex literal.",
                                      "Hex literals must have at least one digit after '0x', e.g. 0xFF");
                } else {
                    System.err.println("[Line " + line + "] Invalid hex literal.");
                }
                return;
            }
            while (isHexDigit(peek())) advance();
            String hexStr = source.substring(start + 2, current); // skip "0x"
            addToken(TokenType.NUMBER, String.valueOf(Long.parseLong(hexStr, 16)));
            return;
        }
        while (isDigit(peek())) advance();
        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek())) advance();
        }
        addToken(TokenType.NUMBER, source.substring(start, current));
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }
    private void string() {
        int stringStartLine = line;
        int stringStartLineStart = lineStart;
        int contentStart = current; // just past opening quote
        boolean hasInterpolation = false;

        // Scan ahead to check for interpolation
        int scanPos = current;
        while (scanPos < source.length() && source.charAt(scanPos) != '"') {
            if (scanPos + 1 < source.length() && source.charAt(scanPos) == '$' && source.charAt(scanPos + 1) == '{') {
                hasInterpolation = true;
                break;
            }
            scanPos++;
        }

        if (!hasInterpolation) {
            // Simple string, no interpolation
            while (peek() != '"' && !isAtEnd()) {
                advance();
            }
            if (isAtEnd()) {
                reportUnterminatedString(stringStartLine, stringStartLineStart);
                return;
            }
            advance();
            String value = source.substring(start + 1, current - 1);
            addToken(TokenType.STRING, value);
            return;
        }

        // String with interpolation: emit as STRING + PLUS + expr + PLUS + STRING ...
        boolean first = true;
        while (!isAtEnd() && peek() != '"') {
            if (peek() == '$' && peekNext() == '{') {
                // Emit preceding text segment (may be empty)
                String segment = source.substring(contentStart, current);
                if (!segment.isEmpty() || first) {
                    if (!first) {
                        int startColumn = current - lineStart + 1;
                        tokens.add(new Token(TokenType.PLUS, "+", line, startColumn, current, current));
                    }
                    int startColumn = contentStart - lineStart;
                    tokens.add(new Token(TokenType.STRING, segment, line, startColumn, contentStart - 1, current));
                    first = false;
                }
                advance(); // skip '$'
                advance(); // skip '{'
                // Add PLUS before the interpolated expression
                if (!first || !source.substring(contentStart, current - 2).isEmpty()) {
                    int startColumn = current - lineStart + 1;
                    tokens.add(new Token(TokenType.PLUS, "+", line, startColumn, current, current));
                }
                // Lex the inner expression tokens, handling nested braces
                int braceDepth = 1;
                int exprStart = current;
                // Save lexer state and lex the interpolation content
                int savedStart = start;
                while (!isAtEnd() && braceDepth > 0) {
                    if (peek() == '{') braceDepth++;
                    else if (peek() == '}') {
                        braceDepth--;
                        if (braceDepth == 0) break;
                    }
                    // Lex individual tokens inside the interpolation
                    start = current;
                    scanToken();
                }
                start = savedStart;
                if (isAtEnd()) {
                    reportUnterminatedString(stringStartLine, stringStartLineStart);
                    return;
                }
                advance(); // skip closing '}'
                contentStart = current;
                first = false;
            } else {
                advance();
            }
        }
        if (isAtEnd()) {
            reportUnterminatedString(stringStartLine, stringStartLineStart);
            return;
        }
        // Emit trailing text segment
        String trailing = source.substring(contentStart, current);
        if (!trailing.isEmpty()) {
            if (!first) {
                int startColumn = current - lineStart + 1;
                tokens.add(new Token(TokenType.PLUS, "+", line, startColumn, current, current));
            }
            int startColumn = contentStart - lineStart;
            tokens.add(new Token(TokenType.STRING, trailing, line, startColumn, contentStart - 1, current + 1));
        } else if (first) {
            // Empty string with no interpolation parts (shouldn't happen, but handle it)
            int startColumn = start - lineStart + 1;
            tokens.add(new Token(TokenType.STRING, "", line, startColumn, start, current + 1));
        }
        advance(); // skip closing quote
    }

    private void reportUnterminatedString(int stringStartLine, int stringStartLineStart) {
        if (errorReporter != null) {
            int stringStartColumn = start - stringStartLineStart + 1;
            SourceLocation stringStart = new SourceLocation(null, stringStartLine, stringStartColumn, start, current);
            errorReporter.error(stringStart, "Unterminated string.",
                              "Add a closing quote \" to complete the string literal");
        } else {
            System.err.println("[Line " + stringStartLine + "] Unterminated string.");
        }
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
                errorReporter.error(getCurrentLocation(), "Invalid char literal.",
                                  "Character literals must be enclosed in single quotes: 'a' or '\\n'");
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
                c == '_' || c == '@';
    }
    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }
}