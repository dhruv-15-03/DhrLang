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
}
