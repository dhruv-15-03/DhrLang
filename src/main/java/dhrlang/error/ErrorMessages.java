package dhrlang.error;

import dhrlang.lexer.Token;

/**
 * Comprehensive error hint generator for DhrLang.
 * 
 * This class provides contextual, actionable hints for all error types,
 * making DhrLang one of the most developer-friendly languages for beginners.
 * 
 * Each hint includes:
 * 1. Clear explanation of the problem
 * 2. Actionable suggestion to fix it
 * 3. Example of correct syntax (when applicable)
 */
public class ErrorMessages {
    
    /**
     * Generate a helpful hint for parse errors based on the error message.
     */
    public static String getParseErrorHint(String message, Token actualToken) {
        String hint = null;
        
        if (message.contains("Expected ';'") || message.contains("';'")) {
            hint = "Try adding a semicolon ';' at the end of the previous statement";
        } else if (message.contains("Expected '}'") || message.contains("'}'")) {
            hint = "Check if you have matching opening '{' and closing '}' braces";
        } else if (message.contains("Expected ')'") || message.contains("')'")) {
            hint = "Check if you have matching opening '(' and closing ')' parentheses";
        } else if (message.contains("identifier") || message.contains("name")) {
            hint = "Expected a variable, function, or class name here. Make sure it starts with a letter or underscore";
        } else if (message.contains("Expected '{'") || message.contains("'{'")) {
            hint = "Expected opening brace '{' for block statement";
        } else if (message.contains("Expected '('") || message.contains("'('")) {
            hint = "Expected opening parenthesis '(' for function call or expression";
        } else if (message.contains("Expected 'class'")) {
            hint = "All code must be inside classes. Start with 'class ClassName { ... }'";
        } else if (message.contains("Expected type")) {
            hint = "Specify a type like 'num', 'sab', 'kya', 'duo' before the variable name";
        } else if (message.contains("Expected expression")) {
            hint = "Provide a valid expression like a variable, literal, or function call";
        } else if (message.contains("Invalid assignment target")) {
            hint = "You can only assign to variables, object properties, or array elements";
        } else if (message.contains("try statement must have")) {
            hint = "Add either 'catch (e) { ... }' or 'finally { ... }' after the try block";
        } else if (message.contains("Duplicate modifier")) {
            hint = "Remove the duplicate modifier. Each modifier (public, private, static, etc.) can only appear once";
        } else if (message.contains("prefix") || message.contains("increment") || message.contains("decrement")) {
            hint = "Prefix/postfix operators (++ and --) can only be used with variables, not literals or expressions";
        } else if (message.contains("field or method declaration")) {
            hint = "Inside a class, you can only declare fields (variables) or methods (functions)";
        } else if (message.contains("Expected 'interface'")) {
            hint = "Use 'interface InterfaceName { ... }' to declare an interface";
        } else if (message.contains("Interface can only contain method declarations")) {
            hint = "Interfaces cannot have fields, only method signatures like 'num methodName();'";
        } else if (message.contains("method declaration in interface")) {
            hint = "Declare methods in interfaces like: 'returnType methodName(parameters);' without implementation";
        } else if (message.contains("implements")) {
            hint = "Use 'class ClassName implements InterfaceName { ... }' to implement an interface";
        } else if (message.contains("must implement")) {
            hint = "Provide implementations for all methods declared in the implemented interface(s)";
        } else if (message.contains("@Override")) {
            hint = "Use '@Override' annotation before methods that override parent class or interface methods";
        }
        
        // If no specific hint found, provide a generic helpful hint
        if (hint == null) {
            if (actualToken != null) {
                hint = "Syntax error near '" + actualToken.getLexeme() + "'. Check the DhrLang syntax guide for correct usage";
            } else {
                hint = "Check your syntax. Make sure all parentheses, braces, and semicolons are properly matched";
            }
        }
        
        return hint;
    }
    
    /**
     * Generate hint for type mismatch errors with specific conversion suggestions.
     */
    public static String getTypeErrorHint(String fromType, String toType) {
        if (fromType.equals("sab") && toType.equals("num")) {
            return "Use explicit conversion: parseNum(stringValue) or ensure this is a numeric string";
        } else if (fromType.equals("num") && toType.equals("sab")) {
            return "Use explicit conversion: toString(numberValue) or string concatenation with ''";
        } else if (fromType.equals("kya") && (toType.equals("num") || toType.equals("duo"))) {
            return "Boolean values cannot be used as numbers directly. Consider using conditional expressions";
        } else if (fromType.equals("null") && !toType.equals("null")) {
            return "Cannot assign null to non-nullable type. Check initialization or use nullable types";
        } else if (fromType.equals("duo") && toType.equals("num")) {
            return "Cannot assign duo (decimal) to num (integer) without truncation. Use floor() or round()";
        } else if (fromType.equals("num") && toType.equals("duo")) {
            return "This conversion is safe - num can be assigned to duo. Check if variable is declared correctly";
        }
        
        return "Check the types of your variables and expressions. Consider explicit type conversion";
    }
    
