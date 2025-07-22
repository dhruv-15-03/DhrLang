package dhrlang.interpreter;
import dhrlang.ast.*;
import dhrlang.lexer.Token;
import dhrlang.lexer.TokenType;
import dhrlang.stdlib.*;
import dhrlang.typechecker.TypeChecker;
import dhrlang.typechecker.TypeException;
import dhrlang.error.ErrorFactory;
import dhrlang.error.SourceLocation;

import java.util.*;

public class Interpreter {
    private final ExecutionStack executionStack = new ExecutionStack();
    private SourceLocation currentCallLocation = null; // Track current call location for error reporting
    
    public Interpreter() {
        initGlobals();
    }
    private boolean inLoop = false;

    private final TypeChecker typeChecker = new TypeChecker();
    private final Environment globals = new Environment();
    
    public SourceLocation getCurrentCallLocation() {
        return currentCallLocation;
    }
    
    public void execute(Program program) {
        try {
            typeChecker.check(program);
        } catch (TypeException e) {
            throw new RuntimeException("Type Error: " + e.getMessage(), e);
        }
        for (ClassDecl classDecl : program.getClasses()) {
            globals.define(classDecl.getName(), null);
        }
        for (ClassDecl classDecl : program.getClasses()) {
            DhrClass superclass = null;
            if (classDecl.getSuperclass() != null) {
                Object sc = globals.get(classDecl.getSuperclass().getName().getLexeme());
                if (!(sc instanceof DhrClass)) {
                    throw ErrorFactory.typeError("Superclass must be a class.", classDecl.getSuperclass().getSourceLocation());
                }
                superclass = (DhrClass) sc;
            }

            Map<String, Function> methods = new HashMap<>();
            Map<String, Function> staticMethods = new HashMap<>(); 
            Map<String, Object> staticFields = new HashMap<>();
            
            for (FunctionDecl method : classDecl.getFunctions()) {
                Function function = new Function(method, globals);
                if (method.hasModifier(Modifier.STATIC)) {
                    staticMethods.put(method.getName(), function);
                } else {
                    methods.put(method.getName(), function);
                }
            }
            
            // Initialize static fields
            for (VarDecl field : classDecl.getVariables()) {
                if (field.hasModifier(Modifier.STATIC)) {
                    Object initialValue = getDefaultValue(field.getType());
                    if (field.getInitializer() != null) {
                        initialValue = evaluate(field.getInitializer(), globals);
                    }
                    staticFields.put(field.getName(), initialValue);
                }
            }
            
            DhrClass klass = new DhrClass(classDecl.getName(), superclass, methods, staticMethods, staticFields, classDecl.isAbstract());
            globals.assign(classDecl.getName(), klass);
        }
        
        Function staticMainMethod = null;
        for (ClassDecl classDecl : program.getClasses()) {
            FunctionDecl method = classDecl.findMethod("main");
            if (method != null && method.hasModifier(Modifier.STATIC)) {
                DhrClass klass = (DhrClass) globals.get(classDecl.getName());
                staticMainMethod = klass.findStaticMethod("main");
                break;
            }
        }
        
        if (staticMainMethod == null) {
            throw ErrorFactory.accessError("Entry point error: No static main method found. Please define 'static kaam main()' in any class.", 
                                           (SourceLocation) null);
        }
        
        if (staticMainMethod.arity() == 0) {
            staticMainMethod.call(this, List.of());
        } else {
            throw ErrorFactory.typeError("Entry point 'main' should not have parameters.", 
                                        (SourceLocation) null);
        }
    }



    public void executeBlock(List<Statement> statements, Environment environment) {
        for (Statement stmt : statements) {
            execute(stmt, environment);
        }
    }

