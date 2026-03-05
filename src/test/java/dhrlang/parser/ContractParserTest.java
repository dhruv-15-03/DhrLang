package dhrlang.parser;

import dhrlang.ast.*;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for smart contract annotation parsing.
 * Part of Iteration 1: Foundation - Smart Contract Support.
 */
@DisplayName("Smart Contract Parser Tests")
class ContractParserTest {

    private Program parse(String code) {
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    @Nested
    @DisplayName("@contract Annotation Tests")
    class ContractAnnotationTests {
        
        @Test
        @DisplayName("Parse @contract class annotation")
        void parseContractAnnotation() {
            String code = """
                @contract
                class Token {
                    num value;
                    
                    num getValue() {
                        return value;
                    }
                }
                """;
            
            Program program = parse(code);
            assertEquals(1, program.getClasses().size());
            
            ClassDecl classDecl = program.getClasses().get(0);
            assertEquals("Token", classDecl.getName());
            assertTrue(classDecl.isContract(), "Class should be marked as @contract");
            assertTrue(classDecl.hasContractAnnotation(ContractAnnotation.CONTRACT));
        }
        
        @Test
        @DisplayName("Parse class without @contract annotation")
        void parseNonContractClass() {
            String code = """
                class RegularClass {
                    num value;
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            assertFalse(classDecl.isContract(), "Class should not be marked as @contract");
        }
    }

    @Nested
    @DisplayName("@storage Annotation Tests")
    class StorageAnnotationTests {
        
        @Test
        @DisplayName("Parse @storage field annotation")
        void parseStorageField() {
            String code = """
                @contract
                class Token {
                    @storage num totalSupply;
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            
            assertEquals(1, classDecl.getVariables().size());
            VarDecl field = classDecl.getVariables().get(0);
            
            assertEquals("totalSupply", field.getName());
            assertEquals("num", field.getType());
            assertTrue(field.isStorage(), "Field should be marked as @storage");
        }
        
        @Test
        @DisplayName("Parse multiple @storage fields")
        void parseMultipleStorageFields() {
            String code = """
                @contract
                class Token {
                    @storage num totalSupply;
                    @storage sab name;
                    num regularField;
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            
            assertEquals(3, classDecl.getVariables().size());
            
            VarDecl totalSupply = classDecl.getVariables().get(0);
            assertTrue(totalSupply.isStorage());
            
            VarDecl name = classDecl.getVariables().get(1);
            assertTrue(name.isStorage());
            
            VarDecl regularField = classDecl.getVariables().get(2);
            assertFalse(regularField.isStorage());
        }
    }

    @Nested
    @DisplayName("@view and @pure Annotation Tests")
    class ViewPureAnnotationTests {
        
        @Test
        @DisplayName("Parse @view method annotation")
        void parseViewMethod() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    
                    @view
                    num getBalance() {
                        return balance;
                    }
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            FunctionDecl method = classDecl.getFunctions().get(0);
            
            assertEquals("getBalance", method.getName());
            assertTrue(method.isView(), "Method should be marked as @view");
            assertFalse(method.isPure());
        }
        
        @Test
        @DisplayName("Parse @pure method annotation")
        void parsePureMethod() {
            String code = """
                @contract
                class Math {
                    @pure
                    num add(num a, num b) {
                        return a + b;
                    }
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            FunctionDecl method = classDecl.getFunctions().get(0);
            
            assertEquals("add", method.getName());
            assertTrue(method.isPure(), "Method should be marked as @pure");
            assertFalse(method.isView());
        }
    }

    @Nested
    @DisplayName("@payable Annotation Tests")
    class PayableAnnotationTests {
        
        @Test
        @DisplayName("Parse @payable method annotation")
        void parsePayableMethod() {
            String code = """
                @contract
                class Donation {
                    @payable
                    kaam donate() {
                        // Accept ETH
                    }
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            FunctionDecl method = classDecl.getFunctions().get(0);
            
            assertEquals("donate", method.getName());
            assertTrue(method.isPayable(), "Method should be marked as @payable");
        }
    }

    @Nested
    @DisplayName("@nonreentrant Annotation Tests")
    class NonReentrantAnnotationTests {
        
        @Test
        @DisplayName("Parse @nonreentrant method annotation")
        void parseNonReentrantMethod() {
            String code = """
                @contract
                class Vault {
                    @nonreentrant
                    kaam withdraw(num amount) {
                        // Safe withdrawal
                    }
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            FunctionDecl method = classDecl.getFunctions().get(0);
            
            assertEquals("withdraw", method.getName());
            assertTrue(method.isNonReentrant(), "Method should be marked as @nonreentrant");
        }
    }

    @Nested
    @DisplayName("@constructor Annotation Tests")
    class ConstructorAnnotationTests {
        
        @Test
        @DisplayName("Parse @constructor method annotation")
        void parseConstructorMethod() {
            String code = """
                @contract
                class Token {
                    @storage sab name;
                    
                    @constructor
                    kaam init(sab tokenName) {
                        name = tokenName;
                    }
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            FunctionDecl method = classDecl.getFunctions().get(0);
            
            assertEquals("init", method.getName());
            assertTrue(method.isContractConstructor(), "Method should be marked as @constructor");
        }
    }

    @Nested
    @DisplayName("@immutable Annotation Tests")
    class ImmutableAnnotationTests {
        
        @Test
        @DisplayName("Parse @immutable field annotation")
        void parseImmutableField() {
            String code = """
                @contract
                class Token {
                    @immutable sab name;
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            VarDecl field = classDecl.getVariables().get(0);
            
            assertEquals("name", field.getName());
            assertTrue(field.isImmutable(), "Field should be marked as @immutable");
        }
    }

    @Nested
    @DisplayName("Multiple Annotations Tests")
    class MultipleAnnotationsTests {
        
        @Test
        @DisplayName("Parse method with multiple annotations")
        void parseMultipleMethodAnnotations() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    
                    @payable
                    @nonreentrant
                    kaam deposit() {
                        // Safe deposit
                    }
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            FunctionDecl method = classDecl.getFunctions().get(0);
            
            assertTrue(method.isPayable(), "Method should be @payable");
            assertTrue(method.isNonReentrant(), "Method should be @nonreentrant");
        }
        
        @Test
        @DisplayName("Parse complete contract with all annotation types")
        void parseCompleteContract() {
            String code = """
                @contract
                class ERC20 {
                    @immutable sab name;
                    @storage num totalSupply;
                    
                    @constructor
                    kaam init(sab tokenName) {
                        name = tokenName;
                        totalSupply = 0;
                    }
                    
                    @view
                    num getTotalSupply() {
                        return totalSupply;
                    }
                    
                    @pure
                    num add(num a, num b) {
                        return a + b;
                    }
                    
                    @nonreentrant
                    kaam transfer(num amount) {
                        // Transfer logic
                    }
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            
            // Class validation
            assertTrue(classDecl.isContract());
            assertEquals("ERC20", classDecl.getName());
            
            // Field validation
            assertEquals(2, classDecl.getVariables().size());
            assertTrue(classDecl.getVariables().get(0).isImmutable());
            assertTrue(classDecl.getVariables().get(1).isStorage());
            
            // Method validation
            assertEquals(4, classDecl.getFunctions().size());
            
            FunctionDecl init = classDecl.getFunctions().get(0);
            assertTrue(init.isContractConstructor());
            
            FunctionDecl getTotalSupply = classDecl.getFunctions().get(1);
            assertTrue(getTotalSupply.isView());
            
            FunctionDecl add = classDecl.getFunctions().get(2);
            assertTrue(add.isPure());
            
            FunctionDecl transfer = classDecl.getFunctions().get(3);
            assertTrue(transfer.isNonReentrant());
        }
    }

    @Nested
    @DisplayName("Annotation with Access Modifiers Tests")
    class AnnotationWithModifiersTests {
        
        @Test
        @DisplayName("Parse annotations before access modifiers")
        void parseAnnotationsBeforeModifiers() {
            String code = """
                @contract
                class Token {
                    @storage
                    private num secret;
                    
                    @view
                    public num getSecret() {
                        return secret;
                    }
                }
                """;
            
            Program program = parse(code);
            ClassDecl classDecl = program.getClasses().get(0);
            
            VarDecl field = classDecl.getVariables().get(0);
            assertTrue(field.isStorage());
            assertTrue(field.hasModifier(Modifier.PRIVATE));
            
            FunctionDecl method = classDecl.getFunctions().get(0);
            assertTrue(method.isView());
            assertTrue(method.hasModifier(Modifier.PUBLIC));
        }
    }
}
