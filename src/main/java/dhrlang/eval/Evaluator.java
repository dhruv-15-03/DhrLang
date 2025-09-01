package dhrlang.eval;

import dhrlang.ast.*;
import dhrlang.interpreter.*;
import dhrlang.runtime.AccessController;
import dhrlang.error.ErrorFactory;


public class Evaluator implements ASTVisitor<Object> {
    private final Interpreter interpreter;
    private Environment env; // current lexical environment during traversal
    public Evaluator(Interpreter interpreter){ this.interpreter = interpreter; }

    public void execute(Statement stmt, Environment environment){ this.env = environment; stmt.accept(this); }
    public void executeBlock(java.util.List<Statement> statements, Environment environment){
        Environment previous = this.env;
        this.env = environment;
        try { for(Statement s: statements){ s.accept(this); } } finally { this.env = previous; }
    }
    public Object evaluate(Expression expr, Environment environment){ this.env = environment; return expr.accept(this); }

    @Override public Object visitBlock(Block block) {
        boolean isForLoopBody = block.isDesugaredForLoopBody();
        Environment previous = env;
        Environment blockEnv = isForLoopBody ? env : new Environment(env);
        if(!isForLoopBody) env = blockEnv;
        try { for(Statement s: block.getStatements()) s.accept(this); }
        finally { if(!isForLoopBody) env = previous; }
        return null; }
    @Override public Object visitExpressionStmt(ExpressionStmt expressionStmt) {
        Expression expr = expressionStmt.getExpression();
        if (expr instanceof CallExpr) {
            var prev = interpreter.getCurrentCallLocation();
            interpreter.setCurrentCallLocation(ErrorFactory.getLocation(expr));
            try { expr.accept(this); } finally { interpreter.setCurrentCallLocation(prev); }
        } else {
            expr.accept(this);
        }
        return null; }
    @Override public Object visitPrintStmt(PrintStmt printStmt) { Object value = printStmt.getExpression().accept(this); System.out.println(value); return null; }
    @Override public Object visitVarDecl(VarDecl varDecl) {
        Object value = null; if(varDecl.getInitializer()!=null){ value = varDecl.getInitializer().accept(this); }
        // If we're directly inside a desugared for-loop synthetic block, its env == parent env (handled in visitBlock)
        // So just define in current env. If not, still define in current env (standard block scoping).
        env.define(varDecl.getName(), value); return null; }
    @Override public Object visitReturnStmt(ReturnStmt returnStmt) { Object value = null; if(returnStmt.getValue()!=null){ value = returnStmt.getValue().accept(this); } throw new ReturnValue(value); }

