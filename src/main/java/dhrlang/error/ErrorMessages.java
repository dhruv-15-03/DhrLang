package dhrlang.error;

import dhrlang.lexer.Token;


public class ErrorMessages {
    
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
    
    public static String getTypeErrorHint(String fromType, String toType) {
        if (fromType.equals("sab") && toType.equals("num")) {
            return "Use explicit conversion: parseNum(stringValue) or ensure this is a numeric string";
        } else if (fromType.equals("num") && toType.equals("sab")) {
            return "Use explicit conversion: toString(numberValue) or string concatenation with ''";
        } else if (fromType.equals("kya") && (toType.equals("num") || toType.equals("duo"))) {
            return "Boolean values cannot be used as numbers directly. Consider using conditional expressions";
        } else if (fromType.equals("null") && !toType.equals("null")) {
            return "Cannot assign null to non-nullable type. Check initialization or use nullable types";
        }
        
        return "Check the types of your variables and expressions. Consider explicit type conversion";
    }
    
    public static String getUndefinedVariableHint(String varName) {
        return "Make sure '" + varName + "' is declared before use. Check for typos or scope issues";
    }
    
    public static String getArrayIndexErrorHint() {
        return "Array indices must be non-negative integers and within bounds [0, array.length-1]";
    }
    
    public static String getFunctionCallErrorHint(String functionName, int expected, int actual) {
        String suffix = expected != 1 ? "s" : "";
        return "Function '" + functionName + "' expects " + expected + " argument" + suffix + 
               " but got " + actual + ". Check the function signature";
    }
    
    public static String getClassNotFoundHint(String className) {
        return "Make sure class '" + className + "' is defined in the current file or imported. Check for typos";
    }
    
    public static String getMethodNotFoundHint(String methodName, String className) {
        return "Method '" + methodName + "' is not defined in class '" + className + "'. Check method name and visibility";
    }
    
    public static String getBreakContinueHint() {
        return "'break' and 'continue' can only be used inside loops (for, while). Check your control flow";
    }
    
    public static String getReturnHint() {
        return "'return' can only be used inside functions. Check if you're in global scope";
    }
    
    public static String getInheritanceHint() {
        return "Check that the superclass exists, is properly defined, and accessible from current scope";
    }
    
    public static String getDivisionByZeroHint() {
        return "Division by zero is not allowed. Check if the divisor could be zero before dividing";
    }
    
    public static String getNullPointerHint(String operation) {
        return "Attempting to " + operation + " on null value. Check if the object is properly initialized";
    }
    
    public static String getAccessModifierHint(String member, String modifier) {
        return "'" + member + "' is " + modifier + " and cannot be accessed from this context. Check visibility rules";
    }
    
    public static String getArrayValidationErrorHint(String message) {
        if (message.contains("arrayPop") && message.contains("empty")) {
            return "Cannot remove elements from an empty array. Check array length before calling arrayPop()";
        } else if (message.contains("arrayAverage") && message.contains("empty")) {
            return "Cannot calculate average of empty array. Ensure the array has at least one element";
        } else if (message.contains("arrayFill") && message.contains("negative")) {
            return "Array size must be non-negative. Use a positive number for array size";
        } else if (message.contains("arraySlice") && message.contains("bounds")) {
            return "Array slice indices out of range. Ensure start >= 0, end <= array.length, and start <= end";
        } else if (message.contains("arrayInsert") && message.contains("bounds")) {
            return "Array insert index out of range. Index must be between 0 and array.length (inclusive)";
        }
        return "Check array operation parameters and ensure they are within valid ranges";
    }
    
    public static String getLexerErrorHint(String message) {
        if (message.contains("Unterminated string")) {
            return "Add a closing quote \" to complete the string literal";
        } else if (message.contains("Unexpected character")) {
            return "Check for typos or unsupported characters. DhrLang supports letters, numbers, and standard operators";
        } else if (message.contains("Invalid char literal")) {
            return "Character literals must be enclosed in single quotes like 'a' or '\\n'";
        } else if (message.contains("Unexpected character: '&'")) {
            return "Use '&&' for logical AND operations in DhrLang";
        } else if (message.contains("Unexpected character: '|'")) {
            return "Use '||' for logical OR operations in DhrLang";
        }
        return "Check the syntax and ensure all characters are valid in DhrLang";
    }
    
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
}
