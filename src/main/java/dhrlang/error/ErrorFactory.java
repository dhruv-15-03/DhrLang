package dhrlang.error;

import dhrlang.lexer.Token;
import dhrlang.ast.*;
import dhrlang.interpreter.DhrRuntimeException;


public class ErrorFactory {
    
    
    public static DhrRuntimeException typeError(String message, Token token) {
        return new DhrRuntimeException(message, 
            token != null ? token.getLocation() : null, 
            RuntimeErrorCategory.TYPE_ERROR);
    }
    
    public static DhrRuntimeException typeError(String message, SourceLocation location) {
        return new DhrRuntimeException(message, location, RuntimeErrorCategory.TYPE_ERROR);
    }
    
    public static DhrRuntimeException indexError(String message, Token token) {
        return new DhrRuntimeException(message, 
            token != null ? token.getLocation() : null, 
            RuntimeErrorCategory.INDEX_ERROR);
    }
    
    public static DhrRuntimeException indexError(String message, SourceLocation location) {
        return new DhrRuntimeException(message, location, RuntimeErrorCategory.INDEX_ERROR);
    }
    
    public static DhrRuntimeException arithmeticError(String message, Token token) {
        return new DhrRuntimeException(message, 
            token != null ? token.getLocation() : null, 
            RuntimeErrorCategory.ARITHMETIC_ERROR);
    }
    
    public static DhrRuntimeException arithmeticError(String message, SourceLocation location) {
        return new DhrRuntimeException(message, location, RuntimeErrorCategory.ARITHMETIC_ERROR);
    }
    
    public static DhrRuntimeException nullError(String message, Token token) {
        return new DhrRuntimeException(message, 
            token != null ? token.getLocation() : null, 
            RuntimeErrorCategory.NULL_ERROR);
    }
    
    public static DhrRuntimeException nullError(String message, SourceLocation location) {
        return new DhrRuntimeException(message, location, RuntimeErrorCategory.NULL_ERROR);
    }
    
    public static DhrRuntimeException accessError(String message, Token token) {
        return new DhrRuntimeException(message, 
            token != null ? token.getLocation() : null, 
            RuntimeErrorCategory.ACCESS_ERROR);
    }
    
    public static DhrRuntimeException accessError(String message, SourceLocation location) {
        return new DhrRuntimeException(message, location, RuntimeErrorCategory.ACCESS_ERROR);
    }
    
    public static DhrRuntimeException validationError(String message, Token token) {
        return new DhrRuntimeException(message, 
            token != null ? token.getLocation() : null, 
            RuntimeErrorCategory.VALIDATION_ERROR);
    }
    
    public static DhrRuntimeException validationError(String message, SourceLocation location) {
        return new DhrRuntimeException(message, location, RuntimeErrorCategory.VALIDATION_ERROR);
    }
    
    public static DhrRuntimeException runtimeError(String message, Token token) {
        return new DhrRuntimeException(message, 
            token != null ? token.getLocation() : null, 
            RuntimeErrorCategory.RUNTIME_ERROR);
    }
    
    public static DhrRuntimeException runtimeError(String message, SourceLocation location) {
        return new DhrRuntimeException(message, location, RuntimeErrorCategory.RUNTIME_ERROR);
    }
    
    public static DhrRuntimeException userException(Object value, Token token) {
        return new DhrRuntimeException(value, 
            token != null ? token.getLocation() : null, 
            RuntimeErrorCategory.USER_EXCEPTION);
    }
    
    public static DhrRuntimeException userException(Object value, SourceLocation location) {
        return new DhrRuntimeException(value, location, RuntimeErrorCategory.USER_EXCEPTION);
    }
    
    public static DhrRuntimeException systemError(String message, Token token) {
        return new DhrRuntimeException(message, 
            token != null ? token.getLocation() : null, 
            RuntimeErrorCategory.SYSTEM_ERROR);
    }
    
    public static DhrRuntimeException systemError(String message, SourceLocation location) {
        return new DhrRuntimeException(message, location, RuntimeErrorCategory.SYSTEM_ERROR);
    }
    
    
    public static SourceLocation getLocation(Expression expr) {
        if (expr != null) {
            SourceLocation location = expr.getSourceLocation();
            if (location != null) {
                return location;
            }
            
            if (expr instanceof VariableExpr varExpr) {
                return varExpr.getName().getLocation();
            } else if (expr instanceof BinaryExpr binExpr) {
                return binExpr.getOperator().getLocation();
            } else if (expr instanceof UnaryExpr unaryExpr) {
                return unaryExpr.getOperator().getLocation();
            }
        }
        return null;
    }
    
    public static SourceLocation getLocation(Statement stmt) {
        if (stmt != null) {
            SourceLocation location = stmt.getSourceLocation();
            if (location != null) {
                return location;
            }
            
            if (stmt instanceof ExpressionStmt exprStmt) {
                return getLocation(exprStmt.getExpression());
            } else if (stmt instanceof PrintStmt printStmt) {
                return getLocation(printStmt.getExpression());
            } else if (stmt instanceof IfStmt ifStmt) {
                return getLocation(ifStmt.getCondition());
            } else if (stmt instanceof WhileStmt whileStmt) {
                return getLocation(whileStmt.getCondition());
            } else if (stmt instanceof ReturnStmt returnStmt) {
                return returnStmt.getValue() != null ? getLocation(returnStmt.getValue()) : null;
            }
        }
        return null;
    }
}