    // Placeholders for unmigrated statements
    @Override public Object visitIfStmt(IfStmt ifStmt) {
        Object cond = ifStmt.getCondition().accept(this);
        if(isTruthy(cond)) {
            ifStmt.getThenBranch().accept(this);
        } else if (ifStmt.getElseBranch()!=null) {
            ifStmt.getElseBranch().accept(this);
        }
        return null;
    }
    @Override public Object visitWhileStmt(WhileStmt whileStmt) {
        boolean prev = interpreter.isInLoop();
        interpreter.setInLoop(true);
        try {
            while(isTruthy(whileStmt.getCondition().accept(this))){
                try { whileStmt.getBody().accept(this); }
                catch (BreakException b){ break; }
                catch (ContinueException c){
                    Statement body = whileStmt.getBody();
                    if(body instanceof Block block && block.isDesugaredForLoopBody()){
                        var stmts = block.getStatements();
                        if(!stmts.isEmpty()){
                            Statement last = stmts.get(stmts.size()-1);
                            try { last.accept(this); } catch (BreakException | ContinueException ignored) {}
                        }
                    }
                    continue; }
            }
        } finally { interpreter.setInLoop(prev); }
        return null;
    }
    @Override public Object visitBreakStmt(BreakStmt breakStmt) { if(!interpreter.isInLoop()) throw ErrorFactory.validationError("'break' statement not within a loop", ErrorFactory.getLocation(breakStmt)); throw new BreakException(); }
    @Override public Object visitContinueStmt(ContinueStmt continueStmt) { if(!interpreter.isInLoop()) throw ErrorFactory.validationError("'continue' statement not within a loop", ErrorFactory.getLocation(continueStmt)); throw new ContinueException(); }
    @Override public Object visitTryStmt(TryStmt tryStmt) {
    // Execute try/catch/finally. Debug printing removed after stabilization.
        boolean finallyExecuted = false;
        try {
            tryStmt.getTryBlock().accept(this);
        } catch (DhrRuntimeException e) {
            boolean caught = false;
            for (CatchClause cc : tryStmt.getCatchClauses()) {
                if (canCatch(cc.getExceptionType(), e)) {
                    Environment catchEnv = new Environment(env);
                    Object val = e.getValue();
                    if(!(val instanceof dhrlang.stdlib.exceptions.DhrException)) {
                        String targetType = cc.getExceptionType();
                        dhrlang.error.SourceLocation loc = e.getLocation();
                        if(!"any".equals(targetType)) {
                            val = switch(targetType){
                                case "ArithmeticException" -> new dhrlang.stdlib.exceptions.ArithmeticException(String.valueOf(val), loc);
                                case "IndexOutOfBoundsException" -> new dhrlang.stdlib.exceptions.IndexOutOfBoundsException(String.valueOf(val), loc);
                                case "TypeException" -> new dhrlang.stdlib.exceptions.TypeException(String.valueOf(val), loc);
                                case "NullPointerException" -> new dhrlang.stdlib.exceptions.NullPointerException(String.valueOf(val), loc);
                                case "Error", "DhrException" -> new dhrlang.stdlib.exceptions.DhrException(String.valueOf(val), loc);
                                default -> val;
                            };
                        }
                    }
                    catchEnv.define(cc.getParameter(), val);
                    Environment prev = env; env = catchEnv;
                    try { cc.getBody().accept(this); } finally { env = prev; }
                    caught = true; break;
                }
            }
            if(!caught) throw e;
        } catch (BreakException | ContinueException | ReturnValue control) {
            if(tryStmt.getFinallyBlock()!=null){
                try { tryStmt.getFinallyBlock().accept(this); finallyExecuted = true; }
                catch (Exception fe){ throw new DhrRuntimeException("Exception in finally block: "+fe.getMessage(), null); }
            }
            throw control;
        } finally {
            if(!finallyExecuted && tryStmt.getFinallyBlock()!=null){
                try { tryStmt.getFinallyBlock().accept(this); }
                catch (DhrRuntimeException dre){ throw dre; }
                catch (Exception fe){ throw new DhrRuntimeException("Exception in finally block: "+fe.getMessage(), null); }
            }
        }
        return null;
    }
    @Override public Object visitCatchClause(CatchClause catchClause) { return null; }
    @Override public Object visitThrowStmt(ThrowStmt throwStmt) {
        Object value = throwStmt.getValue().accept(this);
        dhrlang.error.SourceLocation location = throwStmt.getThrowToken()!=null? throwStmt.getThrowToken().getLocation(): null;
    Object payload = value instanceof dhrlang.stdlib.exceptions.DhrException
        ? value
        : new dhrlang.stdlib.exceptions.ErrorException(String.valueOf(value), location);
    throw new DhrRuntimeException(payload, location, dhrlang.error.RuntimeErrorCategory.USER_EXCEPTION);
    }
    @Override public Object visitFunctionDecl(FunctionDecl functionDecl) { Function fn = new Function(functionDecl, env); env.define(functionDecl.getName(), fn); return null; }
    @Override public Object visitClassDecl(ClassDecl classDecl) { return null; }
    @Override public Object visitInterfaceDecl(InterfaceDecl interfaceDecl) { return null; }
    @Override public Object visitProgram(Program program) { return null; }

