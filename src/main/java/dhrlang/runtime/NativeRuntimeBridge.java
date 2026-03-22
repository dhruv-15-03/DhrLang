package dhrlang.runtime;

import dhrlang.error.ErrorFactory;
import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;

import java.util.List;

/** Shared bridge that allows non-AST backends to invoke registered native functions. */
public final class NativeRuntimeBridge {
    private static final Interpreter INTERPRETER = new Interpreter();

    private NativeRuntimeBridge() {}

    public static Object invoke(String functionName, List<Object> arguments) {
        Object callable = INTERPRETER.getGlobals().get(functionName);
        if (!(callable instanceof NativeFunction nativeFunction)) {
            throw ErrorFactory.typeError("Unknown native function '" + functionName + "'.", (dhrlang.error.SourceLocation) null);
        }
        return nativeFunction.call(INTERPRETER, arguments);
    }
}