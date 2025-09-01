package dhrlang.interpreter;

import dhrlang.ast.FunctionDecl;
import java.util.List;

public class Function implements Callable {
    private final FunctionDecl declaration;
    private final Environment closure;
    // Class context this function logically belongs to (even if static). Used for access control.
    private final String ownerClassName;
    public Function(FunctionDecl declaration, Environment closure) {
        this(declaration, closure, null);
    }
    public Function(FunctionDecl declaration, Environment closure, String ownerClassName) {
        this.declaration = declaration;
        this.closure = closure;
        this.ownerClassName = ownerClassName;
    }

    public FunctionDecl getDeclaration() {
        return declaration;
    }

    public Function bind(Instance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
    // Preserve original declaring class context (ownerClassName) so that
    // private/protected access checks use the method's defining class, not the runtime subclass.
    return new Function(declaration, environment, this.ownerClassName);
    }
    public Environment getClosure() {
        return closure;
    }
    public String getOwnerClassName(){ return ownerClassName; }

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
        // Push execution frame with class context if available
        String className = ownerClassName;
        if(className==null){
            try {
                Object maybeThis = closure.get("this");
                if (maybeThis instanceof Instance inst) {
                    className = inst.getKlass().name;
                }
            } catch (dhrlang.interpreter.DhrRuntimeException ignored) {}
        }
        interpreter.pushFrame(declaration.getName(), className, interpreter.getCurrentCallLocation());
        try {
            return execute(interpreter, arguments, environment);
        } finally {
            // Always decrement, even on exception
            interpreter.decrementCallDepth();
            interpreter.popFrame();
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