    // === Expressions migrated ===
    @Override public Object visitLiteralExpr(LiteralExpr literalExpr) { return literalExpr.getValue(); }
    @Override public Object visitVariableExpr(VariableExpr variableExpr) {
        try { return env.get(variableExpr.getName().getLexeme()); }
        catch (DhrRuntimeException e){ throw ErrorFactory.accessError(e.getMessage(), ErrorFactory.getLocation(variableExpr)); }
    }
    @Override public Object visitAssignmentExpr(AssignmentExpr assignmentExpr) {
        Object value = assignmentExpr.getValue().accept(this);
        try { env.assign(assignmentExpr.getName().getLexeme(), value); return value; }
        catch (DhrRuntimeException e){ throw ErrorFactory.accessError(e.getMessage(), ErrorFactory.getLocation(assignmentExpr)); }
    }
    @Override public Object visitBinaryExpr(BinaryExpr binaryExpr) {
        // short-circuit AND / OR
        if(binaryExpr.getOperator().getType()==dhrlang.lexer.TokenType.AND){ Object left = binaryExpr.getLeft().accept(this); if(!isTruthy(left)) return false; return isTruthy(binaryExpr.getRight().accept(this)); }
        if(binaryExpr.getOperator().getType()==dhrlang.lexer.TokenType.OR){ Object left = binaryExpr.getLeft().accept(this); if(isTruthy(left)) return left; return binaryExpr.getRight().accept(this); }
        Object left = binaryExpr.getLeft().accept(this);
        Object right = binaryExpr.getRight().accept(this);
        return evalBinaryInternal(binaryExpr.getOperator(), left, right);
    }
    @Override public Object visitUnaryExpr(UnaryExpr unaryExpr) { Object right = unaryExpr.getRight().accept(this); return evalUnaryInternal(unaryExpr.getOperator(), right); }
    @Override public Object visitPrefixIncrementExpr(PrefixIncrementExpr expr) { return evalPrefix(expr); }
    @Override public Object visitPostfixIncrementExpr(PostfixIncrementExpr expr) { return evalPostfix(expr); }

