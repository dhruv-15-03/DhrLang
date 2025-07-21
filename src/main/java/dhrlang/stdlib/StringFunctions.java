package dhrlang.stdlib;

import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;
import dhrlang.interpreter.RuntimeError;

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
                    throw new RuntimeError("length() requires a string argument");
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
                    throw new RuntimeError("substring() first argument must be a string");
                }
                if (!(start instanceof Long) || !(end instanceof Long)) {
                    throw new RuntimeError("substring() indices must be numbers");
                }

                String string = (String) str;
                int startIdx = ((Long) start).intValue();
                int endIdx = ((Long) end).intValue();

                if (startIdx < 0 || endIdx > string.length() || startIdx > endIdx) {
                    throw new RuntimeError("substring() indices out of bounds");
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
                    throw new RuntimeError("charAt() first argument must be a string");
                }
                if (!(index instanceof Long)) {
                    throw new RuntimeError("charAt() index must be a number");
                }

                String string = (String) str;
                int idx = ((Long) index).intValue();

                if (idx < 0 || idx >= string.length()) {
                    throw new RuntimeError("charAt() index out of bounds");
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
                    throw new RuntimeError("toUpperCase() requires a string argument");
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
                    throw new RuntimeError("toLowerCase() requires a string argument");
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
                    throw new RuntimeError("indexOf() requires string arguments");
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
                    throw new RuntimeError("replace() requires string arguments");
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
                    throw new RuntimeError("startsWith() requires string arguments");
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
                    throw new RuntimeError("endsWith() requires string arguments");
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
                    throw new RuntimeError("trim() requires a string argument");
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
                    throw new RuntimeError("split() requires string arguments");
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
                    throw new RuntimeError("join() requires an array and string delimiter");
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
                    throw new RuntimeError("repeat() requires a string and number");
                }

                String string = (String) str;
                int times = ((Long) count).intValue();
                
                if (times < 0) {
                    throw new RuntimeError("repeat() count cannot be negative");
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
                    throw new RuntimeError("reverse() requires a string argument");
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
                    throw new RuntimeError("padLeft() requires string, number, string arguments");
                }

                String string = (String) str;
                int targetLength = ((Long) length).intValue();
                String pad = (String) padChar;

                if (pad.length() != 1) {
                    throw new RuntimeError("padLeft() pad character must be a single character");
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
                    throw new RuntimeError("padRight() requires string, number, string arguments");
                }

                String string = (String) str;
                int targetLength = ((Long) length).intValue();
                String pad = (String) padChar;

                if (pad.length() != 1) {
                    throw new RuntimeError("padRight() pad character must be a single character");
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