    public void execute(Statement stmt, Environment env) {
        if (stmt instanceof IfStmt ifStmt) {
            Object conditionValue = evaluate(ifStmt.getCondition(), env);
            if (isTruthy(conditionValue)) {
                if (ifStmt.getThenBranch() instanceof Block) {
                    execute(ifStmt.getThenBranch(), env); 
                } else {
                    execute(ifStmt.getThenBranch(), new Environment(env));
                }
            } else if (ifStmt.getElseBranch() != null) {
                if (ifStmt.getElseBranch() instanceof Block) {
                    execute(ifStmt.getElseBranch(), env);
                } else {
                    execute(ifStmt.getElseBranch(), new Environment(env));
                }
            }
        }
        else if (stmt instanceof WhileStmt whileStmt) {
            boolean previousLoopState = inLoop;
            inLoop = true;
            try {
                while (isTruthy(evaluate(whileStmt.getCondition(), env))) {
                    try {
                        execute(whileStmt.getBody(), env);
                    } catch (BreakException be) {
                        break;
                    } catch (ContinueException ce) {
                        continue;
                    }
                }
            } finally {
                inLoop = previousLoopState;
            }
        } else if (stmt instanceof BreakStmt) {
            if (!inLoop) {
                throw ErrorFactory.validationError("'break' statement not within a loop", ErrorFactory.getLocation(stmt));
            }
            throw new BreakException();
        } else if (stmt instanceof ContinueStmt) {
            if (!inLoop) {
                throw ErrorFactory.validationError("'continue' statement not within a loop", ErrorFactory.getLocation(stmt));
            }
            throw new ContinueException();
        }
        else if (stmt instanceof TryStmt tryStmt) {
            executeTryStmt(tryStmt, env);
        } else if (stmt instanceof ThrowStmt throwStmt) {
            Object value = evaluate(throwStmt.getValue(), env);
            dhrlang.error.SourceLocation location = null;
            if (throwStmt.getThrowToken() != null) {
                location = throwStmt.getThrowToken().getLocation();
            }
            DhrRuntimeException exception = new DhrRuntimeException(value, location);
            String originalMessage = exception.getDetailedMessage();
            String stackTrace = executionStack.getStackTrace();
            String enhancedMessage = originalMessage + "\nStack trace:\n" + stackTrace;
            throw new DhrRuntimeException(value, location) {
                @Override
                public String getDetailedMessage() {
                    return enhancedMessage;
                }
            };
        } else if (stmt instanceof ExpressionStmt exprStmt) {
            evaluate(exprStmt.getExpression(), env);
        } else if (stmt instanceof PrintStmt printStmt) {
            Object value = evaluate(printStmt.getExpression(), env);
            System.out.println(value);
        } else if (stmt instanceof ReturnStmt returnStmt) {
            Object value = returnStmt.getValue() != null ? evaluate(returnStmt.getValue(), env) : null;
            throw new ReturnValue(value);
        } else if (stmt instanceof BreakStmt) {
            throw new BreakException();
        } else if (stmt instanceof ContinueStmt) {
            throw new ContinueException();
        } else if (stmt instanceof Block block) {
            executeBlock(block.getStatements(), new Environment(env));
        } else if (stmt instanceof VarDecl varDecl) {
            Object value = varDecl.getInitializer() != null ? evaluate(varDecl.getInitializer(), env) : null;
            env.define(varDecl.getName(), value);
        } else if (stmt instanceof FunctionDecl funcDecl) {
            Function function = new Function(funcDecl, env);
            env.define(funcDecl.getName(), function);
        } else {
            throw ErrorFactory.systemError("Unsupported statement type: " + stmt.getClass(), ErrorFactory.getLocation(stmt));
        }
    }
    