    // Placeholders for remaining expression types (to be migrated incrementally)
    @Override public Object visitThisExpr(ThisExpr thisExpr) { return env.get("this"); }
    @Override public Object visitSuperExpr(SuperExpr superExpr) {
        String methodName = superExpr.method.getLexeme();
        Instance instance = (Instance) env.get("this");
    DhrClass superclass = instance.getKlass().getSuperclass();
        if (superclass == null) throw ErrorFactory.accessError("Cannot use 'super' in a class with no superclass", ErrorFactory.getLocation(superExpr));
        Function method = superclass.findMethod(methodName);
        if (method == null) throw ErrorFactory.accessError("Undefined method '"+methodName+"' in superclass", ErrorFactory.getLocation(superExpr));
        return method.bind(instance);
    }
    @Override public Object visitGetExpr(GetExpr getExpr) {
        Object object = getExpr.getObject().accept(this);
        String name = getExpr.getName().getLexeme();
        if(object==null) throw ErrorFactory.nullError("Cannot access property '"+name+"' of null", ErrorFactory.getLocation(getExpr));
        if(object instanceof Object[] arr && name.equals("length")) return (long) arr.length;
        if(object instanceof String s && isBuiltInStringMethod(name)) return createBuiltInStringMethod(name, s);
        if(object instanceof Instance inst){
            DhrClass k = inst.getKlass();
            boolean isField = k.getFieldModifiers(name).size()>0 || k.findDeclaringClassForField(name)!=null;
            boolean isMethod = k.getMethodModifiers(name).size()>0 || k.findDeclaringClassForMethod(name)!=null;
            DhrClass declaring = isField? k.findDeclaringClassForField(name) : (isMethod? k.findDeclaringClassForMethod(name): null);
            if(declaring!=null){ AccessController.assertCanAccess(interpreter, declaring, name, isField, false, inst, getExpr); }
            return inst.get(getExpr.getName());
        }
        throw ErrorFactory.typeError("Only instances have properties", ErrorFactory.getLocation(getExpr));
    }
    @Override public Object visitSetExpr(SetExpr setExpr) {
        Object obj = setExpr.getObject().accept(this);
        if(obj==null) throw ErrorFactory.nullError("Cannot set property '"+setExpr.getName().getLexeme()+"' on null", ErrorFactory.getLocation(setExpr));
        if(obj instanceof Instance inst){
            String fname = setExpr.getName().getLexeme();
            DhrClass declaring = inst.getKlass().findDeclaringClassForField(fname);
            if(declaring!=null) AccessController.assertCanAccess(interpreter, declaring, fname, true, false, inst, setExpr);
            Object value = setExpr.getValue().accept(this);
            inst.set(setExpr.getName(), value); return value;
        }
        throw ErrorFactory.typeError("Only instances have fields", ErrorFactory.getLocation(setExpr));
    }
    @Override public Object visitStaticAccessExpr(StaticAccessExpr staticAccessExpr) {
        String className = staticAccessExpr.className.getName().getLexeme();
        String memberName = staticAccessExpr.memberName.getLexeme();
        Object classObj = interpreter.getGlobals().get(className);
        if(!(classObj instanceof DhrClass dhrClass)) throw ErrorFactory.typeError("'"+className+"' is not a class", ErrorFactory.getLocation(staticAccessExpr));
        try {
            Object value = dhrClass.getStaticField(memberName, ErrorFactory.getLocation(staticAccessExpr));
            AccessController.assertCanAccess(interpreter, dhrClass, memberName, true, true, null, staticAccessExpr);
            return value;
        } catch (DhrRuntimeException e) {
            Function staticMethod = dhrClass.findStaticMethod(memberName);
            if(staticMethod!=null){ AccessController.assertCanAccess(interpreter, dhrClass, memberName, false, true, null, staticAccessExpr); return staticMethod; }
            throw ErrorFactory.accessError("Static member '"+memberName+"' not found in class '"+className+"'", ErrorFactory.getLocation(staticAccessExpr));
        }
    }
    @Override public Object visitStaticAssignExpr(StaticAssignExpr staticAssignExpr) {
        String className = staticAssignExpr.className.getName().getLexeme();
        String memberName = staticAssignExpr.memberName.getLexeme();
        Object value = staticAssignExpr.value.accept(this);
        Object classObj = interpreter.getGlobals().get(className);
        if(!(classObj instanceof DhrClass dhrClass)) throw ErrorFactory.typeError("'"+className+"' is not a class", ErrorFactory.getLocation(staticAssignExpr));
        AccessController.assertCanAccess(interpreter, dhrClass, memberName, true, true, null, staticAssignExpr);
        dhrClass.setStaticField(memberName, value); return value;
    }
    @Override public Object visitCallExpr(CallExpr callExpr) {
        Object callee = callExpr.getCallee().accept(this);
        if(!(callee instanceof Callable fn)) throw ErrorFactory.typeError("Can only call functions and classes", ErrorFactory.getLocation(callExpr));
        java.util.List<Object> args = new java.util.ArrayList<>();
        for(Expression a : callExpr.getArguments()) args.add(a.accept(this));
        if(args.size()!=fn.arity()) throw ErrorFactory.validationError("Expected "+fn.arity()+" arguments but got "+args.size(), ErrorFactory.getLocation(callExpr));
        var prevLoc = interpreter.getCurrentCallLocation();
        interpreter.setCurrentCallLocation(ErrorFactory.getLocation(callExpr));
        try { return fn.call(interpreter, args); } finally { interpreter.setCurrentCallLocation(prevLoc); }
    }
    @Override public Object visitNewExpr(NewExpr newExpr) {
        String className = newExpr.getClassName();
        String base = className; String[] typeArgs = new String[0];
        if(className.contains("<")) { base = className.substring(0, className.indexOf('<')); String typeArgsStr = className.substring(className.indexOf('<')+1, className.lastIndexOf('>')); typeArgs = typeArgsStr.split(","); for(int i=0;i<typeArgs.length;i++) typeArgs[i]=typeArgs[i].trim(); }
        // Special-case built-in generic user error: new Error()
        if("Error".equals(base)){
            if(!newExpr.getArguments().isEmpty()) throw ErrorFactory.typeError("Error() takes no arguments", ErrorFactory.getLocation(newExpr));
            return new dhrlang.stdlib.exceptions.ErrorException();
        }
        Object klassObj = interpreter.getGlobals().get(base);
        if(!(klassObj instanceof DhrClass klass)) throw ErrorFactory.typeError("Cannot instantiate unknown class '"+base+"'."+(className.contains("<")?" Generic types use base class name '"+base+"'.":""), ErrorFactory.getLocation(newExpr));
        java.util.List<Object> args = new java.util.ArrayList<>(); for(Expression e: newExpr.getArguments()) args.add(e.accept(this));
        var prevLoc = interpreter.getCurrentCallLocation(); interpreter.setCurrentCallLocation(ErrorFactory.getLocation(newExpr));
        try { Object result = klass.call(interpreter, args); if(typeArgs.length>0 && result instanceof Instance inst) inst.setGenericTypeArguments(typeArgs); return result; }
        catch (RuntimeError e){ throw ErrorFactory.runtimeError(e.getMessage(), ErrorFactory.getLocation(newExpr)); }
        finally { interpreter.setCurrentCallLocation(prevLoc); }
    }
    @Override public Object visitArrayExpr(ArrayExpr arrayExpr) {
        Object[] array = new Object[arrayExpr.getElements().size()]; for(int i=0;i<array.length;i++) array[i]=arrayExpr.getElements().get(i).accept(this); return array; }
    @Override public Object visitNewArrayExpr(NewArrayExpr newArrayExpr) {
        Object sizeVal = newArrayExpr.getSize().accept(this); if(!(sizeVal instanceof Long)) throw ErrorFactory.typeError("Array size must be a number.", ErrorFactory.getLocation(newArrayExpr)); int size=((Long)sizeVal).intValue(); if(size<0) throw ErrorFactory.validationError("Array size cannot be negative.", ErrorFactory.getLocation(newArrayExpr)); if(size>1_000_000) throw ErrorFactory.validationError("Array size too large (max: 1,000,000).", ErrorFactory.getLocation(newArrayExpr)); Object[] arr = new Object[size]; Object def = dhrlang.runtime.RuntimeDefaults.getDefaultValue(newArrayExpr.getElementType()); java.util.Arrays.fill(arr, def); return arr; }
    @Override public Object visitIndexExpr(IndexExpr indexExpr) {
        Object object = indexExpr.getObject().accept(this); Object index = indexExpr.getIndex().accept(this);
        if(!(object instanceof Object[] arr)) throw ErrorFactory.typeError("Can only index arrays.", ErrorFactory.getLocation(indexExpr));
        if(!(index instanceof Long)) throw ErrorFactory.typeError("Array index must be a number.", ErrorFactory.getLocation(indexExpr));
        int i = ((Long)index).intValue(); if(i<0 || i>=arr.length) throw ErrorFactory.indexError("Array index "+i+" out of bounds for array of length "+arr.length+".", ErrorFactory.getLocation(indexExpr));
        return arr[i]; }
    @Override public Object visitIndexAssignExpr(IndexAssignExpr indexAssignExpr) {
        Object object = indexAssignExpr.getObject().accept(this); Object index = indexAssignExpr.getIndex().accept(this); Object value = indexAssignExpr.getValue().accept(this);
        if(!(object instanceof Object[] arr)) throw ErrorFactory.typeError("Can only assign to array elements.", ErrorFactory.getLocation(indexAssignExpr));
        if(!(index instanceof Long)) throw ErrorFactory.typeError("Array index must be a number.", ErrorFactory.getLocation(indexAssignExpr));
        int i=((Long)index).intValue(); if(i<0 || i>=arr.length) throw ErrorFactory.indexError("Array index "+i+" out of bounds for array of length "+arr.length+".", ErrorFactory.getLocation(indexAssignExpr));
        arr[i]=value; return value; }
    @Override public Object visitGenericType(GenericType genericType) { return null; }
    @Override public Object visitTypeParameter(TypeParameter typeParameter) { return null; }

