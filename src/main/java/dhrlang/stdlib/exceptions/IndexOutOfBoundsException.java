package dhrlang.stdlib.exceptions;

import dhrlang.error.SourceLocation;

/**
 * Thrown when array index is out of bounds
 */
public class IndexOutOfBoundsException extends DhrException {
    public IndexOutOfBoundsException(String message) {
        super("IndexOutOfBoundsException", message, null);
    }

    public IndexOutOfBoundsException(String message, SourceLocation location) {
        super("IndexOutOfBoundsException", message, location);
    }
}
