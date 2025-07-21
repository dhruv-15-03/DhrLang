package dhrlang.error;


public enum RuntimeErrorCategory {
    TYPE_ERROR("Type Error"),
    RUNTIME_ERROR("Runtime Error"), 
    USER_EXCEPTION("User Exception"),
    SYSTEM_ERROR("System Error"),
    VALIDATION_ERROR("Validation Error"),
    ACCESS_ERROR("Access Error"),
    ARITHMETIC_ERROR("Arithmetic Error"),
    INDEX_ERROR("Index Error"),
    NULL_ERROR("Null Reference Error");
    
    private final String displayName;
    
    RuntimeErrorCategory(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
