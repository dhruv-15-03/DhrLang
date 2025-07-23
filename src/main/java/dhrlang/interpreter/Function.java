package dhrlang.interpreter;

import dhrlang.ast.FunctionDecl;
import java.util.List;

public class Function implements Callable {
    private final FunctionDecl declaration;
    private final Environment closure;

    public Function(FunctionDecl declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    public FunctionDecl getDeclaration() {
        return declaration;
    }

    public Function bind(Instance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new Function(declaration, environment);
    }
    public Environment getClosure() {
        return closure;
    }

    @Override
    public int arity() {
        return declaration.getParameters().size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // Check for stack overflow before executing
        if (interpreter.getCurrentCallDepth() >= interpreter.getMaxCallDepth()) {
            throw new dhrlang.interpreter.DhrRuntimeException("Stack overflow: Maximum recursion depth (" + 
                interpreter.getMaxCallDepth() + ") exceeded. " +
                "Check for infinite recursion in your function calls. Consider adding a base case to recursive functions.");
        }
        
        Environment environment = new Environment(this.closure);
        
        // Increment call depth
        interpreter.incrementCallDepth();
        try {
            return execute(interpreter, arguments, environment);
        } finally {
            // Always decrement, even on exception
            interpreter.decrementCallDepth();
        }
    }
    public Object execute(Interpreter interpreter, List<Object> arguments, Environment environment) {
        for (int i = 0; i < declaration.getParameters().size(); i++) {
            environment.define(declaration.getParameters().get(i).getName(), arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.getBody().getStatements(), environment);
        } catch (ReturnValue returnValue) {
            return returnValue.getValue();
        }

        if (declaration.getName().equals("init")) {
            return environment.get("this");
        }

        return null;
    }


    @Override
    public String toString() {
        return "<fn " + declaration.getName() + ">";
    }
}