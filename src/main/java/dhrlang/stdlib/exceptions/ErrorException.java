package dhrlang.stdlib.exceptions;

import dhrlang.error.SourceLocation;

/** Generic user error throwable via 'new Error()' and catchable with 'pakdo(e Error)'. */
public class ErrorException extends DhrException {
    public ErrorException() { super("Error", "Error", null); }
    public ErrorException(String message, SourceLocation loc){ super("Error", message, loc); }
}