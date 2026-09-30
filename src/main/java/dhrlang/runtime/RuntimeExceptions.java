package dhrlang.runtime;

import dhrlang.error.RuntimeErrorCategory;
import dhrlang.interpreter.DhrRuntimeException;
import dhrlang.stdlib.exceptions.DhrException;

/** Preserves runtime categories while exceptions cross IR/bytecode call frames. */
public final class RuntimeExceptions {
    private RuntimeExceptions() {}

    public static DhrRuntimeException propagate(Object value) {
        return value instanceof DhrRuntimeException failure ? failure
                : new DhrRuntimeException(value, null, RuntimeErrorCategory.USER_EXCEPTION);
    }

    public static Object payload(Object value) {
        return value instanceof DhrRuntimeException failure ? failure.getValue() : value;
    }

    public static boolean matches(String type, Object value) {
        if ("any".equals(type)) return true;
        Object payload = payload(value);
        if (payload instanceof DhrException exception) {
            return "DhrException".equals(type) || type.equals(exception.getExceptionType());
        }
        if (value instanceof DhrRuntimeException failure) {
            if ("DhrException".equals(type) || "Error".equals(type)) return true;
            return type.equals(switch (failure.getCategory()) {
                case ARITHMETIC_ERROR -> "ArithmeticException";
                case INDEX_ERROR -> "IndexOutOfBoundsException";
                case TYPE_ERROR -> "TypeException";
                case NULL_ERROR -> "NullPointerException";
                default -> "Error";
            });
        }
        return false;
    }
}
