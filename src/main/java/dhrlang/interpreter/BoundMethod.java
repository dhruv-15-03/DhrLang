// Create this new file
package dhrlang.interpreter;

import java.util.List;

public class BoundMethod implements Callable {
    private final Instance instance;
    private final Function function;

    public BoundMethod(Instance instance, Function function) {
        this.instance = instance;
        this.function = function;
    }

    @Override
    public int arity() {
        return function.arity();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment methodEnvironment = new Environment(function.getClosure());
        methodEnvironment.define("this", this.instance);

        return function.execute(interpreter, arguments, methodEnvironment);
    }

    @Override
    public String toString() {
        return function.toString();
    }
}