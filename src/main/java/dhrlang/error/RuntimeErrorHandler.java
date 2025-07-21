package dhrlang.error;

import dhrlang.interpreter.DhrRuntimeException;


public class RuntimeErrorHandler {
    private static ErrorReporter errorReporter;
    
    public static void setErrorReporter(ErrorReporter reporter) {
        errorReporter = reporter;
    }
    
    
    public static DhrRuntimeException createException(String message, SourceLocation location) {
        DhrRuntimeException exception = new DhrRuntimeException(message, location);
        
        // Log to error reporter if available
        if (errorReporter != null && location != null) {
            errorReporter.error(location, "Runtime Error: " + message);
        }
        
        return exception;
    }
    
    
    public static DhrRuntimeException createException(String message) {
        return createException(message, null);
    }
    
    
    public static DhrRuntimeException createValueException(Object value, SourceLocation location) {
        DhrRuntimeException exception = new DhrRuntimeException(value, location);
        
        // Log to error reporter if available
        if (errorReporter != null && location != null) {
            errorReporter.error(location, "Runtime Exception: " + (value != null ? value.toString() : "null"));
        }
        
        return exception;
    }
    
    
    public static String formatErrorWithContext(DhrRuntimeException exception, String sourceCode) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== DhrLang Runtime Error ===\n");
        
        if (exception.getLocation() != null) {
            sb.append("Location: ").append(exception.getLocation()).append("\n");
            
            // Add source context if available
            if (sourceCode != null && !sourceCode.isEmpty()) {
                String context = getSourceContext(exception.getLocation(), sourceCode);
                if (!context.isEmpty()) {
                    sb.append("\nSource Context:\n").append(context);
                }
            }
        }
        
        sb.append("Error: ").append(exception.getMessage()).append("\n");
        sb.append("===============================");
        
        return sb.toString();
    }
    
    private static String getSourceContext(SourceLocation location, String sourceCode) {
        if (location == null || sourceCode == null || sourceCode.isEmpty()) {
            return "";
        }

        String[] lines = sourceCode.split("\n");
        int lineNum = location.getLine();
        
        if (lineNum < 1 || lineNum > lines.length) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        
        int startLine = Math.max(1, lineNum - 1);
        int endLine = Math.min(lines.length, lineNum + 1);
        
        for (int i = startLine; i <= endLine; i++) {
            String line = lines[i - 1];
            String lineNumStr = String.format("%4d", i);
            
            if (i == lineNum) {
                context.append(" -> ").append(lineNumStr).append(" | ").append(line).append("\n");
                
                if (location.getColumn() > 0) {
                    context.append("      | ");
                    for (int j = 0; j < location.getColumn() - 1; j++) {
                        context.append(" ");
                    }
                    context.append("^\n");
                }
            } else {
                context.append("    ").append(lineNumStr).append(" | ").append(line).append("\n");
            }
        }
        
        return context.toString();
    }
}
