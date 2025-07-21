package dhrlang.interpreter;

import dhrlang.error.SourceLocation;
import dhrlang.error.RuntimeErrorCategory;

public class DhrRuntimeException extends RuntimeException {
    private final Object value;
    private final SourceLocation location;
    private final RuntimeErrorCategory category;
    
    public DhrRuntimeException(Object value) {
        this(value, null, RuntimeErrorCategory.RUNTIME_ERROR);
    }
    
    public DhrRuntimeException(Object value, SourceLocation location) {
        this(value, location, RuntimeErrorCategory.RUNTIME_ERROR);
    }
    
    public DhrRuntimeException(Object value, SourceLocation location, RuntimeErrorCategory category) {
        super(formatMessage(value, location, category));
        this.value = value;
        this.location = location;
        this.category = category != null ? category : RuntimeErrorCategory.RUNTIME_ERROR;
    }
    
    public DhrRuntimeException(String message, SourceLocation location, RuntimeErrorCategory category) {
        this((Object) message, location, category);
    }
    
    public DhrRuntimeException(String message, SourceLocation location) {
        this((Object) message, location, RuntimeErrorCategory.RUNTIME_ERROR);
    }
    
    private static String formatMessage(Object value, SourceLocation location, RuntimeErrorCategory category) {
        String valueStr = (value != null ? value.toString() : "null");
        if (location != null) {
            return "[" + category.getDisplayName() + "] at " + location + ": " + valueStr;
        }
        return "[" + category.getDisplayName() + "]: " + valueStr;
    }
    
    public Object getValue() {
        return value;
    }
    
    public SourceLocation getLocation() {
        return location;
    }
    
    public RuntimeErrorCategory getCategory() {
        return category;
    }
    
    @Override
    public String toString() {
        return getMessage();
    }
    
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DhrLang Runtime Exception ===\n");
        sb.append("Category: ").append(category.getDisplayName()).append("\n");
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
