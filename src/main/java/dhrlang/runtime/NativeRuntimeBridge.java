package dhrlang.runtime;

import dhrlang.error.ErrorFactory;
import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;

import java.util.List;

/** Shared bridge that allows non-AST backends to invoke registered native functions. */
public final class NativeRuntimeBridge {
    private static final Interpreter INTERPRETER = new Interpreter();
    private static final java.util.Set<String> HOST_NATIVES = java.util.Set.of(
            "print", "printLine", "readLine", "readLineWithPrompt", "toNum", "toDuo", "toString",
            "abs", "sqrt", "pow", "min", "max", "floor", "ceil", "round", "sin", "cos", "tan",
            "log", "log10", "exp", "clamp", "length", "substring", "charAt", "toUpperCase",
            "toLowerCase", "indexOf", "replace", "startsWith", "endsWith", "trim", "split", "join",
            "repeat", "reverse", "padLeft", "padRight", "arrayLength", "arrayContains", "arrayIndexOf",
            "arrayCopy", "arrayReverse", "arraySort", "arraySlice", "arrayConcat", "arrayFill",
            "arraySum", "arrayAverage", "arrayPush", "arrayPop", "arrayInsert", "isNum", "isDuo",
            "isSab", "isKya", "isArray", "typeOf", "range");

    private NativeRuntimeBridge() {}

    public static Object invoke(String functionName, List<Object> arguments) {
        if (Boolean.getBoolean("dhrlang.host.restricted")
                && !HOST_NATIVES.contains(functionName)) {
            throw ErrorFactory.accessError("Native function '" + functionName
                    + "' is disabled in the deterministic host profile.", INTERPRETER.getCurrentCallLocation());
        }
        Object callable = INTERPRETER.getGlobals().get(functionName);
        if (!(callable instanceof NativeFunction nativeFunction)) {
            throw ErrorFactory.typeError("Unknown native function '" + functionName + "'.", (dhrlang.error.SourceLocation) null);
        }
        return nativeFunction.call(INTERPRETER, arguments);
    }
}