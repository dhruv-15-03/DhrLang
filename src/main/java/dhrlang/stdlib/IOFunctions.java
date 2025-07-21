package dhrlang.stdlib;

import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;
import dhrlang.interpreter.RuntimeError;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class IOFunctions {

    private static final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    public static NativeFunction readLine() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                try {
                    String line = reader.readLine();
                    return line != null ? line : "";
                } catch (IOException e) {
                    throw new RuntimeError("Error reading input: " + e.getMessage());
                }
            }

            @Override
            public String toString() {
                return "<native fn readLine>";
            }
        };
    }

    public static NativeFunction readLineWithPrompt() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object prompt = arguments.get(0);
                if (!(prompt instanceof String)) {
                    throw new RuntimeError("readLineWithPrompt() requires a string prompt");
                }
                
                System.out.print(prompt);
                try {
                    String line = reader.readLine();
                    return line != null ? line : "";
                } catch (IOException e) {
                    throw new RuntimeError("Error reading input: " + e.getMessage());
                }
            }

            @Override
            public String toString() {
                return "<native fn readLineWithPrompt>";
            }
        };
    }

    public static NativeFunction toNum() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                if (!(arg instanceof String)) {
                    throw new RuntimeError("toNum() requires a string argument");
                }
                
                try {
                    return Long.parseLong(((String) arg).trim());
                } catch (NumberFormatException e) {
                    throw new RuntimeError("toNum() could not parse '" + arg + "' as an integer");
                }
            }

            @Override
            public String toString() {
                return "<native fn toNum>";
            }
        };
    }

    public static NativeFunction toDuo() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                if (!(arg instanceof String)) {
                    throw new RuntimeError("parseDouble() requires a string argument");
                }
                
                try {
                    return Double.parseDouble(((String) arg).trim());
                } catch (NumberFormatException e) {
                    throw new RuntimeError("toDuo() could not parse '" + arg + "' as a number");
                }
            }

            @Override
            public String toString() {
                return "<native fn toDuo>";
            }
        };
    }

    public static NativeFunction toStringFunc() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                if (arg == null) {
                    return "null";
                }
                
                if (arg instanceof Object[]) {
                    Object[] array = (Object[]) arg;
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < array.length; i++) {
                        if (i > 0) sb.append(", ");
                        if (array[i] == null) {
                            sb.append("null");
                        } else if (array[i] instanceof String) {
                            sb.append("\"").append(array[i]).append("\"");
                        } else {
                            sb.append(array[i].toString());
                        }
                    }
                    sb.append("]");
                    return sb.toString();
                }
                
                return arg.toString();
            }

            @Override
            public String toString() {
                return "<native fn toString>";
            }
        };
    }
}
