package dhrlang.interpreter;
import dhrlang.ast.*;
import dhrlang.error.ErrorFactory;
import dhrlang.error.SourceLocation;
import dhrlang.runtime.NativeRegistrar;
import dhrlang.runtime.ProgramLoader;

public class Interpreter {
    private final ExecutionStack executionStack = new ExecutionStack();
    private SourceLocation currentCallLocation = null;
    private static final int MAX_CALL_DEPTH = 1000;
    private int currentCallDepth = 0;
    private final dhrlang.eval.Evaluator evaluator = new dhrlang.eval.Evaluator(this);
    private boolean inLoop = false;
    private final Environment globals = new Environment();

    public Interpreter(){ NativeRegistrar.registerAll(this, globals); }

    public void execute(Program program){ ProgramLoader.loadAndRun(program, this, globals); }
    public void execute(Statement stmt, Environment env){ evaluator.execute(stmt, env); }
    public void executeBlock(java.util.List<Statement> statements, Environment environment){ evaluator.executeBlock(statements, environment); }
    public Object evaluate(Expression expr, Environment env){ if(currentCallDepth >= MAX_CALL_DEPTH) throw ErrorFactory.runtimeError("Stack overflow: Maximum recursion depth ("+MAX_CALL_DEPTH+") exceeded.", (SourceLocation)null); currentCallDepth++; try { return evaluator.evaluate(expr, env); } finally { currentCallDepth--; } }

    // Accessors for evaluator & runtime
    public Environment getGlobals(){ return globals; }
    public boolean isInLoop(){ return inLoop; }
    public void setInLoop(boolean value){ inLoop = value; }
    public SourceLocation getCurrentCallLocation(){ return currentCallLocation; }
    public void setCurrentCallLocation(SourceLocation location){ this.currentCallLocation = location; }
    public int getCurrentCallDepth(){ return currentCallDepth; }
    public int getMaxCallDepth(){ return MAX_CALL_DEPTH; }
    public void incrementCallDepth(){ currentCallDepth++; }
    public void decrementCallDepth(){ currentCallDepth--; }

    // Execution stack helpers
    public void pushFrame(String functionName, String className, SourceLocation location){ executionStack.push(functionName, className, location); }
    public void popFrame(){ executionStack.pop(); }
    public ExecutionStack getExecutionStack(){ return executionStack; }
    public String currentClassContext(){ StackFrame f = executionStack.getCurrentFrame(); return f!=null? f.getClassName(): null; }
}
