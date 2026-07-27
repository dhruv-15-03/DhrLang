package dhrlang.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for DhrLang's enhanced error hint system.
 * 
 * These tests verify that:
 * 1. Each error type has a helpful, contextual hint
 * 2. Error codes are properly formatted (DHR-EXXX / DHR-WXXX)
 * 3. Hints include actionable suggestions
 * 4. Edge cases are handled gracefully
 */
@DisplayName("Error Hint System Tests")
public class ErrorHintTests {

    @Nested
    @DisplayName("Error Code Format Tests")
    class ErrorCodeFormatTests {
        
        @Test
        @DisplayName("All error codes should have DHR prefix")
        void allCodesHaveDhrPrefix() {
            for (ErrorCode code : ErrorCode.values()) {
                assertTrue(code.getCode().startsWith("DHR-"), 
                    "Error code " + code.name() + " should have DHR- prefix but was: " + code.getCode());
            }
        }
        
        @Test
        @DisplayName("Error codes should have E prefix, warnings W prefix")
        void errorWarningPrefixesCorrect() {
            for (ErrorCode code : ErrorCode.values()) {
                String codeStr = code.getCode();
                if (code.isError()) {
                    assertTrue(codeStr.contains("-E"), 
                        code.name() + " should be an error with -E but was: " + codeStr);
                }
                if (code.isWarning()) {
                    assertTrue(codeStr.contains("-W"), 
                        code.name() + " should be a warning with -W but was: " + codeStr);
                }
            }
        }
        
        @Test
        @DisplayName("Each error code should have a description")
        void allCodesHaveDescription() {
            for (ErrorCode code : ErrorCode.values()) {
                assertNotNull(code.getDescription(), code.name() + " should have a description");
                assertFalse(code.getDescription().isEmpty(), code.name() + " description should not be empty");
            }
        }
        
        @Test
        @DisplayName("toString() should return the code identifier")
        void toStringReturnsCode() {
            assertEquals("DHR-E201", ErrorCode.TYPE_MISMATCH.toString());
            assertEquals("DHR-W001", ErrorCode.UNUSED_VARIABLE.toString());
        }
        
        @Test
        @DisplayName("fromCode() should find code by string identifier")
        void fromCodeFindsCorrectCode() {
            assertEquals(ErrorCode.TYPE_MISMATCH, ErrorCode.fromCode("DHR-E201"));
            assertEquals(ErrorCode.BOUNDS_VIOLATION, ErrorCode.fromCode("DHR-E401"));
            assertNull(ErrorCode.fromCode("DHR-E999"));
            assertNull(ErrorCode.fromCode("INVALID"));
        }
    }
    
    @Nested
    @DisplayName("Parse Error Hints")
    class ParseErrorHints {
        
        @Test
        @DisplayName("Missing semicolon should suggest adding semicolon")
        void missingSemicolonHint() {
            String hint = ErrorMessages.getParseErrorHint("Expected ';' after expression", null);
            assertTrue(hint.contains("semicolon") || hint.contains(";"), 
                "Hint should mention semicolon: " + hint);
        }
        
        @Test
        @DisplayName("Missing brace should suggest matching braces")
        void missingBraceHint() {
            String hint = ErrorMessages.getParseErrorHint("Expected '}' at end of block", null);
            assertTrue(hint.contains("brace") || hint.contains("{") || hint.contains("}"),
                "Hint should mention braces: " + hint);
        }
        
        @Test
        @DisplayName("Unknown token should give context-aware hint")
        void unknownTokenHintWithContext() {
            dhrlang.lexer.Token token = new dhrlang.lexer.Token(
                dhrlang.lexer.TokenType.IDENTIFIER, "foobar", 10, 5, 0, 6);
            String hint = ErrorMessages.getParseErrorHint("Unexpected token", token);
            assertTrue(hint.contains("foobar"), "Hint should reference the actual token: " + hint);
        }
        
        @Test
        @DisplayName("Expected class should suggest class syntax")
        void expectedClassHint() {
            String hint = ErrorMessages.getParseErrorHint("Expected 'class' declaration", null);
            assertTrue(hint.contains("class"), "Hint should mention class: " + hint);
        }
    }
    
