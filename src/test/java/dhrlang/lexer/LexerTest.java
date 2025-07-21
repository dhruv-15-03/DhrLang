package dhrlang.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    @Test
    void testBasicTokenization() {
        String code = """
            class Main {
                num a = 5;
                sab msg = "Hello";
                kya flag = true;

                kaam main() {
                    print(a);
                    print(msg);
                    print(flag);
                }
            }
            """;

        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();

        assertFalse(tokens.isEmpty(), "Token list should not be empty");
        assertEquals(TokenType.CLASS, tokens.get(0).getType(), "First token should be 'class'");
        assertEquals("Main", tokens.get(1).getLexeme(), "Second token should be identifier 'Main'");
        assertEquals(TokenType.LBRACE, tokens.get(2).getType(), "Third token should be '{'");
        assertEquals(TokenType.EOF, tokens.get(tokens.size() - 1).getType(), "Last token should be EOF");
    }

    @Test
    void testInvalidCharacter() {
        String code = "@";
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        assertEquals(1, tokens.size(), "Only EOF token expected");
        assertEquals(TokenType.EOF, tokens.get(0).getType());
    }

    @Test
    void testLogicalOperators() {
        String code = "true && false || !true";
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();

        assertEquals(TokenType.BOOLEAN, tokens.get(0).getType(), "First token should be 'true'");
        assertEquals(TokenType.AND, tokens.get(1).getType(), "Second token should be '&&'");
        assertEquals(TokenType.BOOLEAN, tokens.get(2).getType(), "Third token should be 'false'");
        assertEquals(TokenType.OR, tokens.get(3).getType(), "Fourth token should be '||'");
        assertEquals(TokenType.NOT, tokens.get(4).getType(), "Fifth token should be '!'");
        assertEquals(TokenType.BOOLEAN, tokens.get(5).getType(), "Sixth token should be 'true'");
        assertEquals(TokenType.EOF, tokens.get(6).getType(), "Last token should be EOF");
    }
}
