package dhrlang.interpreter;

import java.util.List;

public interface Callable {
    Object call(Interpreter interpreter, List<Object> arguments);
    int arity(); // Number of expected parameters
}
