package dhrlang.error;

import dhrlang.lexer.Token;


public class ErrorMessages {
    
    public static String getParseErrorHint(String expectedToken, Token actualToken) {
        String hint = null;
        
        if (expectedToken.equals(";")) {
            hint = "Try adding a semicolon at the end of the previous statement";
        } else if (expectedToken.equals("}")) {
            hint = "Check if you have matching opening and closing braces";
        } else if (expectedToken.equals(")")) {
            hint = "Check if you have matching opening and closing parentheses";
        } else if (expectedToken.contains("identifier")) {
            hint = "Expected a variable, function, or class name here";
        }
        
        return hint;
    }
    
    public static String getTypeErrorHint(String fromType, String toType) {
        if (fromType.equals("sab") && toType.equals("num")) {
            return "Use string-to-number conversion or check if this should be a string operation";
        } else if (fromType.equals("num") && toType.equals("sab")) {
            return "Use number-to-string conversion or string concatenation with '+'";
        } else if (fromType.equals("kya") && (toType.equals("num") || toType.equals("duo"))) {
            return "Boolean values cannot be used as numbers directly";
        }
        
        return "Check the types of your variables and expressions";
    }
    
    public static String getUndefinedVariableHint(String varName) {
        return "Make sure '" + varName + "' is declared before use, or check for typos";
    }
    
    public static String getArrayIndexErrorHint() {
        return "Array indices must be integers and within bounds";
    }
    
    public static String getFunctionCallErrorHint(String functionName, int expected, int actual) {
        return "Function '" + functionName + "' expects " + expected + " argument" + 
               (expected != 1 ? "s" : "") + " but got " + actual;
    }
    
    public static String getClassNotFoundHint(String className) {
        return "Make sure class '" + className + "' is defined or check for typos";
    }
    
    public static String getMethodNotFoundHint(String methodName, String className) {
        return "Method '" + methodName + "' is not defined in class '" + className + "'";
    }
    
    public static String getBreakContinueHint() {
        return "'break' and 'continue' can only be used inside loops";
    }
    
    public static String getReturnHint() {
        return "'return' can only be used inside functions";
    }
    
    public static String getInheritanceHint() {
        return "Check that the superclass exists and is properly defined";
    }
}