    private boolean isTruthy(Object v){ if(v==null) return false; if(v instanceof Boolean b) return b; return true; }
    private boolean canCatch(String catchType, DhrRuntimeException exception){
        if("any".equals(catchType)) return true;
        Object payload = exception.getValue();
        // Match by payload exception class simple name if available
        if(payload instanceof dhrlang.stdlib.exceptions.ErrorException){
            if("Error".equals(catchType) || "DhrException".equals(catchType)) return true;
        }
        if(payload instanceof dhrlang.stdlib.exceptions.DhrException dhrEx){
            String simple = dhrEx.getExceptionType();
            if(simple!=null && (simple.equals(catchType) || (catchType.endsWith("Exception") && simple.endsWith(catchType)))) return true;
            if("DhrException".equals(catchType)) return true;
        }
        String cat = exception.getCategory().toString();
        return switch(catchType){
            case "ArithmeticException" -> cat.contains("ARITHMETIC");
            case "IndexOutOfBoundsException" -> cat.contains("INDEX");
            case "TypeException" -> cat.contains("TYPE");
            case "NullPointerException" -> cat.contains("NULL");
            case "DhrException" -> true;
            default -> false;
        };
    }

    private Object evalUnaryInternal(dhrlang.lexer.Token operator, Object right){
        return switch(operator.getType()){
            case MINUS -> {
                if(right instanceof Long) yield -((Long)right);
                if(right instanceof Double) yield -((Double)right);
                throw ErrorFactory.typeError("Operand for '-' must be a number, got: "+(right==null?"null": right.getClass().getSimpleName()), operator);
            }
            case NOT -> !isTruthy(right);
            default -> throw ErrorFactory.systemError("Unsupported unary operator: "+operator.getType(), operator);
        };
    }
    private Object evalBinaryInternal(dhrlang.lexer.Token operator, Object left, Object right){
        switch(operator.getType()){
            case PLUS:
                if(left instanceof String || right instanceof String){ return stringify(left)+stringify(right); }
                if(left instanceof Double || right instanceof Double){ return toDouble(left)+toDouble(right); }
                if(left instanceof Long && right instanceof Long) return (Long)left + (Long)right;
                throw ErrorFactory.typeError("Operands for '+' must be two numbers or at least one string for concatenation.", operator);
            case MINUS:
                validateNumberOperands(operator,left,right);
                if(left instanceof Double || right instanceof Double) return toDouble(left)-toDouble(right);
                return (Long)left - (Long)right;
            case STAR:
                validateNumberOperands(operator,left,right);
                if(left instanceof Double || right instanceof Double) return toDouble(left)*toDouble(right);
                return (Long)left * (Long)right;
            case SLASH:
                validateNumberOperands(operator,left,right);
                double divisor = toDouble(right); if(divisor==0.0) throw ErrorFactory.arithmeticError("Division by zero.", operator); return toDouble(left)/divisor;
            case MOD:
                validateNumberOperands(operator,left,right);
                if(left instanceof Double || right instanceof Double){ double d = toDouble(right); if(d==0.0) throw ErrorFactory.arithmeticError("Modulo by zero.", operator); return toDouble(left)%d; }
                long md = (Long)right; if(md==0) throw ErrorFactory.arithmeticError("Modulo by zero.", operator); return (Long)left % md;
            case EQUALITY: return java.util.Objects.equals(left,right);
            case NEQ: return !java.util.Objects.equals(left,right);
            case GREATER:
                validateNumberOperands(operator,left,right);
                if(left instanceof Long && right instanceof Long) return (Long)left > (Long)right;
                return toDouble(left) > toDouble(right);
            case GEQ:
                validateNumberOperands(operator,left,right);
                if(left instanceof Long && right instanceof Long) return (Long)left >= (Long)right;
                return toDouble(left) >= toDouble(right);
            case LESS:
                validateNumberOperands(operator,left,right);
                if(left instanceof Long && right instanceof Long) return (Long)left < (Long)right;
                return toDouble(left) < toDouble(right);
            case LEQ:
                validateNumberOperands(operator,left,right);
                if(left instanceof Long && right instanceof Long) return (Long)left <= (Long)right;
                return toDouble(left) <= toDouble(right);
            default: throw ErrorFactory.systemError("Unsupported binary operator: "+operator.getType(), operator);
        }
    }
    private Object evalPostfix(PostfixIncrementExpr expr){
        Expression target = expr.getTarget(); boolean inc = expr.isIncrement();
        if(target instanceof VariableExpr v){ String name = v.getName().getLexeme(); Object current = env.get(name); validateNumberForIncrement(current, expr.getOperator()); Long num=(Long)current; Long newVal = inc? num+1: num-1; env.assign(name,newVal); return num; }
        if(target instanceof GetExpr g){ Object obj = g.getObject().accept(this); if(!(obj instanceof Instance inst)) throw ErrorFactory.typeError("Can only increment/decrement object properties", ErrorFactory.getLocation(g)); Object current = inst.get(g.getName()); validateNumberForIncrement(current, expr.getOperator()); Long num=(Long)current; Long newVal= inc? num+1: num-1; inst.set(g.getName(), newVal); return num; }
    if(target instanceof IndexExpr ix){ Object arrayObj = ix.getObject().accept(this); Object indexVal = ix.getIndex().accept(this); if(!(arrayObj instanceof Object[])) throw ErrorFactory.typeError("Can only increment/decrement array elements", ErrorFactory.getLocation(ix)); if(!(indexVal instanceof Long)) throw ErrorFactory.typeError("Array index must be a number", ErrorFactory.getLocation(ix)); int i=((Long)indexVal).intValue(); Object[] arr=(Object[])arrayObj; if(i<0||i>=arr.length) throw ErrorFactory.indexError("Array index out of bounds", ErrorFactory.getLocation(ix)); Object current=arr[i]; validateNumberForIncrement(current, expr.getOperator()); Long num=(Long)current; Long newVal= inc? num+1: num-1; arr[i]=newVal; return num; }
        throw ErrorFactory.validationError("Invalid postfix increment/decrement target", ErrorFactory.getLocation(expr));
    }
    private Object evalPrefix(PrefixIncrementExpr expr){
        Expression target = expr.getTarget(); boolean inc = expr.isIncrement();
        if(target instanceof VariableExpr v){ String name = v.getName().getLexeme(); Object current = env.get(name); validateNumberForIncrement(current, expr.getOperator()); Long num=(Long)current; Long newVal= inc? num+1: num-1; env.assign(name,newVal); return newVal; }
        if(target instanceof GetExpr g){ Object obj = g.getObject().accept(this); if(!(obj instanceof Instance inst)) throw ErrorFactory.typeError("Can only increment/decrement object properties", ErrorFactory.getLocation(g)); Object current = inst.get(g.getName()); validateNumberForIncrement(current, expr.getOperator()); Long num=(Long)current; Long newVal= inc? num+1: num-1; inst.set(g.getName(), newVal); return newVal; }
    if(target instanceof IndexExpr ix){ Object arrayObj = ix.getObject().accept(this); Object indexVal = ix.getIndex().accept(this); if(!(arrayObj instanceof Object[])) throw ErrorFactory.typeError("Can only increment/decrement array elements", ErrorFactory.getLocation(ix)); if(!(indexVal instanceof Long)) throw ErrorFactory.typeError("Array index must be a number", ErrorFactory.getLocation(ix)); int i=((Long)indexVal).intValue(); Object[] arr=(Object[])arrayObj; if(i<0||i>=arr.length) throw ErrorFactory.indexError("Array index out of bounds", ErrorFactory.getLocation(ix)); Object current=arr[i]; validateNumberForIncrement(current, expr.getOperator()); Long num=(Long)current; Long newVal= inc? num+1: num-1; arr[i]=newVal; return newVal; }
        throw ErrorFactory.validationError("Invalid prefix increment/decrement target", ErrorFactory.getLocation(expr));
    }
    private void validateNumberForIncrement(Object value, dhrlang.lexer.Token operator){ if(!(value instanceof Long)) throw ErrorFactory.typeError("Can only increment/decrement numbers, got: "+(value==null?"null": value.getClass().getSimpleName()), operator); }
    private void validateNumberOperands(dhrlang.lexer.Token operator, Object left, Object right){ if(left==null||right==null) throw ErrorFactory.typeError("Null operand for operator: "+operator.getLexeme(), operator); if(!(left instanceof Number && right instanceof Number)) throw ErrorFactory.typeError("Operands must be numbers for operator: "+operator.getLexeme()+", got: "+(left==null?"null":left.getClass().getSimpleName())+" and "+(right==null?"null":right.getClass().getSimpleName()), operator); }
    private Double toDouble(Object operand){ if(operand instanceof Double d) return d; if(operand instanceof Long l) return l.doubleValue(); throw ErrorFactory.typeError("Operand must be a number, got: "+(operand==null?"null": operand.getClass().getSimpleName()), (dhrlang.lexer.Token)null); }
    private String stringify(Object value){ if(value==null) return "null"; if(value instanceof String s) return s; return value.toString(); }