    @Nested
    @DisplayName("Type Error Hints")
    class TypeErrorHints {
        
        @Test
        @DisplayName("String to number should suggest parseNum")
        void stringToNumberHint() {
            String hint = ErrorMessages.getTypeErrorHint("sab", "num");
            assertTrue(hint.toLowerCase().contains("parsenum") || hint.contains("conversion"),
                "Hint should suggest parseNum or conversion: " + hint);
        }
        
        @Test
        @DisplayName("Boolean to number should explain limitation")
        void booleanToNumberHint() {
            String hint = ErrorMessages.getTypeErrorHint("kya", "num");
            assertTrue(hint.contains("cannot") || hint.contains("not"),
                "Hint should explain boolean cannot be number: " + hint);
        }
        
        @Test
        @DisplayName("Duo to num should suggest rounding")
        void duoToNumHint() {
            String hint = ErrorMessages.getTypeErrorHint("duo", "num");
            assertTrue(hint.contains("floor") || hint.contains("round") || hint.contains("truncat"),
                "Hint should suggest floor/round: " + hint);
        }
    }
    
    @Nested
    @DisplayName("Undefined Variable Hints")
    class UndefinedVariableHints {
        
        @Test
        @DisplayName("'string' should suggest 'sab'")
        void stringTypeSuggestion() {
            String hint = ErrorMessages.getUndefinedVariableHint("string");
            assertTrue(hint.contains("sab"), "Hint should suggest 'sab': " + hint);
        }
        
        @Test
        @DisplayName("'int' should suggest 'num'")
        void intTypeSuggestion() {
            String hint = ErrorMessages.getUndefinedVariableHint("int");
            assertTrue(hint.contains("num"), "Hint should suggest 'num': " + hint);
        }
        
        @Test
        @DisplayName("'bool' should suggest 'kya'")
        void boolTypeSuggestion() {
            String hint = ErrorMessages.getUndefinedVariableHint("bool");
            assertTrue(hint.contains("kya"), "Hint should suggest 'kya': " + hint);
        }
        
        @Test
        @DisplayName("'double' should suggest 'duo'")
        void doubleTypeSuggestion() {
            String hint = ErrorMessages.getUndefinedVariableHint("double");
            assertTrue(hint.contains("duo"), "Hint should suggest 'duo': " + hint);
        }
        
        @Test
        @DisplayName("'void' should suggest 'kaam'")
        void voidTypeSuggestion() {
            String hint = ErrorMessages.getUndefinedVariableHint("void");
            assertTrue(hint.contains("kaam"), "Hint should suggest 'kaam': " + hint);
        }
        
        @Test
        @DisplayName("Regular variable should suggest declaration")
        void regularVariableSuggestion() {
            String hint = ErrorMessages.getUndefinedVariableHint("myVar");
            assertTrue(hint.contains("Declare") || hint.contains("declared"),
                "Hint should suggest declaring variable: " + hint);
        }
    }
    
    @Nested
    @DisplayName("Array Error Hints")
    class ArrayErrorHints {
        
        @Test
        @DisplayName("Array index error should mention bounds")
        void arrayIndexBoundsHint() {
            String hint = ErrorMessages.getArrayIndexErrorHint();
            assertTrue(hint.contains("bounds") || hint.contains("range"),
                "Hint should mention bounds: " + hint);
        }
        
        @Test
        @DisplayName("Specific negative index should explain issue")
        void negativeIndexHint() {
            String hint = ErrorMessages.getArrayIndexErrorHint(-1, 5);
            assertTrue(hint.contains("-1") || hint.contains("negative"),
                "Hint should mention negative index: " + hint);
            assertTrue(hint.contains("0") && hint.contains("4"),
                "Hint should show valid range [0,4]: " + hint);
        }
        
        @Test
        @DisplayName("Index exceeding bounds should show valid range")
        void indexExceedsHint() {
            String hint = ErrorMessages.getArrayIndexErrorHint(10, 5);
            assertTrue(hint.contains("10") && hint.contains("exceeds"),
                "Hint should mention index 10 exceeds: " + hint);
        }
    }
    