    /**
     * Generate hint for undefined variable errors with typo detection.
     */
    public static String getUndefinedVariableHint(String varName) {
        StringBuilder hint = new StringBuilder();
        hint.append("Variable '").append(varName).append("' is not declared. ");
        
        // Common typo suggestions
        if (varName.equalsIgnoreCase("string")) {
            hint.append("Did you mean 'sab' (DhrLang string type)?");
        } else if (varName.equalsIgnoreCase("int") || varName.equalsIgnoreCase("integer")) {
            hint.append("Did you mean 'num' (DhrLang integer type)?");
        } else if (varName.equalsIgnoreCase("bool") || varName.equalsIgnoreCase("boolean")) {
            hint.append("Did you mean 'kya' (DhrLang boolean type)?");
        } else if (varName.equalsIgnoreCase("double") || varName.equalsIgnoreCase("float")) {
            hint.append("Did you mean 'duo' (DhrLang decimal type)?");
        } else if (varName.equalsIgnoreCase("void")) {
            hint.append("Did you mean 'kaam' (DhrLang void type)?");
        } else if (varName.equalsIgnoreCase("char") || varName.equalsIgnoreCase("character")) {
            hint.append("Did you mean 'ek' (DhrLang character type)?");
        } else {
            hint.append("Declare it first with: 'num ").append(varName).append(" = value;'");
        }
        
        return hint.toString();
    }
    
    /**
     * Generate hint for array index errors with bounds information.
     */
    public static String getArrayIndexErrorHint() {
        return "Array indices must be non-negative integers and within bounds [0, arrayLength(arr)-1]. " +
               "Use 'if(index < arrayLength(arr))' to check bounds before accessing";
    }
    
    /**
     * Generate detailed hint for array index errors with specific bounds.
     */
    public static String getArrayIndexErrorHint(int index, int arrayLength) {
        if (index < 0) {
            return String.format("Index %d is negative. Array indices start at 0. " +
                    "Valid range: [0, %d]", index, arrayLength - 1);
        } else {
            return String.format("Index %d exceeds array bounds. Array has %d elements, " +
                    "valid range: [0, %d]. Check your loop condition or index calculation", 
                    index, arrayLength, arrayLength - 1);
        }
    }
    
    /**
     * Generate hint for function call errors.
     */
    public static String getFunctionCallErrorHint(String functionName, int expected, int actual) {
        String suffix = expected != 1 ? "s" : "";
        StringBuilder hint = new StringBuilder();
        hint.append("Function '").append(functionName).append("' expects ")
            .append(expected).append(" argument").append(suffix)
            .append(" but got ").append(actual).append(". ");
        
        if (actual < expected) {
            hint.append("Add ").append(expected - actual).append(" more argument(s)");
        } else {
            hint.append("Remove ").append(actual - expected).append(" argument(s)");
        }
        
        return hint.toString();
    }
    
    /**
     * Generate hint for class not found errors.
     */
    public static String getClassNotFoundHint(String className) {
        return "Class '" + className + "' is not defined. Make sure it's declared in the current file. " +
               "Class names are case-sensitive";
    }
    
    /**
     * Generate hint for method not found errors.
     */
    public static String getMethodNotFoundHint(String methodName, String className) {
        return "Method '" + methodName + "' is not defined in class '" + className + "'. " +
               "Check method name spelling and visibility (public/private)";
    }
    
    /**
     * Generate hint for break/continue errors.
     */
    public static String getBreakContinueHint() {
        return "'break' and 'continue' can only be used inside loops (for, while). " +
               "Check if you're inside a loop, not just an if-statement";
    }
    
    /**
     * Generate hint for return statement errors.
     */
    public static String getReturnHint() {
        return "'return' can only be used inside functions. Check if you're in global scope";
    }
    
    /**
     * Generate hint for inheritance errors.
     */
    public static String getInheritanceHint() {
        return "Check that the superclass exists, is properly defined, and accessible from current scope";
    }
    
    /**
     * Generate hint for division by zero.
     */
    public static String getDivisionByZeroHint() {
        return "Division by zero is not allowed. Add a check: 'if(divisor != 0)' before dividing";
    }
    
    /**
     * Generate hint for null pointer errors.
     */
    public static String getNullPointerHint(String operation) {
        return "Attempting to " + operation + " on null value. " +
               "Check if the object is properly initialized with 'new ClassName()' or is null";
    }
    
    /**
     * Generate hint for access modifier violations.
     */
    public static String getAccessModifierHint(String member, String modifier) {
        return "'" + member + "' is " + modifier + " and cannot be accessed from this context. " +
               "Consider making it 'public' or accessing it through a public method";
    }
    
