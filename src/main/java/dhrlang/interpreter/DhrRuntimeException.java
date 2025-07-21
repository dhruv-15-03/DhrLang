package dhrlang.interpreter;

import dhrlang.error.SourceLocation;


public class DhrRuntimeException extends RuntimeException {
    private final Object value;
    private final SourceLocation location;
    
    public DhrRuntimeException(Object value) {
        this(value, null);
    }
    
    public DhrRuntimeException(Object value, SourceLocation location) {
        super(formatMessage(value, location));
        this.value = value;
        this.location = location;
    }
    
    private static String formatMessage(Object value, SourceLocation location) {
        String valueStr = (value != null ? value.toString() : "null");
        if (location != null) {
            return "Exception at " + location + ": " + valueStr;
        }
        return "Exception: " + valueStr;
    }
    
    public Object getValue() {
        return value;
    }
    
    public SourceLocation getLocation() {
        return location;
    }
    
    @Override
    public String toString() {
        return getMessage();
    }
    
    
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DhrLang Runtime Exception ===\n");
        sb.append("Exception Type: User-thrown exception\n");
        sb.append("Exception Value: ").append(value != null ? value.toString() : "null").append("\n");
        if (value != null) {
            sb.append("Value Type: ").append(value.getClass().getSimpleName()).append("\n");
        }
        if (location != null) {
            sb.append("Location: ").append(location).append("\n");
        }
        sb.append("=================================");
        return sb.toString();
    }
}
