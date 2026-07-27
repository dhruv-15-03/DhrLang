package dhrlang.debug;

import dhrlang.interpreter.Callable;
import dhrlang.interpreter.DhrClass;
import dhrlang.interpreter.Instance;
import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;

import java.util.*;

/**
 * Provides runtime inspection utilities for DhrLang values.
 *
 * <p>Each static factory method returns a {@link NativeFunction} that can be
 * registered in the global environment via {@code globals.define(...)}.
 * All core logic is also available through public static methods so that
 * unit tests can exercise it directly without an {@link Interpreter}.</p>
 *
 * <h3>Provided functions</h3>
 * <ul>
 *   <li>{@code inspect(value)} – detailed formatted view of any value</li>
 *   <li>{@code inspectType(value)} – type name string</li>
 *   <li>{@code inspectFields(instance)} – field dump for class instances</li>
 *   <li>{@code inspectStack()} – current execution stack snapshot</li>
 * </ul>
 */
public final class InspectFunction {

    private InspectFunction() { /* utility */ }

    // ── Native function factories ────────────────────────────────────────

    /**
     * {@code inspect(value)} – returns a human-readable debug string
     * describing the value's type, contents, and size hints.
     */
    public static NativeFunction inspect() {
        return new NativeFunction() {
            @Override public int arity() { return 1; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return formatValue(arguments.get(0));
            }

            @Override public String toString() { return "<native fn inspect>"; }
        };
    }

    /**
     * {@code inspectType(value)} – returns just the type name.
     */
    public static NativeFunction inspectType() {
        return new NativeFunction() {
            @Override public int arity() { return 1; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return getTypeName(arguments.get(0));
            }

            @Override public String toString() { return "<native fn inspectType>"; }
        };
    }

    /**
     * {@code inspectFields(instance)} – returns a field→value dump for
     * class instances, or {@code "not an instance"} otherwise.
     */
    public static NativeFunction inspectFields() {
        return new NativeFunction() {
            @Override public int arity() { return 1; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return formatFields(arguments.get(0));
            }

            @Override public String toString() { return "<native fn inspectFields>"; }
        };
    }

