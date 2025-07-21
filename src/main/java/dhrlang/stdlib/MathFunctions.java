package dhrlang.stdlib;

import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;
import dhrlang.error.ErrorFactory;

import java.util.List;

public class MathFunctions {

    public static NativeFunction abs() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                if (arg instanceof Long) {
                    return Math.abs((Long) arg);
                } else if (arg instanceof Double) {
                    return Math.abs((Double) arg);
                } else {
                    throw ErrorFactory.typeError(
                        "abs() requires a number argument",
                        interpreter.getCurrentCallLocation()
                    );
                }
            }

            @Override
            public String toString() {
                return "<native fn abs>";
            }
        };
    }

    public static NativeFunction sqrt() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                double value;
                if (arg instanceof Long) {
                    value = ((Long) arg).doubleValue();
                } else if (arg instanceof Double) {
                    value = (Double) arg;
                } else {
                    throw ErrorFactory.typeError(
                        "sqrt() requires a number argument",
                        interpreter.getCurrentCallLocation()
                    );
                }
                
                if (value < 0) {
                    throw ErrorFactory.validationError(
                        "sqrt() cannot be called with negative number",
                        interpreter.getCurrentCallLocation()
                    );
                }
                
                return Math.sqrt(value);
            }

            @Override
            public String toString() {
                return "<native fn sqrt>";
            }
        };
    }

    public static NativeFunction pow() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object base = arguments.get(0);
                Object exponent = arguments.get(1);
                
                double baseValue = toDouble(base, interpreter);
                double expValue = toDouble(exponent, interpreter);
                
                return Math.pow(baseValue, expValue);
            }

            @Override
            public String toString() {
                return "<native fn pow>";
            }
        };
    }

    public static NativeFunction min() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object a = arguments.get(0);
                Object b = arguments.get(1);
                
                if (a instanceof Long && b instanceof Long) {
                    return Math.min((Long) a, (Long) b);
                } else {
                    return Math.min(toDouble(a, interpreter), toDouble(b, interpreter));
                }
            }

            @Override
            public String toString() {
                return "<native fn min>";
            }
        };
    }

    public static NativeFunction max() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object a = arguments.get(0);
                Object b = arguments.get(1);
                
                if (a instanceof Long && b instanceof Long) {
                    return Math.max((Long) a, (Long) b);
                } else {
                    return Math.max(toDouble(a, interpreter), toDouble(b, interpreter));
                }
            }

            @Override
            public String toString() {
                return "<native fn max>";
            }
        };
    }

    public static NativeFunction floor() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return (long) Math.floor(toDouble(arg, interpreter));
            }

            @Override
            public String toString() {
                return "<native fn floor>";
            }
        };
    }

    public static NativeFunction ceil() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return (long) Math.ceil(toDouble(arg, interpreter));
            }

            @Override
            public String toString() {
                return "<native fn ceil>";
            }
        };
    }

    public static NativeFunction round() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return Math.round(toDouble(arg, interpreter));
            }

            @Override
            public String toString() {
                return "<native fn round>";
            }
        };
    }

    public static NativeFunction random() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return Math.random();
            }

            @Override
            public String toString() {
                return "<native fn random>";
            }
        };
    }

    public static NativeFunction sin() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return Math.sin(toDouble(arg, interpreter));
            }

            @Override
            public String toString() {
                return "<native fn sin>";
            }
        };
    }

    public static NativeFunction cos() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return Math.cos(toDouble(arg, interpreter));
            }

            @Override
            public String toString() {
                return "<native fn cos>";
            }
        };
    }

    public static NativeFunction tan() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return Math.tan(toDouble(arg, interpreter));
            }

            @Override
            public String toString() {
                return "<native fn tan>";
            }
        };
    }

    public static NativeFunction log() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                double value = toDouble(arg, interpreter);
                if (value <= 0) {
                    throw ErrorFactory.validationError(
                        "log() requires a positive number",
                        interpreter.getCurrentCallLocation()
                    );
                }
                return Math.log(value);
            }

            @Override
            public String toString() {
                return "<native fn log>";
            }
        };
    }

    public static NativeFunction log10() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                double value = toDouble(arg, interpreter);
                if (value <= 0) {
                    throw ErrorFactory.validationError(
                        "log10() requires a positive number",
                        interpreter.getCurrentCallLocation()
                    );
                }
                return Math.log10(value);
            }

            @Override
            public String toString() {
                return "<native fn log10>";
            }
        };
    }

    public static NativeFunction exp() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                return Math.exp(toDouble(arg, interpreter));
            }

            @Override
            public String toString() {
                return "<native fn exp>";
            }
        };
    }

    public static NativeFunction randomRange() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object min = arguments.get(0);
                Object max = arguments.get(1);
                
                long minVal = ((Long) min);
                long maxVal = ((Long) max);
                
                if (minVal >= maxVal) {
                    throw ErrorFactory.validationError(
                        "randomRange() min must be less than max",
                        interpreter.getCurrentCallLocation()
                    );
                }
                
                return (long) (Math.random() * (maxVal - minVal) + minVal);
            }

            @Override
            public String toString() {
                return "<native fn randomRange>";
            }
        };
    }

    public static NativeFunction clamp() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 3;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object value = arguments.get(0);
                Object min = arguments.get(1);
                Object max = arguments.get(2);
                
                if (value instanceof Long && min instanceof Long && max instanceof Long) {
                    long val = (Long) value;
                    long minVal = (Long) min;
                    long maxVal = (Long) max;
                    return Math.max(minVal, Math.min(maxVal, val));
                } else {
                    double val = toDouble(value, interpreter);
                    double minVal = toDouble(min, interpreter);
                    double maxVal = toDouble(max, interpreter);
                    return Math.max(minVal, Math.min(maxVal, val));
                }
            }

            @Override
            public String toString() {
                return "<native fn clamp>";
            }
        };
    }

    private static double toDouble(Object obj, Interpreter interpreter) {
        if (obj instanceof Long) {
            return ((Long) obj).doubleValue();
        } else if (obj instanceof Double) {
            return (Double) obj;
        } else {
            throw ErrorFactory.typeError(
                "Expected number, got " + obj.getClass().getSimpleName(), 
                interpreter.getCurrentCallLocation()
            );
        }
    }
}