    private void executeTryStmt(TryStmt tryStmt, Environment env) {
        boolean finallyExecuted = false;
        try {
            execute(tryStmt.getTryBlock(), env);
        } catch (DhrRuntimeException e) {
            boolean caught = false;
            for (CatchClause catchClause : tryStmt.getCatchClauses()) {
                Environment catchEnv = new Environment(env);
                catchEnv.define(catchClause.getParameter(), e.getValue());
                try {
                    execute(catchClause.getBody(), catchEnv);
                    caught = true;
                    break;
                } catch (DhrRuntimeException nestedE) {
                    throw nestedE;
                }
            }
            if (!caught) {
                throw e;
            }
        } catch (BreakException | ContinueException | ReturnValue controlFlow) {
            if (tryStmt.getFinallyBlock() != null) {
                try {
                    execute(tryStmt.getFinallyBlock(), env);
                    finallyExecuted = true;
                } catch (Exception finallyException) {
                    throw new DhrRuntimeException("Exception in finally block: " + finallyException.getMessage(), null);
                }
            }
            throw controlFlow; 
        } finally {
            if (!finallyExecuted && tryStmt.getFinallyBlock() != null) {
                try {
                    execute(tryStmt.getFinallyBlock(), env);
                } catch (DhrRuntimeException finallyException) {
                    throw finallyException;
                } catch (Exception finallyException) {
                    throw new DhrRuntimeException("Exception in finally block: " + finallyException.getMessage(), null);
                }
            }
        }
    }
    
    private Object evaluatePostfixIncrement(PostfixIncrementExpr expr, Environment env) {
        Expression target = expr.getTarget();
        boolean isIncrement = expr.isIncrement();

        if (target instanceof VariableExpr varExpr) {
            String varName = varExpr.getName().getLexeme();
            Object currentValue = env.get(varName);

            validateNumberForIncrement(currentValue, expr.getOperator());

            Long numValue = (Long) currentValue;
            Long newValue = isIncrement ? numValue + 1 : numValue - 1;

            env.assign(varName, newValue);
            return numValue;

        } else if (target instanceof GetExpr getExpr) {
            Object object = evaluate(getExpr.getObject(), env);
            if (!(object instanceof Instance instance)) {
                throw ErrorFactory.typeError("Can only increment/decrement object properties", ErrorFactory.getLocation(getExpr));
            }

            Object currentValue = instance.get(getExpr.getName());

            validateNumberForIncrement(currentValue, expr.getOperator());

            Long numValue = (Long) currentValue;
            Long newValue = isIncrement ? numValue + 1 : numValue - 1;

            instance.set(getExpr.getName(), newValue);
            return numValue;

        } else if (target instanceof IndexExpr indexExpr) {
            Object array = evaluate(indexExpr.getObject(), env);
            Object index = evaluate(indexExpr.getIndex(), env);

            if (!(array instanceof Object[])) {
                throw ErrorFactory.typeError("Can only increment/decrement array elements", ErrorFactory.getLocation(indexExpr));
            }
            if (!(index instanceof Long)) {
                throw ErrorFactory.typeError("Array index must be a number", ErrorFactory.getLocation(indexExpr));
            }

            Object[] arr = (Object[]) array;
            int i = ((Long) index).intValue();

            if (i < 0 || i >= arr.length) {
                throw ErrorFactory.indexError("Array index out of bounds", ErrorFactory.getLocation(indexExpr));
            }

            Object currentValue = arr[i];
            validateNumberForIncrement(currentValue, expr.getOperator());

            Long numValue = (Long) currentValue;
            Long newValue = isIncrement ? numValue + 1 : numValue - 1;

            arr[i] = newValue;
            return numValue;
        }

        throw ErrorFactory.validationError("Invalid postfix increment/decrement target", ErrorFactory.getLocation(expr));
    }