    /**
     * {@code inspectStack()} – returns the current execution stack trace.
     */
    public static NativeFunction inspectStack() {
        return new NativeFunction() {
            @Override public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return formatStack(interpreter);
            }

            @Override public String toString() { return "<native fn inspectStack>"; }
        };
    }

    // ── Core logic (public, testable) ────────────────────────────────────

    /**
     * Returns a human-readable debug representation of any DhrLang value.
     */
    public static String formatValue(Object value) {
        if (value == null) {
            return "inspect: null (kya)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("inspect: ");

        if (value instanceof Long l) {
            sb.append("num = ").append(l);
            sb.append(" [range: ").append(numRange(l)).append("]");
        } else if (value instanceof Double d) {
            sb.append("duo = ").append(d);
            if (Double.isNaN(d)) sb.append(" [NaN]");
            else if (Double.isInfinite(d)) sb.append(" [Infinity]");
        } else if (value instanceof Boolean b) {
            sb.append("sab = ").append(b);
        } else if (value instanceof String s) {
            sb.append("string = \"").append(escapeString(s)).append("\"");
            sb.append(" [length: ").append(s.length()).append("]");
        } else if (value instanceof Object[] arr) {
            sb.append("array [length: ").append(arr.length).append("] = ");
            sb.append(formatArray(arr));
        } else if (value instanceof Instance inst) {
            sb.append("instance of ").append(inst.getKlass().getName());
            sb.append(" ").append(formatInstanceFields(inst));
        } else if (value instanceof Callable callable) {
            sb.append("callable(").append(callable.arity()).append(" params)");
            sb.append(" = ").append(callable);
        } else {
            sb.append("unknown(").append(value.getClass().getSimpleName()).append(") = ");
            sb.append(value);
        }

        return sb.toString();
    }

    /**
     * Returns the DhrLang type name for the given runtime value.
     */
    public static String getTypeName(Object value) {
        if (value == null)                     return "kya";
        if (value instanceof Long)             return "num";
        if (value instanceof Double)           return "duo";
        if (value instanceof Boolean)          return "sab";
        if (value instanceof String)           return "string";
        if (value instanceof Object[])         return "array";
        if (value instanceof Instance inst)    return inst.getKlass().getName();
        if (value instanceof DhrClass cls)     return "class<" + cls.getName() + ">";
        if (value instanceof Callable)         return "function";
        return "unknown";
    }

    /**
     * Returns a map of field names→values for an Instance, or an empty map.
     */
    public static Map<String, String> getObjectDetails(Object value) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("type", getTypeName(value));

        if (value == null) {
            details.put("value", "null");
        } else if (value instanceof Long l) {
            details.put("value", String.valueOf(l));
            details.put("range", numRange(l));
        } else if (value instanceof Double d) {
            details.put("value", String.valueOf(d));
            details.put("special", Double.isNaN(d) ? "NaN" : Double.isInfinite(d) ? "Infinity" : "normal");
        } else if (value instanceof Boolean b) {
            details.put("value", String.valueOf(b));
        } else if (value instanceof String s) {
            details.put("value", s);
            details.put("length", String.valueOf(s.length()));
        } else if (value instanceof Object[] arr) {
            details.put("length", String.valueOf(arr.length));
        } else if (value instanceof Instance) {
            details.put("value", value.toString());
        }

        return details;
    }

    /**
     * Formats an Instance's fields as a string dump.
     */
    public static String formatFields(Object value) {
        if (!(value instanceof Instance inst)) {
            return "not an instance (type: " + getTypeName(value) + ")";
        }
        return formatInstanceFields(inst);
    }

    /**
     * Formats the current execution stack from the Interpreter.
     */
    public static String formatStack(Interpreter interpreter) {
        if (interpreter == null) {
            return "stack: (no interpreter)";
        }
        var stack = interpreter.getExecutionStack();
        var frames = stack.getFrames();
        if (frames.isEmpty()) {
            return "stack: (empty)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("stack (").append(frames.size()).append(" frames):\n");
        for (int i = frames.size() - 1; i >= 0; i--) {
            var frame = frames.get(i);
            sb.append("  [").append(frames.size() - 1 - i).append("] ");
            if (frame.getClassName() != null) {
                sb.append(frame.getClassName()).append(".");
            }
            sb.append(frame.getFunctionName());
            if (frame.getLocation() != null) {
                sb.append(" at ").append(frame.getLocation());
            }
            if (i > 0) sb.append("\n");
        }
        return sb.toString();
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private static String numRange(long value) {
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return "byte";
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) return "short";
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return "int";
        return "long";
    }

    private static String escapeString(String s) {
        if (s.length() > 100) {
            return s.substring(0, 97) + "...";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r");
    }

    private static String formatArray(Object[] arr) {
        if (arr.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        int limit = Math.min(arr.length, 10);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatCompact(arr[i]));
        }
        if (arr.length > 10) {
            sb.append(", ... (").append(arr.length - 10).append(" more)");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatCompact(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + escapeString(s) + "\"";
        if (value instanceof Object[] arr) return "array[" + arr.length + "]";
        if (value instanceof Instance inst) return inst.getKlass().getName() + "{...}";
        return String.valueOf(value);
    }

    private static String formatInstanceFields(Instance inst) {
        // We access fields reflectively through the Instance
        // Since Instance stores fields in a private Map, we use toString
        // and extract class name. For debug display we provide the class info.
        StringBuilder sb = new StringBuilder("{");
        sb.append("class: ").append(inst.getKlass().getName());
        if (inst.isGenericInstance()) {
            sb.append("<");
            String[] typeArgs = inst.getGenericTypeArguments();
            for (int i = 0; i < typeArgs.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(typeArgs[i]);
            }
            sb.append(">");
        }
        sb.append("}");
        return sb.toString();
    }
}