    /**
     * Generate hint for array validation errors.
     */
    public static String getArrayValidationErrorHint(String message) {
        if (message.contains("arrayPop") && message.contains("empty")) {
            return "Cannot remove elements from an empty array. Check array length before calling arrayPop()";
        } else if (message.contains("arrayAverage") && message.contains("empty")) {
            return "Cannot calculate average of empty array. Ensure the array has at least one element";
        } else if (message.contains("arrayFill") && message.contains("negative")) {
            return "Array size must be non-negative. Use a positive number for array size";
        } else if (message.contains("arraySlice") && message.contains("bounds")) {
            return "Array slice indices out of range. Ensure start >= 0, end <= arrayLength(arr), and start <= end";
        } else if (message.contains("arrayInsert") && message.contains("bounds")) {
            return "Array insert index out of range. Index must be between 0 and arrayLength(arr) (inclusive)";
        } else if (message.contains("negative") && message.contains("size")) {
            return "Array size cannot be negative. Use a non-negative integer for array dimensions";
        } else if (message.contains("too large")) {
            return "Array size exceeds maximum allowed (1,000,000 elements). Consider using smaller arrays or chunking data";
        }
        return "Check array operation parameters and ensure they are within valid ranges";
    }
    
    /**
     * Generate hint for lexer errors.
     */
    public static String getLexerErrorHint(String message) {
        if (message.contains("Unterminated string")) {
            return "Add a closing quote \" to complete the string literal. Strings must be enclosed in double quotes";
        } else if (message.contains("Unexpected character: '&'")) {
            return "Use '&&' for logical AND operations in DhrLang (two ampersands, not one)";
        } else if (message.contains("Unexpected character: '|'")) {
            return "Use '||' for logical OR operations in DhrLang (two pipes, not one)";
        } else if (message.contains("Unexpected character")) {
            return "Check for typos or unsupported characters. DhrLang supports letters, numbers, and standard operators (+, -, *, /, %, =, <, >, !)";
        } else if (message.contains("Invalid char literal")) {
            return "Character literals use single quotes and contain one character: 'a', '\\n', '\\t'. " +
                   "Use double quotes for strings";
        }
        return "Check the syntax and ensure all characters are valid in DhrLang";
    }
    
    /**
     * Generate hint for interface errors.
     */
    public static String getInterfaceErrorHint(String errorType) {
        switch (errorType) {
            case "DUPLICATE_INTERFACE":
                return "Each interface name must be unique. Choose a different name for your interface";
            case "NAME_CONFLICT":
                return "Classes and interfaces cannot share the same name. Use different names to avoid conflicts";
            case "INTERFACE_METHOD_BODY":
                return "Interface methods should only declare signatures. Remove the method body and end with a semicolon";
            case "INTERFACE_PRIVATE_METHOD":
                return "Interface methods are implicitly public. Remove the 'private' modifier";
            case "INTERFACE_STATIC_METHOD":
                return "Interface methods define contracts for instances. Static methods are not allowed";
            case "INTERFACE_FINAL_METHOD":
                return "Interface methods are meant to be implemented by classes. Final methods cannot be overridden";
            case "MISSING_IMPLEMENTATION":
                return "Implementing classes must provide concrete implementations for all interface methods";
            case "SIGNATURE_MISMATCH":
                return "Method signatures must match exactly: same return type, method name, and parameter types";
            case "UNDEFINED_INTERFACE":
                return "Make sure the interface is declared before implementing it, and check for typos";
            default:
                return "Follow proper interface syntax and implementation rules";
        }
    }
    
    /**
     * Generate hint for multi-dimensional array errors.
     */
    public static String getMultiDimArrayHint(String errorType) {
        switch (errorType) {
            case "DIMENSION_MISMATCH":
                return "Multi-dimensional array access must match declared dimensions. " +
                       "For num[][] m, access as m[i][j], not m[i] or m[i][j][k]";
            case "JAGGED_NULL":
                return "Inner array is null. For jagged arrays, allocate each row: arr[i] = new num[size];";
            case "IR_NOT_SUPPORTED":
                return "Multi-dimensional array allocation is not yet supported in IR backend. " +
                       "Use --backend=ast or allocate dimensions manually";
            default:
                return "Check array dimensions and ensure all indices are valid";
        }
    }
    
    /**
     * Generate hint for generic type errors.
     */
    public static String getGenericTypeHint(String errorType, String typeName) {
        switch (errorType) {
            case "MISSING_TYPE_ARG":
                return "Generic type '" + typeName + "' requires type arguments. " +
                       "Example: List<num> instead of just List";
            case "WRONG_ARITY":
                return "Wrong number of type arguments for '" + typeName + "'. " +
                       "Check the generic type declaration for required parameters";
            case "RUNTIME_ACCESS":
                return "Generic types are used in declarations, not as runtime values. " +
                       "Use 'new ClassName<Type>()' to create instances";
            default:
                return "Check generic type syntax and ensure type parameters are correctly specified";
        }
    }
}