    private boolean isBuiltInStringMethod(String name){ return switch(name){ case "length","charAt","substring","indexOf","toUpperCase","toLowerCase","trim","startsWith","endsWith","equals","replace","split","repeat","contains" -> true; default -> false; }; }
    private NativeFunction createBuiltInStringMethod(String methodName, String value){
        return switch(methodName){
            case "length" -> new NativeFunction(){ public int arity(){return 0;} public Object call(Interpreter i, java.util.List<Object> a){ return (long)value.length(); } public String toString(){return "<native method length>";}};
            case "charAt" -> new NativeFunction(){ public int arity(){return 1;} public Object call(Interpreter i, java.util.List<Object> a){ if(!(a.get(0) instanceof Long)) throw ErrorFactory.typeError("charAt index must be a number", i.getCurrentCallLocation()); int idx=((Long)a.get(0)).intValue(); if(idx<0||idx>=value.length()) throw ErrorFactory.indexError("String index out of bounds", i.getCurrentCallLocation()); return String.valueOf(value.charAt(idx)); } public String toString(){return"<native method charAt>";}};
            case "substring" -> new NativeFunction(){ public int arity(){return 2;} public Object call(Interpreter i, java.util.List<Object> a){ if(!(a.get(0) instanceof Long) || !(a.get(1) instanceof Long)) throw ErrorFactory.typeError("substring indices must be numbers", i.getCurrentCallLocation()); int s=((Long)a.get(0)).intValue(); int e=((Long)a.get(1)).intValue(); if(s<0||e>value.length()||s>e) throw ErrorFactory.indexError("substring indices out of bounds", i.getCurrentCallLocation()); return value.substring(s,e);} public String toString(){return"<native method substring>";}};
            case "indexOf" -> new NativeFunction(){ public int arity(){return 1;} public Object call(Interpreter i, java.util.List<Object> a){ if(!(a.get(0) instanceof String)) throw ErrorFactory.typeError("indexOf requires a string argument", i.getCurrentCallLocation()); return (long)value.indexOf((String)a.get(0)); } public String toString(){return"<native method indexOf>";}};
            case "toUpperCase" -> new NativeFunction(){ public int arity(){return 0;} public Object call(Interpreter i, java.util.List<Object> a){ return value.toUpperCase(); } public String toString(){return"<native method toUpperCase>";}};
            case "toLowerCase" -> new NativeFunction(){ public int arity(){return 0;} public Object call(Interpreter i, java.util.List<Object> a){ return value.toLowerCase(); } public String toString(){return"<native method toLowerCase>";}};
            case "trim" -> new NativeFunction(){ public int arity(){return 0;} public Object call(Interpreter i, java.util.List<Object> a){ return value.trim(); } public String toString(){return"<native method trim>";}};
            case "startsWith" -> new NativeFunction(){ public int arity(){return 1;} public Object call(Interpreter i, java.util.List<Object> a){ if(!(a.get(0) instanceof String)) throw ErrorFactory.typeError("startsWith requires a string argument", i.getCurrentCallLocation()); return value.startsWith((String)a.get(0)); } public String toString(){return"<native method startsWith>";}};
            case "endsWith" -> new NativeFunction(){ public int arity(){return 1;} public Object call(Interpreter i, java.util.List<Object> a){ if(!(a.get(0) instanceof String)) throw ErrorFactory.typeError("endsWith requires a string argument", i.getCurrentCallLocation()); return value.endsWith((String)a.get(0)); } public String toString(){return"<native method endsWith>";}};
            case "equals" -> new NativeFunction(){ public int arity(){return 1;} public Object call(Interpreter i, java.util.List<Object> a){ if(!(a.get(0) instanceof String)) return false; return value.equals(a.get(0)); } public String toString(){return"<native method equals>";}};
            case "replace" -> new NativeFunction(){ public int arity(){return 2;} public Object call(Interpreter i, java.util.List<Object> a){ if(!(a.get(0) instanceof String) || !(a.get(1) instanceof String)) throw ErrorFactory.typeError("replace requires string arguments", i.getCurrentCallLocation()); return value.replace((String)a.get(0),(String)a.get(1)); } public String toString(){return"<native method replace>";}};
            case "contains" -> new NativeFunction(){ public int arity(){return 1;} public Object call(Interpreter i, java.util.List<Object> a){ if(!(a.get(0) instanceof String)) throw ErrorFactory.typeError("contains requires a string argument", i.getCurrentCallLocation()); return value.contains((String)a.get(0)); } public String toString(){return"<native method contains>";}};
            default -> throw ErrorFactory.systemError("Unknown built-in string method: "+methodName, interpreter.getCurrentCallLocation());
        };
    }
}
