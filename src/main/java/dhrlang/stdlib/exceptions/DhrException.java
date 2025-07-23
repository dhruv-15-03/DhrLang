package dhrlang.stdlib.exceptions;

import dhrlang.error.SourceLocation;

/**
 * Base class for all DhrLang exceptions that can be thrown and caught
 */
public class DhrException {
    private final String message;
    private final SourceLocation location;
    private final String exceptionType;

    public DhrException(String message) {
        this("DhrException", message, null);
    }

    public DhrException(String message, SourceLocation location) {
        this("DhrException", message, location);
    }

    public DhrException(String exceptionType, String message, SourceLocation location) {
        this.exceptionType = exceptionType;
        this.message = message;
        this.location = location;
    }

    public String getMessage() {
        return message;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(exceptionType).append(": ").append(message);
        if (location != null) {
            sb.append(" at ").append(location);
        }
        return sb.toString();
    }

    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ ").append(exceptionType).append(" ═══\n");
        sb.append("Message: ").append(message).append("\n");
        if (location != null) {
            sb.append("Location: ").append(location).append("\n");
        }
        sb.append("════════════════════════════════════");
        return sb.toString();
    }
}
