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
        Environment environment = new Environment(this.closure);
        return execute(interpreter, arguments, environment);
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