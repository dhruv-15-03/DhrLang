package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ContractValidator.
 * Part of Iteration 1: Foundation - Smart Contract Support.
 */
@DisplayName("Contract Validator Tests")
class ContractValidatorTest {

    private ContractValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ContractValidator();
    }

    private Program parse(String code) {
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    @Nested
    @DisplayName("@storage Validation Tests")
    class StorageValidationTests {
        
        @Test
        @DisplayName("@storage in @contract class is valid")
        void storageInContractIsValid() {
            String code = """
                @contract
                class Token {
                    @storage num totalSupply;
                    
                    @constructor
                    kaam init() {
                        totalSupply = 0;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertTrue(valid, "Storage in contract class should be valid");
            assertEquals(0, validator.getErrorCount());
        }
        
        @Test
        @DisplayName("@storage in non-contract class is error DHR-E502")
        void storageInNonContractIsError() {
            String code = """
                class NotAContract {
                    @storage num value;
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertFalse(valid, "Storage in non-contract class should be invalid");
            assertTrue(validator.hasError("DHR-E502"));
        }
    }

    @Nested
    @DisplayName("@immutable Validation Tests")
    class ImmutableValidationTests {
        
        @Test
        @DisplayName("@immutable in @contract class is valid")
        void immutableInContractIsValid() {
            String code = """
                @contract
                class Token {
                    @immutable sab name;
                    
                    @constructor
                    kaam init() {
                        name = "Token";
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertTrue(valid, "Immutable in contract class should be valid");
        }
        
        @Test
        @DisplayName("@immutable in non-contract class is error DHR-E503")
        void immutableInNonContractIsError() {
            String code = """
                class NotAContract {
                    @immutable sab name;
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertFalse(valid);
            assertTrue(validator.hasError("DHR-E503"));
        }
        
        @Test
        @DisplayName("@storage and @immutable together is error DHR-E504")
        void storageAndImmutableTogetherIsError() {
            String code = """
                @contract
                class Token {
                    @storage
                    @immutable num value;
                    
                    @constructor
                    kaam init() {
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertFalse(valid);
            assertTrue(validator.hasError("DHR-E504"));
        }
    }

    @Nested
    @DisplayName("@constructor Validation Tests")
    class ConstructorValidationTests {
        
        @Test
        @DisplayName("Single @constructor is valid")
        void singleConstructorIsValid() {
            String code = """
                @contract
                class Token {
                    @storage num value;
                    
                    @constructor
                    kaam init() {
                        value = 0;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertTrue(valid);
        }
        
        @Test
        @DisplayName("Multiple @constructors is error DHR-E505")
        void multipleConstructorsIsError() {
            String code = """
                @contract
                class Token {
                    @storage num value;
                    
                    @constructor
                    kaam init1() {
                        value = 0;
                    }
                    
                    @constructor
                    kaam init2() {
                        value = 100;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertFalse(valid);
            assertTrue(validator.hasError("DHR-E505"));
        }
        
        @Test
        @DisplayName("Contract without @constructor is error DHR-E506")
        void noConstructorIsError() {
            String code = """
                @contract
                class Token {
                    @storage num value;
                    
                    num getValue() {
                        return value;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertFalse(valid);
            assertTrue(validator.hasError("DHR-E506"));
        }
        
        @Test
        @DisplayName("@constructor in non-contract class is error DHR-E513")
        void constructorInNonContractIsError() {
            String code = """
                class NotAContract {
                    @constructor
                    kaam init() {
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertFalse(valid);
            assertTrue(validator.hasError("DHR-E513"));
        }
    }

    @Nested
    @DisplayName("@view and @pure Validation Tests")
    class ViewPureValidationTests {
        
        @Test
        @DisplayName("@view and @pure together is error DHR-E510")
        void viewAndPureTogetherIsError() {
            String code = """
                @contract
                class Token {
                    @storage num value;
                    
                    @constructor
                    kaam init() {
                    }
                    
                    @view
                    @pure
                    num badMethod() {
                        return 0;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertFalse(valid);
            assertTrue(validator.hasError("DHR-E510"));
        }
        
        @Test
        @DisplayName("@view method alone is valid")
        void viewMethodAloneIsValid() {
            String code = """
                @contract
                class Token {
                    @storage num value;
                    
                    @constructor
                    kaam init() {
                    }
                    
                    @view
                    num getValue() {
                        return value;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertTrue(valid);
        }
        
        @Test
        @DisplayName("@view in non-contract is error DHR-E514")
        void viewInNonContractIsError() {
            String code = """
                class NotAContract {
                    @view
                    num getValue() {
                        return 0;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertFalse(valid);
            assertTrue(validator.hasError("DHR-E514"));
        }
    }

    @Nested
    @DisplayName("@nonreentrant Validation Tests")
    class NonReentrantValidationTests {
        
        @Test
        @DisplayName("@nonreentrant on @view is warning DHR-E511")
        void nonReentrantOnViewIsWarning() {
            String code = """
                @contract
                class Token {
                    @storage num value;
                    
                    @constructor
                    kaam init() {
                    }
                    
                    @nonreentrant
                    @view
                    num getValue() {
                        return value;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertFalse(valid);
            assertTrue(validator.hasError("DHR-E511"));
        }
        
        @Test
        @DisplayName("@nonreentrant on regular method is valid")
        void nonReentrantOnRegularMethodIsValid() {
            String code = """
                @contract
                class Vault {
                    @storage num balance;
                    
                    @constructor
                    kaam init() {
                    }
                    
                    @nonreentrant
                    kaam withdraw() {
                        balance = 0;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertTrue(valid);
        }
    }

    @Nested
    @DisplayName("@payable Validation Tests")
    class PayableValidationTests {
        
        @Test
        @DisplayName("@payable on @pure is error DHR-E512")
        void payableOnPureIsError() {
            String code = """
                @contract
                class Token {
                    @constructor
                    kaam init() {
                    }
                    
                    @payable
                    @pure
                    num badMethod() {
                        return 0;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertFalse(valid);
            assertTrue(validator.hasError("DHR-E512"));
        }
        
        @Test
        @DisplayName("@payable method is valid")
        void payableMethodIsValid() {
            String code = """
                @contract
                class Donation {
                    @storage num total;
                    
                    @constructor
                    kaam init() {
                    }
                    
                    @payable
                    kaam donate() {
                        total = total + 1;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertTrue(valid);
        }
    }

    @Nested
    @DisplayName("Complete Contract Validation Tests")
    class CompleteContractTests {
        
        @Test
        @DisplayName("Valid ERC20-like contract passes validation")
        void validErc20LikeContractPasses() {
            String code = """
                @contract
                class Token {
                    @immutable sab name;
                    @storage num totalSupply;
                    
                    @constructor
                    kaam init(sab tokenName) {
                        name = tokenName;
                        totalSupply = 1000000;
                    }
                    
                    @view
                    sab getName() {
                        return name;
                    }
                    
                    @view
                    num getTotalSupply() {
                        return totalSupply;
                    }
                    
                    @nonreentrant
                    kaam transfer(num amount) {
                        // Transfer logic
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertTrue(valid, "Valid contract should pass validation. Errors: " + validator.getErrors());
            assertEquals(0, validator.getErrorCount());
        }
        
        @Test
        @DisplayName("Non-contract class passes validation")
        void nonContractClassPasses() {
            String code = """
                class RegularClass {
                    num value;
                    
                    num getValue() {
                        return value;
                    }
                }
                """;
            
            Program program = parse(code);
            boolean valid = validator.validate(program);
            
            assertTrue(valid);
            assertEquals(0, validator.getErrorCount());
        }
    }

    @Nested
    @DisplayName("Error Reporting Tests")
    class ErrorReportingTests {
        
        @Test
        @DisplayName("Multiple errors are all reported")
        void multipleErrorsReported() {
            String code = """
                class BadContract {
                    @storage num value;
                    @immutable sab name;
                    
                    @constructor
                    kaam init() {
                    }
                    
                    @view
                    num getValue() {
                        return value;
                    }
                }
                """;
            
            Program program = parse(code);
            validator.validate(program);
            
            // Should have multiple errors:
            // - @storage in non-contract (DHR-E502)
            // - @immutable in non-contract (DHR-E503)
            // - @constructor in non-contract (DHR-E513)
            // - @view in non-contract (DHR-E514)
            assertTrue(validator.getErrorCount() >= 4, 
                "Should have at least 4 errors, got: " + validator.getErrorCount());
        }
        
        @Test
        @DisplayName("clearErrors() clears all errors")
        void clearErrorsWorks() {
            String code = """
                class BadContract {
                    @storage num value;
                }
                """;
            
            Program program = parse(code);
            validator.validate(program);
            assertTrue(validator.getErrorCount() > 0);
            
            validator.clearErrors();
            assertEquals(0, validator.getErrorCount());
        }
    }
}
