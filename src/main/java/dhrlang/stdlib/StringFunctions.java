package dhrlang.stdlib;

import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;
import dhrlang.error.ErrorFactory;

import java.util.List;

public class StringFunctions {

    public static NativeFunction length() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                if (!(arg instanceof String)) {
                    throw ErrorFactory.typeError(
                        "length() requires a string argument",
                        interpreter.getCurrentCallLocation()
                    );
                }
                return (long) ((String) arg).length();
            }

            @Override
            public String toString() {
                return "<native fn length>";
            }
        };
    }

    public static NativeFunction substring() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 3;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object str = arguments.get(0);
                Object start = arguments.get(1);
                Object end = arguments.get(2);

                if (!(str instanceof String)) {
                    throw ErrorFactory.typeError(
                        "substring() first argument must be a string",
                        interpreter.getCurrentCallLocation()
                    );
                }
                if (!(start instanceof Long) || !(end instanceof Long)) {
                    throw ErrorFactory.typeError(
                        "substring() indices must be numbers",
                        interpreter.getCurrentCallLocation()
                    );
                }

                String string = (String) str;
                int startIdx = ((Long) start).intValue();
                int endIdx = ((Long) end).intValue();

                if (startIdx < 0 || endIdx > string.length() || startIdx > endIdx) {
                    throw ErrorFactory.validationError(
                        "substring() indices out of bounds",
                        interpreter.getCurrentCallLocation()
                    );
                }

                return string.substring(startIdx, endIdx);
            }

            @Override
            public String toString() {
                return "<native fn substring>";
            }
        };
    }

    public static NativeFunction charAt() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object str = arguments.get(0);
                Object index = arguments.get(1);

                if (!(str instanceof String)) {
                    throw ErrorFactory.typeError(
                        "charAt() first argument must be a string",
                        interpreter.getCurrentCallLocation()
                    );
                }
                if (!(index instanceof Long)) {
                    throw ErrorFactory.typeError(
                        "charAt() index must be a number",
                        interpreter.getCurrentCallLocation()
                    );
                }

                String string = (String) str;
                int idx = ((Long) index).intValue();

                if (idx < 0 || idx >= string.length()) {
                    throw ErrorFactory.indexError(
                        "charAt() index out of bounds",
                        interpreter.getCurrentCallLocation()
                    );
                }

                return String.valueOf(string.charAt(idx));
            }

            @Override
            public String toString() {
                return "<native fn charAt>";
            }
        };
    }

    public static NativeFunction toUpperCase() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                if (!(arg instanceof String)) {
                    throw ErrorFactory.typeError(
                        "toUpperCase() requires a string argument",
                        interpreter.getCurrentCallLocation()
                    );
                }
                return ((String) arg).toUpperCase();
            }

            @Override
            public String toString() {
                return "<native fn toUpperCase>";
            }
        };
    }

    public static NativeFunction toLowerCase() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                if (!(arg instanceof String)) {
                    throw ErrorFactory.typeError(
                        "toLowerCase() requires a string argument",
                        interpreter.getCurrentCallLocation()
                    );
                }
                return ((String) arg).toLowerCase();
            }

            @Override
            public String toString() {
                return "<native fn toLowerCase>";
            }
        };
    }

    public static NativeFunction indexOf() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object str = arguments.get(0);
                Object searchStr = arguments.get(1);

                if (!(str instanceof String) || !(searchStr instanceof String)) {
                    throw ErrorFactory.typeError(
                        "indexOf() requires string arguments",
                        interpreter.getCurrentCallLocation()
                    );
                }

                return (long) ((String) str).indexOf((String) searchStr);
            }

            @Override
            public String toString() {
                return "<native fn indexOf>";
            }
        };
    }

    public static NativeFunction replace() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 3;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object str = arguments.get(0);
                Object target = arguments.get(1);
                Object replacement = arguments.get(2);

                if (!(str instanceof String) || !(target instanceof String) || !(replacement instanceof String)) {
                    throw ErrorFactory.typeError(
                        "replace() requires string arguments",
                        interpreter.getCurrentCallLocation()
                    );
                }

                return ((String) str).replace((String) target, (String) replacement);
            }

            @Override
            public String toString() {
                return "<native fn replace>";
            }
        };
    }

    public static NativeFunction startsWith() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object str = arguments.get(0);
                Object prefix = arguments.get(1);

                if (!(str instanceof String) || !(prefix instanceof String)) {
                    throw ErrorFactory.typeError(
                        "startsWith() requires string arguments",
                        interpreter.getCurrentCallLocation()
                    );
                }

                return ((String) str).startsWith((String) prefix);
            }

            @Override
            public String toString() {
                return "<native fn startsWith>";
            }
        };
    }

    public static NativeFunction endsWith() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object str = arguments.get(0);
                Object suffix = arguments.get(1);

                if (!(str instanceof String) || !(suffix instanceof String)) {
                    throw ErrorFactory.typeError(
                        "endsWith() requires string arguments",
                        interpreter.getCurrentCallLocation()
                    );
                }

                return ((String) str).endsWith((String) suffix);
            }

            @Override
            public String toString() {
                return "<native fn endsWith>";
            }
        };
    }

    public static NativeFunction trim() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                if (!(arg instanceof String)) {
                    throw ErrorFactory.typeError(
                        "trim() requires a string argument",
                        interpreter.getCurrentCallLocation()
                    );
                }
                return ((String) arg).trim();
            }

            @Override
            public String toString() {
                return "<native fn trim>";
            }
        };
    }

    public static NativeFunction split() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object str = arguments.get(0);
                Object delimiter = arguments.get(1);

                if (!(str instanceof String) || !(delimiter instanceof String)) {
                    throw ErrorFactory.typeError(
                        "split() requires string arguments",
                        interpreter.getCurrentCallLocation()
                    );
                }

                String[] parts = ((String) str).split((String) delimiter);
                Object[] result = new Object[parts.length];
                System.arraycopy(parts, 0, result, 0, parts.length);
                return result;
            }

            @Override
            public String toString() {
                return "<native fn split>";
            }
        };
    }

    public static NativeFunction join() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);
                Object delimiter = arguments.get(1);

                if (!(arr instanceof Object[]) || !(delimiter instanceof String)) {
                    throw ErrorFactory.typeError(
                        "join() requires an array and string delimiter",
                        interpreter.getCurrentCallLocation()
                    );
                }

                Object[] array = (Object[]) arr;
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < array.length; i++) {
                    if (i > 0) result.append((String) delimiter);
                    result.append(array[i].toString());
                }
                return result.toString();
            }

            @Override
            public String toString() {
                return "<native fn join>";
            }
        };
    }

    public static NativeFunction repeat() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object str = arguments.get(0);
                Object count = arguments.get(1);

                if (!(str instanceof String) || !(count instanceof Long)) {
                    throw ErrorFactory.typeError(
                        "repeat() requires a string and number",
                        interpreter.getCurrentCallLocation()
                    );
                }

                String string = (String) str;
                int times = ((Long) count).intValue();
                
                if (times < 0) {
                    throw ErrorFactory.validationError(
                        "repeat() count cannot be negative",
                        interpreter.getCurrentCallLocation()
                    );
                }

                return string.repeat(times);
            }

            @Override
            public String toString() {
                return "<native fn repeat>";
            }
        };
    }

    public static NativeFunction reverse() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object str = arguments.get(0);
                if (!(str instanceof String)) {
                    throw ErrorFactory.typeError(
                        "reverse() requires a string argument",
                        interpreter.getCurrentCallLocation()
                    );
                }
                
                return new StringBuilder((String) str).reverse().toString();
            }

            @Override
            public String toString() {
                return "<native fn reverse>";
            }
        };
    }

    public static NativeFunction padLeft() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 3;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object str = arguments.get(0);
                Object length = arguments.get(1);
                Object padChar = arguments.get(2);

                if (!(str instanceof String) || !(length instanceof Long) || !(padChar instanceof String)) {
                    throw ErrorFactory.typeError(
                        "padLeft() requires string, number, string arguments",
                        interpreter.getCurrentCallLocation()
                    );
                }

                String string = (String) str;
                int targetLength = ((Long) length).intValue();
                String pad = (String) padChar;

                if (pad.length() != 1) {
                    throw ErrorFactory.validationError(
                        "padLeft() pad character must be a single character",
                        interpreter.getCurrentCallLocation()
                    );
                }

                while (string.length() < targetLength) {
                    string = pad + string;
                }
                return string;
            }

            @Override
            public String toString() {
                return "<native fn padLeft>";
            }
        };
    }

    public static NativeFunction padRight() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 3;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object str = arguments.get(0);
                Object length = arguments.get(1);
                Object padChar = arguments.get(2);

                if (!(str instanceof String) || !(length instanceof Long) || !(padChar instanceof String)) {
                    throw ErrorFactory.typeError(
                        "padRight() requires string, number, string arguments",
                        interpreter.getCurrentCallLocation()
                    );
                }

                String string = (String) str;
                int targetLength = ((Long) length).intValue();
                String pad = (String) padChar;

                if (pad.length() != 1) {
                    throw ErrorFactory.validationError(
                        "padRight() pad character must be a single character",
                        interpreter.getCurrentCallLocation()
                    );
                }

                while (string.length() < targetLength) {
                    string = string + pad;
                }
                return string;
            }

            @Override
            public String toString() {
                return "<native fn padRight>";
            }
        };
    }
}