    private Object evaluatePrefixIncrement(PrefixIncrementExpr expr, Environment env) {
        Expression target = expr.getTarget();
        boolean isIncrement = expr.isIncrement();

        if (target instanceof VariableExpr varExpr) {
            String varName = varExpr.getName().getLexeme();
            Object currentValue = env.get(varName);

            validateNumberForIncrement(currentValue, expr.getOperator());

            Long numValue = (Long) currentValue;
            Long newValue = isIncrement ? numValue + 1 : numValue - 1;

            env.assign(varName, newValue);
            return newValue; 

        } else if (target instanceof GetExpr getExpr) {
            Object object = evaluate(getExpr.getObject(), env);
            if (!(object instanceof Instance instance)) {
                throw ErrorFactory.typeError("Can only increment/decrement object properties", ErrorFactory.getLocation(getExpr));
            }

            Object currentValue = instance.get(getExpr.getName());

            validateNumberForIncrement(currentValue, expr.getOperator());

            Long numValue = (Long) currentValue;
            Long newValue = isIncrement ? numValue + 1 : numValue - 1;

            instance.set(getExpr.getName(), newValue);
            return newValue;

        } else if (target instanceof IndexExpr indexExpr) {
            Object array = evaluate(indexExpr.getObject(), env);
            Object index = evaluate(indexExpr.getIndex(), env);

            if (!(array instanceof Object[])) {
                throw ErrorFactory.typeError("Can only increment/decrement array elements", ErrorFactory.getLocation(indexExpr));
            }
            if (!(index instanceof Long)) {
                throw ErrorFactory.typeError("Array index must be a number", ErrorFactory.getLocation(indexExpr));
            }

            Object[] arr = (Object[]) array;
            int i = ((Long) index).intValue();

            if (i < 0 || i >= arr.length) {
                throw ErrorFactory.indexError("Array index out of bounds", ErrorFactory.getLocation(indexExpr));
            }

            Object currentValue = arr[i];
            validateNumberForIncrement(currentValue, expr.getOperator());

            Long numValue = (Long) currentValue;
            Long newValue = isIncrement ? numValue + 1 : numValue - 1;

            arr[i] = newValue;
            return newValue;
        }

        throw ErrorFactory.validationError("Invalid prefix increment/decrement target", ErrorFactory.getLocation(expr));
    }

