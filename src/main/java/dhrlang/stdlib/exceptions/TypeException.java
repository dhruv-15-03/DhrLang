package dhrlang.stdlib.exceptions;

import dhrlang.error.SourceLocation;

/**
 * Thrown when type operations are invalid
 */
public class TypeException extends DhrException {
    public TypeException(String message) {
        super("TypeException", message, null);
    }

    public TypeException(String message, SourceLocation location) {
        super("TypeException", message, location);
    }
}