    @Nested
    @DisplayName("Lexer Error Hints")
    class LexerErrorHints {
        
        @Test
        @DisplayName("Single & should suggest &&")
        void singleAmpersandHint() {
            String hint = ErrorMessages.getLexerErrorHint("Unexpected character: '&'");
            assertTrue(hint.contains("&&"), "Hint should suggest &&: " + hint);
        }
        
        @Test
        @DisplayName("Single | should suggest ||")
        void singlePipeHint() {
            String hint = ErrorMessages.getLexerErrorHint("Unexpected character: '|'");
            assertTrue(hint.contains("||"), "Hint should suggest ||: " + hint);
        }
        
        @Test
        @DisplayName("Unterminated string should suggest closing quote")
        void unterminatedStringHint() {
            String hint = ErrorMessages.getLexerErrorHint("Unterminated string literal");
            assertTrue(hint.contains("\"") || hint.contains("quote"),
                "Hint should mention closing quote: " + hint);
        }
    }
    
    @Nested
    @DisplayName("Multi-Dimensional Array Hints")
    class MultiDimArrayHints {
        
        @Test
        @DisplayName("Dimension mismatch should explain access syntax")
        void dimensionMismatchHint() {
            String hint = ErrorMessages.getMultiDimArrayHint("DIMENSION_MISMATCH");
            assertTrue(hint.contains("[i][j]") || hint.contains("dimension"),
                "Hint should explain dimension syntax: " + hint);
        }
        
        @Test
        @DisplayName("Jagged null should suggest allocation")
        void jaggedNullHint() {
            String hint = ErrorMessages.getMultiDimArrayHint("JAGGED_NULL");
            assertTrue(hint.contains("allocate") || hint.contains("new"),
                "Hint should suggest allocation: " + hint);
        }
        
        @Test
        @DisplayName("IR not supported should suggest AST backend")
        void irNotSupportedHint() {
            String hint = ErrorMessages.getMultiDimArrayHint("IR_NOT_SUPPORTED");
            assertTrue(hint.contains("ast") || hint.contains("AST") || hint.contains("backend"),
                "Hint should suggest AST backend: " + hint);
        }
    }
    
    @Nested
    @DisplayName("Function Call Error Hints")
    class FunctionCallHints {
        
        @Test
        @DisplayName("Too few arguments should suggest adding")
        void tooFewArgsHint() {
            String hint = ErrorMessages.getFunctionCallErrorHint("myFunc", 3, 1);
            assertTrue(hint.contains("Add") && hint.contains("2"),
                "Hint should suggest adding 2 arguments: " + hint);
        }
        
        @Test
        @DisplayName("Too many arguments should suggest removing")
        void tooManyArgsHint() {
            String hint = ErrorMessages.getFunctionCallErrorHint("myFunc", 1, 4);
            assertTrue(hint.contains("Remove") && hint.contains("3"),
                "Hint should suggest removing 3 arguments: " + hint);
        }
        
        @Test
        @DisplayName("Singular argument should not have 's' suffix")
        void singularArgumentGrammar() {
            String hint = ErrorMessages.getFunctionCallErrorHint("myFunc", 1, 3);
            assertTrue(hint.contains("1 argument") && !hint.contains("1 arguments"),
                "Should say '1 argument' not '1 arguments': " + hint);
        }
    }
    
    @Nested
    @DisplayName("Generic Type Hints")
    class GenericTypeHints {
        
        @Test
        @DisplayName("Missing type argument should suggest example")
        void missingTypeArgHint() {
            String hint = ErrorMessages.getGenericTypeHint("MISSING_TYPE_ARG", "List");
            assertTrue(hint.contains("List<") || hint.contains("type argument"),
                "Hint should suggest type arguments: " + hint);
        }
        
        @Test
        @DisplayName("Runtime access should explain declaration vs runtime")
        void runtimeAccessHint() {
            String hint = ErrorMessages.getGenericTypeHint("RUNTIME_ACCESS", "T");
            assertTrue(hint.contains("runtime") || hint.contains("declaration"),
                "Hint should explain runtime vs declaration: " + hint);
        }
    }
}