    private void validateNumberForIncrement(Object value, Token operator) {
        if (!(value instanceof Long)) {
            throw ErrorFactory.typeError("Can only increment/decrement numbers, got: " +
                    (value == null ? "null" : value.getClass().getSimpleName()), operator);
        }
    }
    public Object evaluate(Expression expr, Environment env) {
        if (expr instanceof PostfixIncrementExpr postfixExpr) {
            return evaluatePostfixIncrement(postfixExpr, env);
        }

        if (expr instanceof PrefixIncrementExpr prefixExpr) {
            return evaluatePrefixIncrement(prefixExpr, env);
        }



        if (expr instanceof NewExpr newExpr) {
            String className = newExpr.getClassName();
            Object klassObj = globals.get(className);
            if (!(klassObj instanceof DhrClass)) {
                throw ErrorFactory.typeError("Can only instantiate classes, not '" + className + "'.", ErrorFactory.getLocation(newExpr));
            }
            DhrClass klass = (DhrClass) klassObj;
            List<Object> arguments = new ArrayList<>();
            for (Expression arg : newExpr.getArguments()) {
                arguments.add(evaluate(arg, env)); 
            }
            
            try {
                // Set current call location for error reporting
                SourceLocation previousLocation = currentCallLocation;
                currentCallLocation = ErrorFactory.getLocation(newExpr);
                Object result = klass.call(this, arguments);
                currentCallLocation = previousLocation; // Restore previous location
                return result;
            } catch (RuntimeError e) {
                throw ErrorFactory.runtimeError(e.getMessage(), ErrorFactory.getLocation(newExpr));
            }
        }
        if (expr instanceof SuperExpr superExpr) {
            String methodName = superExpr.method.getLexeme();

            Instance instance = (Instance) env.get("this");
            DhrClass superclass = instance.getKlass().superclass;

            if (superclass == null) {
                throw ErrorFactory.accessError("Cannot use 'super' in a class with no superclass", ErrorFactory.getLocation(superExpr));
            }

            Function method = superclass.findMethod(methodName);
            if (method == null) {
                throw ErrorFactory.accessError("Undefined method '" + methodName + "' in superclass", ErrorFactory.getLocation(superExpr));
            }
            return method.bind(instance);
        }
        if(expr instanceof ThisExpr){
            return env.get("this");
        }

        if (expr instanceof GetExpr getExpr) {
            Object object = evaluate(getExpr.getObject(), env);
            if (object instanceof Object[] && getExpr.getName().getLexeme().equals("length")) {
                return (long) ((Object[]) object).length;
            }
            if (object instanceof String && getExpr.getName().getLexeme().equals("length")) {
                return (long) ((String) object).length();
            }

            if (object instanceof Instance) {
                return ((Instance) object).get(getExpr.getName());
            }
            throw ErrorFactory.typeError("Only instances have properties", ErrorFactory.getLocation(getExpr));
        }
        
        if (expr instanceof StaticAccessExpr staticExpr) {
            String className = staticExpr.className.getName().getLexeme();
            String memberName = staticExpr.memberName.getLexeme();
            
            Object classObj = globals.get(className);
            if (!(classObj instanceof DhrClass)) {
                throw ErrorFactory.typeError("'" + className + "' is not a class", ErrorFactory.getLocation(staticExpr));
            }
            
            DhrClass dhrClass = (DhrClass) classObj;
            
            // Try to get static field first
            try {
                return dhrClass.getStaticField(memberName, ErrorFactory.getLocation(staticExpr));
            } catch (DhrRuntimeException e) {
                // If not a field, try static method
                Function staticMethod = dhrClass.findStaticMethod(memberName);
                if (staticMethod != null) {
                    return staticMethod;
                }
                throw ErrorFactory.accessError("Static member '" + memberName + "' not found in class '" + className + "'", ErrorFactory.getLocation(staticExpr));
            }
        }
        
        if (expr instanceof StaticAssignExpr staticAssignExpr) {
            String className = staticAssignExpr.className.getName().getLexeme();
            String memberName = staticAssignExpr.memberName.getLexeme();
            Object value = evaluate(staticAssignExpr.value, env);
            
            Object classObj = globals.get(className);
            if (!(classObj instanceof DhrClass)) {
                throw ErrorFactory.typeError("'" + className + "' is not a class", ErrorFactory.getLocation(staticAssignExpr));
            }
            
            DhrClass dhrClass = (DhrClass) classObj;
            dhrClass.setStaticField(memberName, value);
            return value;
        }
        if (expr instanceof SetExpr setExpr) {
            Object object = evaluate(setExpr.getObject(), env);
            if (object instanceof Instance instance) {
                Object value = evaluate(setExpr.getValue(), env);
                instance.set(setExpr.getName(), value);
                return value;
            } else {
                throw ErrorFactory.typeError("Only objects have fields", ErrorFactory.getLocation(setExpr));
            }
        }

        if (expr instanceof LiteralExpr lit) {
            return lit.getValue();
        }

        if (expr instanceof VariableExpr var) {
            return env.get(var.getName().getLexeme());
        }

        if (expr instanceof AssignmentExpr assign) {
            String name = assign.getName().getLexeme();
            Object value = evaluate(assign.getValue(), env);
            env.assign(name, value);
            return value;
        }
        if (expr instanceof CallExpr call) {
            Object callee = evaluate(call.getCallee(), env);

        if (!(callee instanceof Callable function)) {
            throw ErrorFactory.typeError("Can only call functions and classes", ErrorFactory.getLocation(call));
        }

        List<Object> arguments = new ArrayList<>();
        for (Expression argument : call.getArguments()) {
            arguments.add(evaluate(argument, env));
        }

        if (arguments.size() != function.arity()) {
            throw ErrorFactory.validationError("Expected " + function.arity() + " arguments but got " + arguments.size(), ErrorFactory.getLocation(call));
        }            
            String functionName = "unknown";
            if (function instanceof Function func) {
                functionName = func.getDeclaration().getName();
                executionStack.push(functionName, null, null);
            }

            try {
                SourceLocation previousLocation = currentCallLocation;
                currentCallLocation = ErrorFactory.getLocation(call);
                Object result = function.call(this, arguments);
                currentCallLocation = previousLocation;
                return result;
            } finally {
                if (function instanceof Function) {
                    executionStack.pop();
                }
            }
        }


        if (expr instanceof UnaryExpr unary) {
            Object right = evaluate(unary.getRight(), env);
            return evaluateUnary(unary.getOperator(), right);
        }

        if (expr instanceof BinaryExpr binary) {
            if (binary.getOperator().getType() == TokenType.AND) {
                Object left = evaluate(binary.getLeft(), env);
                if (!isTruthy(left)) return false; 
                return isTruthy(evaluate(binary.getRight(), env));
            }
            
            if (binary.getOperator().getType() == TokenType.OR) {
                Object left = evaluate(binary.getLeft(), env);
                if (isTruthy(left)) return left; 
                return evaluate(binary.getRight(), env);
            }
            
            Object left = evaluate(binary.getLeft(), env);
            Object right = evaluate(binary.getRight(), env);
            return evaluateBinary(binary.getOperator(), left, right);
        }
        if (expr instanceof ArrayExpr) {
            return evaluateArray((ArrayExpr) expr, env);
        }
        if (expr instanceof NewArrayExpr) {
            return evaluateNewArray((NewArrayExpr) expr, env);
        }
        if (expr instanceof IndexExpr) {
            return evaluateIndex((IndexExpr) expr, env);
        }
        if (expr instanceof IndexAssignExpr) {
            return evaluateIndexAssign((IndexAssignExpr) expr, env);
        }


        throw ErrorFactory.systemError("Unsupported expression type: " + expr.getClass(), ErrorFactory.getLocation(expr));
    }
    private Object evaluateArray(ArrayExpr expr, Environment env) {
        Object[] array = new Object[expr.getElements().size()];

        for (int i = 0; i < expr.getElements().size(); i++) {
            array[i] = evaluate(expr.getElements().get(i), env);
        }

        return array;
    }

