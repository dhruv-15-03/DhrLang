package dhrlang.interpreter;

import java.util.List;
import java.util.Objects;

public abstract class NativeFunction implements Callable {

    @Override
    public abstract int arity();

    @Override
    public abstract Object call(Interpreter interpreter, List<Object> arguments);

    @Override
    public String toString() {
        return "<native fn>";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NativeFunction;
    }

    @Override
    public int hashCode() {
        return Objects.hash("native");
    }
}

