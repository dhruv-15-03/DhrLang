package dhrlang.interpreter;

import dhrlang.error.SourceLocation;

/**
 * Represents a stack frame for DhrLang execution context
 */
public class StackFrame {
    private final String functionName;
    private final String className;
    private final SourceLocation location;
    
    public StackFrame(String functionName, String className, SourceLocation location) {
        this.functionName = functionName;
        this.className = className;
        this.location = location;
    }
    
    public String getFunctionName() {
        return functionName;
    }
    
    public String getClassName() {
        return className;
    }
    
    public SourceLocation getLocation() {
        return location;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (className != null) {
            sb.append(className).append(".");
        }
        sb.append(functionName != null ? functionName : "<unknown>");
        if (location != null) {
            sb.append(" (").append(location).append(")");
        }
        return sb.toString();
    }
}