    private Object evaluateNewArray(NewArrayExpr expr, Environment env) {
        Object sizeValue = evaluate(expr.getSize(), env);
        
        if (!(sizeValue instanceof Long)) {
            throw new DhrRuntimeException("Array size must be a number.", null);
        }
        
        int size = ((Long) sizeValue).intValue();
        if (size < 0) {
            throw new DhrRuntimeException("Array size cannot be negative.", null);
        }
        
        Object[] array = new Object[size];
        Object defaultValue = getDefaultValue(expr.getElementType());
        
        for (int i = 0; i < size; i++) {
            array[i] = defaultValue;
        }
        
        return array;
    }
    
    private Object getDefaultValue(String type) {
        switch (type) {
            case "num": return 0L;
            case "duo": return 0.0;
            case "kya": return false;
            case "ek": return '\0';
            case "sab": return "";
            default: return null; // For object types
        }
    }

    private Object evaluateIndex(IndexExpr expr, Environment env) {
        Object object = evaluate(expr.getObject(), env);
        Object index = evaluate(expr.getIndex(), env);

        if (!(object instanceof Object[])) {
            throw ErrorFactory.typeError("Can only index arrays.", ErrorFactory.getLocation(expr));
        }

        if (!(index instanceof Long)) {
            throw ErrorFactory.typeError("Array index must be a number.", ErrorFactory.getLocation(expr));
        }

        Object[] array = (Object[]) object;
        int i = ((Long) index).intValue();

        if (i < 0 || i >= array.length) {
            throw ErrorFactory.indexError("Array index " + i + " out of bounds for array of length " + array.length + ".", ErrorFactory.getLocation(expr));
        }

        return array[i];
    }

    private Object evaluateIndexAssign(IndexAssignExpr expr, Environment env) {
        Object object = evaluate(expr.getObject(), env);
        Object index = evaluate(expr.getIndex(), env);
        Object value = evaluate(expr.getValue(), env);

        if (!(object instanceof Object[])) {
            throw ErrorFactory.typeError("Can only assign to array elements.", ErrorFactory.getLocation(expr));
        }

        if (!(index instanceof Long)) {
            throw ErrorFactory.typeError("Array index must be a number.", ErrorFactory.getLocation(expr));
        }

        Object[] array = (Object[]) object;
        int i = ((Long) index).intValue();

        if (i < 0 || i >= array.length) {
            throw ErrorFactory.indexError("Array index " + i + " out of bounds for array of length " + array.length + ".", ErrorFactory.getLocation(expr));
        }

        array[i] = value;
        return value;
    }

