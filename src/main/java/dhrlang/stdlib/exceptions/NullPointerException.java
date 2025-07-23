package dhrlang.stdlib.exceptions;

import dhrlang.error.SourceLocation;

/**
 * Thrown when a null pointer is accessed
 */
public class NullPointerException extends DhrException {
    public NullPointerException(String message) {
        super("NullPointerException", message, null);
    }

    public NullPointerException(String message, SourceLocation location) {
        super("NullPointerException", message, location);
    }
}
