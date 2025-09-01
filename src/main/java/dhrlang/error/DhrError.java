package dhrlang.error;


public class DhrError {
    private final ErrorType type;
    private final SourceLocation location;
    private final String message;
    private final String hint;
    private final ErrorCode code;

    public DhrError(ErrorType type, SourceLocation location, String message) {
        this(type, location, message, null, null);
    }

    public DhrError(ErrorType type, SourceLocation location, String message, String hint) {
        this(type, location, message, hint, null);
    }

    public DhrError(ErrorType type, SourceLocation location, String message, String hint, ErrorCode code) {
        this.type = type;
        this.location = location;
        this.message = message;
        this.hint = hint;
        this.code = code;
    }

    public ErrorType getType() {
        return type;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }

    public String getHint() {
        return hint;
    }

    public ErrorCode getCode() { return code; }

    public boolean hasHint() {
        return hint != null && !hint.trim().isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.toString().toLowerCase()).append(": ");
        if (location != null) {
            sb.append("[").append(location.toString()).append("] ");
        }
        sb.append(message);
    if (code != null) sb.append(" [").append(code).append("]");
    if (hasHint()) sb.append(" (Hint: ").append(hint).append(")");
        return sb.toString();
    }
}