    private Object evaluateUnary(Token operator, Object right) {
        return switch (operator.getType()) {
            case MINUS -> {
                if (right instanceof Long) yield -((Long) right);
                if (right instanceof Double) yield -((Double) right);
                throw new DhrRuntimeException("Operand for '-' must be a number.", null);
            }
            case NOT -> !isTruthy(right);
            default -> throw new DhrRuntimeException("Unsupported unary operator: " + operator.getType(), null);
        };
    }

    private Object evaluateBinary(Token operator, Object left, Object right) {
        switch (operator.getType()) {
            case PLUS:
                if (left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }
                if (left instanceof Double || right instanceof Double) {
                    return toDouble(left) + toDouble(right);
                }
                if (left instanceof Long && right instanceof Long) {
                    return (Long) left + (Long) right;
                }
                throw new DhrRuntimeException("Operands for '+' must be two numbers or at least one string for concatenation.", null);


            case MINUS:
                validateNumberOperands(operator, left, right);
                if (left instanceof Double || right instanceof Double) {
                    return toDouble(left) - toDouble(right);
                }
                return ((Long) left) - ((Long) right);


            case STAR:
                if (left instanceof Double || right instanceof Double) {
                    return toDouble(left) * toDouble(right);
                }
                return (Long) left * (Long) right;

            case SLASH:
                double divisor = toDouble(right);
                if (divisor == 0.0) throw ErrorFactory.arithmeticError("Division by zero.", operator);
                return toDouble(left) / divisor;
            case MOD:
                if (left instanceof Double || right instanceof Double) {
                    return toDouble(left) % toDouble(right);
                }
                return (Long) left % (Long) right;


            case EQUALITY:
                return isEqual(left, right);

            case NEQ:
                return !isEqual(left, right);

            case GREATER:
                if (left instanceof Long && right instanceof Long) {
                    return (Long) left > (Long) right;
                }
                return toDouble(left) > toDouble(right);

            case GEQ:
                if (left instanceof Long && right instanceof Long) {
                    return (Long) left >= (Long) right;
                }
                return toDouble(left) >= toDouble(right);

            case LESS:
                if (left instanceof Long && right instanceof Long) {
                    return (Long) left < (Long) right;
                }
                return toDouble(left) < toDouble(right);

            case LEQ:
                if (left instanceof Long && right instanceof Long) {
                    return (Long) left <= (Long) right;
                }
                return toDouble(left) <= toDouble(right);

            default:
                throw new DhrRuntimeException("Unsupported binary operator: " + operator.getType(), null);
        }
    }
    private void validateNumberOperands(Token operator, Object left, Object right) {
        if (left == null || right == null) {
            throw new DhrRuntimeException("Null operand for operator: " + operator.getLexeme(), null);
        }
        if (!(left instanceof Number && right instanceof Number)) {
            throw new DhrRuntimeException("Operands must be numbers for operator: " + operator.getLexeme(), null);
        }
    }

    private Double toDouble(Object operand) {
        if (operand instanceof Double) return (Double) operand;
        if (operand instanceof Long) return ((Long) operand).doubleValue();
        throw new DhrRuntimeException("Operand must be a number.", null);
    }

