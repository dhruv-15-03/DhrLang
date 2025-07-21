package dhrlang.stdlib;

import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;
import dhrlang.error.ErrorFactory;

import java.util.List;

public class UtilityFunctions {

    public static NativeFunction isNum() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return arg instanceof Long;
            }

            @Override
            public String toString() {
                return "<native fn isNum>";
            }
        };
    }

    public static NativeFunction isDuo() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return arg instanceof Double;
            }

            @Override
            public String toString() {
                return "<native fn isDuo>";
            }
        };
    }

    public static NativeFunction isSab() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return arg instanceof String;
            }

            @Override
            public String toString() {
                return "<native fn isSab>";
            }
        };
    }

    public static NativeFunction isKya() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return arg instanceof Boolean;
            }

            @Override
            public String toString() {
                return "<native fn isKya>";
            }
        };
    }

    public static NativeFunction isArray() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return arg instanceof Object[];
            }

            @Override
            public String toString() {
                return "<native fn isArray>";
            }
        };
    }

    public static NativeFunction typeOf() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                if (arg == null) return "null";
                if (arg instanceof Long) return "num";
                if (arg instanceof Double) return "duo";
                if (arg instanceof String) return "sab";
                if (arg instanceof Boolean) return "kya";
                if (arg instanceof Object[]) return "array";
                return "object";
            }

            @Override
            public String toString() {
                return "<native fn typeOf>";
            }
        };
    }

    public static NativeFunction range() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object start = arguments.get(0);
                Object end = arguments.get(1);

                if (!(start instanceof Long) || !(end instanceof Long)) {
                    throw ErrorFactory.typeError(
                        "range() requires two number arguments",
                        interpreter.getCurrentCallLocation()
                    );
                }

                long startVal = (Long) start;
                long endVal = (Long) end;

                if (startVal >= endVal) {
                    return new Object[0]; // Empty array
                }

                Object[] result = new Object[(int)(endVal - startVal)];
                for (int i = 0; i < result.length; i++) {
                    result[i] = startVal + i;
                }
                return result;
            }

            @Override
            public String toString() {
                return "<native fn range>";
            }
        };
    }

    public static NativeFunction sleep() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object ms = arguments.get(0);
                if (!(ms instanceof Long)) {
                    throw ErrorFactory.typeError(
                        "sleep() requires a number argument (milliseconds)",
                        interpreter.getCurrentCallLocation()
                    );
                }

                try {
                    Thread.sleep((Long) ms);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw ErrorFactory.runtimeError(
                        "Sleep was interrupted",
                        interpreter.getCurrentCallLocation()
                    );
                }
                return null;
            }

            @Override
            public String toString() {
                return "<native fn sleep>";
            }
        };
    }
}
