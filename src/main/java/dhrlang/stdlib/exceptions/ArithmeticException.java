package dhrlang.stdlib.exceptions;

import dhrlang.error.SourceLocation;

/**
 * Thrown when an arithmetic operation is invalid (like division by zero)
 */
public class ArithmeticException extends DhrException {
    public ArithmeticException(String message) {
        super("ArithmeticException", message, null);
    }

    public ArithmeticException(String message, SourceLocation location) {
        super("ArithmeticException", message, location);
    }
}