    private boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        return Objects.equals(a, b);
    }

    

    
    private String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return (String) value;
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Long || value instanceof Double) return value.toString();
        return value.toString();
    }

    private void initGlobals() {
        globals.define("clock", new NativeFunction() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis();
            }

            @Override
            public String toString() {
                return "<native fn clock>";
            }
        });

        globals.define("printLine", new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                System.out.println(formatForPrint(arg));
                return null;
            }

            @Override
            public String toString() {
                return "<native fn printLine>";
            }
        });
        
        globals.define("print", new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                System.out.print(formatForPrint(arg));
                return null;
            }

            @Override
            public String toString() {
                return "<native fn print>";
            }
        });

        globals.define("abs", MathFunctions.abs());
        globals.define("sqrt", MathFunctions.sqrt());
        globals.define("pow", MathFunctions.pow());
        globals.define("min", MathFunctions.min());
        globals.define("max", MathFunctions.max());
        globals.define("floor", MathFunctions.floor());
        globals.define("ceil", MathFunctions.ceil());
        globals.define("round", MathFunctions.round());
        globals.define("random", MathFunctions.random());
        
        globals.define("sin", MathFunctions.sin());
        globals.define("cos", MathFunctions.cos());
        globals.define("tan", MathFunctions.tan());
        globals.define("log", MathFunctions.log());
        globals.define("log10", MathFunctions.log10());
        globals.define("exp", MathFunctions.exp());
        globals.define("randomRange", MathFunctions.randomRange());
        globals.define("clamp", MathFunctions.clamp());

        globals.define("length", StringFunctions.length());
        globals.define("substring", StringFunctions.substring());
        globals.define("charAt", StringFunctions.charAt());
        globals.define("toUpperCase", StringFunctions.toUpperCase());
        globals.define("toLowerCase", StringFunctions.toLowerCase());
        globals.define("indexOf", StringFunctions.indexOf());
        globals.define("replace", StringFunctions.replace());
        globals.define("startsWith", StringFunctions.startsWith());
        globals.define("endsWith", StringFunctions.endsWith());
        globals.define("trim", StringFunctions.trim());

        globals.define("readLine", IOFunctions.readLine());
        globals.define("readLineWithPrompt", IOFunctions.readLineWithPrompt());
        globals.define("toNum", IOFunctions.toNum());
        globals.define("toDuo", IOFunctions.toDuo());
        globals.define("toString", IOFunctions.toStringFunc());
        
        globals.define("split", StringFunctions.split());
        globals.define("join", StringFunctions.join());
        globals.define("repeat", StringFunctions.repeat());
        globals.define("reverse", StringFunctions.reverse());
        globals.define("padLeft", StringFunctions.padLeft());
        globals.define("padRight", StringFunctions.padRight());

        globals.define("arrayLength", ArrayFunctions.arrayLength());
        globals.define("arrayContains", ArrayFunctions.arrayContains());
        globals.define("arrayIndexOf", ArrayFunctions.arrayIndexOf());
        globals.define("arrayCopy", ArrayFunctions.arrayCopy());

        globals.define("arrayReverse", ArrayFunctions.arrayReverse());
        globals.define("arraySlice", ArrayFunctions.arraySlice());
        globals.define("arraySort", ArrayFunctions.arraySort());
        globals.define("arrayConcat", ArrayFunctions.arrayConcat());
        globals.define("arrayFill", ArrayFunctions.arrayFill());
        globals.define("arraySum", ArrayFunctions.arraySum());
        globals.define("arrayAverage", ArrayFunctions.arrayAverage());
        globals.define("arrayPush", ArrayFunctions.arrayPush());
        globals.define("arrayPop", ArrayFunctions.arrayPop());
        globals.define("arrayInsert", ArrayFunctions.arrayInsert());

        globals.define("isNum", UtilityFunctions.isNum());
        globals.define("isDuo", UtilityFunctions.isDuo());
        globals.define("isSab", UtilityFunctions.isSab());
        globals.define("isKya", UtilityFunctions.isKya());
        globals.define("isArray", UtilityFunctions.isArray());
        globals.define("typeOf", UtilityFunctions.typeOf());
        globals.define("range", UtilityFunctions.range());
        globals.define("sleep", UtilityFunctions.sleep());
    }

    private String formatForPrint(Object obj) {
        if (obj == null) {
            return "null";
        }
        
        if (obj instanceof Object[]) {
            Object[] array = (Object[]) obj;
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
        
        return obj.toString();
    }
}
