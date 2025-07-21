package dhrlang.typechecker;

import dhrlang.ast.*;
import dhrlang.lexer.TokenType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TypeChecker {
    private final Map<String, ClassDecl> classRegistry = new HashMap<>();
    private final Map<String, TypeEnvironment> classEnvironments = new HashMap<>();
    private final TypeEnvironment globals = new TypeEnvironment();
    private ClassDecl currentClass = null;
    private String currentFunctionReturnType = null;
    private boolean inLoop = false;

    public void check(Program program) {
        for (ClassDecl classDecl : program.getClasses()) {
            if (classRegistry.containsKey(classDecl.getName())) {
                error("Class '" + classDecl.getName() + "' is already defined.");
            }
            classRegistry.put(classDecl.getName(), classDecl);
            globals.define(classDecl.getName(), classDecl.getName());
        }

        for (ClassDecl classDecl : program.getClasses()) {
            resolveClass(classDecl);
        }

        for (ClassDecl classDecl : program.getClasses()) {
            checkClassBody(classDecl);
        }
        
        if (!classRegistry.containsKey("exp")) {
            error("Entry point error: Class 'exp' not found.");
        }
        ClassDecl expClass = classRegistry.get("exp");
        FunctionDecl mainMethod = expClass.findMethod("main");
        if (mainMethod == null) {
            error("Entry point error: Method 'main' not found in class 'exp'.");
        }
        if (!mainMethod.getParameters().isEmpty()) {
            error("Entry point 'main' should not have parameters.");
        }
    }

    private TypeEnvironment resolveClass(ClassDecl klass) {
        if (classEnvironments.containsKey(klass.getName())) {
            return classEnvironments.get(klass.getName());
        }
        if (klass.isBeingResolved()) {
            error("Cyclic inheritance involving class " + klass.getName());
        }

        klass.setBeingResolved(true);

        TypeEnvironment parentEnv = globals;
        if (klass.getSuperclass() != null) {
            String superclassName = klass.getSuperclass().getName().getLexeme();
            if (!classRegistry.containsKey(superclassName)) {
                error("Undefined superclass '" + superclassName + "'.");
            }
            parentEnv = resolveClass(classRegistry.get(superclassName));
        }

        TypeEnvironment classEnv = new TypeEnvironment(parentEnv);
        
        for (VarDecl field : klass.getVariables()) {
            if (classEnv.getLocalFields().containsKey(field.getName())) {
                error("Field '" + field.getName() + "' is already defined in class '" + klass.getName() + "'.");
            }
            classEnv.define(field.getName(), field.getType());
        }
        
        for (FunctionDecl func : klass.getFunctions()) {
            List<String> paramTypes = func.getParameters().stream()
                    .map(VarDecl::getType)
                    .collect(Collectors.toList());
            
            String methodName = func.getName();
            if (classEnv.getLocalFunctions().containsKey(methodName)) {
                error("Method '" + methodName + "' is already defined in class '" + klass.getName() + "'.");
            }
            
            classEnv.defineFunction(methodName, new FunctionSignature(paramTypes, func.getReturnType()));
        }

        classEnvironments.put(klass.getName(), classEnv);
        klass.setBeingResolved(false);
        return classEnv;
    }

    private void checkClassBody(ClassDecl klass) {
        this.currentClass = klass;
        TypeEnvironment classEnv = classEnvironments.get(klass.getName());
        
        for (VarDecl field : klass.getVariables()) {
            validateModifiers(field);
            
            if (field.getInitializer() != null) {
                String initType = checkExpr(field.getInitializer(), classEnv);
                if (!isAssignable(initType, field.getType())) {
                    error("Type mismatch in field '" + field.getName() + "': Cannot assign type '" + 
                          initType + "' to field of type '" + field.getType() + "'.");
                }
            }
        }
        
        for (FunctionDecl function : klass.getFunctions()) {
            // Validate modifiers
            validateModifiers(function);
            checkFunction(function, classEnv);
        }
        
        this.currentClass = null;
    }

    private void checkFunction(FunctionDecl function, TypeEnvironment env) {
        TypeEnvironment local = new TypeEnvironment(env);
        
        if (currentClass != null) {
            local.define("this", currentClass.getName());
        }
        
        for (VarDecl param : function.getParameters()) {
            if (local.getLocalFields().containsKey(param.getName())) {
                error("Parameter '" + param.getName() + "' is already defined in function '" + function.getName() + "'.");
            }
            local.define(param.getName(), param.getType());
        }

        String previousReturnType = currentFunctionReturnType;
        currentFunctionReturnType = function.getReturnType();

        checkBlock(function.getBody(), local);

        currentFunctionReturnType = previousReturnType;
    }

    private void checkBlock(Block block, TypeEnvironment env) {
        TypeEnvironment blockEnv = new TypeEnvironment(env);
        for (Statement stmt : block.getStatements()) {
            checkStatement(stmt, blockEnv);
        }
    }

    private void checkStatement(Statement stmt, TypeEnvironment env) {
        if (stmt instanceof ExpressionStmt) {
            checkExpr(((ExpressionStmt) stmt).getExpression(), env);
        } else if (stmt instanceof VarDecl) {
            checkVarDecl((VarDecl) stmt, env);
        } else if (stmt instanceof Block) {
            checkBlock((Block) stmt, env);
        } else if (stmt instanceof IfStmt) {
            checkIfStmt((IfStmt) stmt, env);
        } else if (stmt instanceof WhileStmt) {
            checkWhileStmt((WhileStmt) stmt, env);
        } else if (stmt instanceof TryStmt) {
            checkTryStmt((TryStmt) stmt, env);
        } else if (stmt instanceof ThrowStmt) {
            checkThrowStmt((ThrowStmt) stmt, env);
        } else if (stmt instanceof ReturnStmt) {
            checkReturnStmt((ReturnStmt) stmt, env);
        } else if (stmt instanceof BreakStmt) {
            checkBreakStmt();
        } else if (stmt instanceof ContinueStmt) {
            checkContinueStmt();
        } else if (stmt instanceof PrintStmt) {
            checkExpr(((PrintStmt) stmt).getExpression(), env);
        } else if (stmt instanceof FunctionDecl) {
            checkFunction((FunctionDecl) stmt, env);
        } else {
            error("Unsupported statement type: " + stmt.getClass().getSimpleName());
        }
    }

    private void checkVarDecl(VarDecl stmt, TypeEnvironment env) {
        if (env.getLocalFields().containsKey(stmt.getName())) {
            error("Variable '" + stmt.getName() + "' is already defined in this scope.");
        }

        if (stmt.getInitializer() != null) {
            String initType = checkExpr(stmt.getInitializer(), env);
            if (!isAssignable(initType, stmt.getType())) {
                error("Type mismatch: Cannot assign type '" + initType + 
                      "' to variable '" + stmt.getName() + "' of type '" + stmt.getType() + "'.");
            }
        }
        
        env.define(stmt.getName(), stmt.getType());
    }

    private void checkIfStmt(IfStmt stmt, TypeEnvironment env) {
        String conditionType = checkExpr(stmt.getCondition(), env);
        if (!conditionType.equals("kya")) {
            error("If condition must be a boolean ('kya'), got '" + conditionType + "'.");
        }
        checkStatement(stmt.getThenBranch(), env);
        if (stmt.getElseBranch() != null) {
            checkStatement(stmt.getElseBranch(), env);
        }
    }

    private void checkWhileStmt(WhileStmt stmt, TypeEnvironment env) {
        String conditionType = checkExpr(stmt.getCondition(), env);
        if (!conditionType.equals("kya")) {
            error("While condition must be a boolean ('kya'), got '" + conditionType + "'.");
        }
        
        boolean wasInLoop = inLoop;
        inLoop = true;
        checkStatement(stmt.getBody(), env);
        inLoop = wasInLoop;
    }

    private void checkBreakStmt() {
        if (!inLoop) {
            error("'break' can only be used inside a loop.");
        }
    }

    private void checkContinueStmt() {
        if (!inLoop) {
            error("'continue' can only be used inside a loop.");
        }
    }

    private void checkReturnStmt(ReturnStmt stmt, TypeEnvironment env) {
        if (currentFunctionReturnType == null) {
            error("'return' used outside a function.");
        }
        
        if (stmt.getValue() == null) {
            if (!currentFunctionReturnType.equals("kaam")) {
                error("Function with return type '" + currentFunctionReturnType + "' must return a value.");
            }
        } else {
            String returnType = checkExpr(stmt.getValue(), env);
            if (!isAssignable(returnType, currentFunctionReturnType)) {
                error("Cannot return '" + returnType + "' from a function expecting '" + currentFunctionReturnType + "'.");
            }
        }
    }

    private void checkTryStmt(TryStmt stmt, TypeEnvironment env) {
        checkStatement(stmt.getTryBlock(), env);
        
        for (CatchClause catchClause : stmt.getCatchClauses()) {
            TypeEnvironment catchEnv = new TypeEnvironment(env);
            catchEnv.define(catchClause.getParameter(), "sab");
            checkStatement(catchClause.getBody(), catchEnv);
        }
        
        if (stmt.getFinallyBlock() != null) {
            checkStatement(stmt.getFinallyBlock(), env);
        }
    }
    
    private void checkThrowStmt(ThrowStmt stmt, TypeEnvironment env) {
        checkExpr(stmt.getValue(), env);
    }

    private String checkExpr(Expression expr, TypeEnvironment env) {
        if (expr instanceof LiteralExpr) return checkLiteral((LiteralExpr) expr);
        if (expr instanceof VariableExpr) return checkVariable((VariableExpr) expr, env);
        if (expr instanceof UnaryExpr) return checkUnary((UnaryExpr) expr, env);
        if (expr instanceof BinaryExpr) return checkBinary((BinaryExpr) expr, env);
        if (expr instanceof AssignmentExpr) return checkAssign((AssignmentExpr) expr, env);
        if (expr instanceof NewExpr) return checkNew((NewExpr) expr, env);
        if (expr instanceof NewArrayExpr) return checkNewArray((NewArrayExpr) expr, env);
        if (expr instanceof GetExpr) return checkGet((GetExpr) expr, env);
        if (expr instanceof SetExpr) return checkSet((SetExpr) expr, env);
        if (expr instanceof ThisExpr) return checkThis();
        if (expr instanceof SuperExpr) return checkSuper((SuperExpr) expr, env);
        if (expr instanceof CallExpr) return checkCall((CallExpr) expr, env);
        if (expr instanceof ArrayExpr) return checkArray((ArrayExpr) expr, env);
        if (expr instanceof IndexExpr) return checkIndex((IndexExpr) expr, env);
        if (expr instanceof IndexAssignExpr) return checkIndexAssign((IndexAssignExpr) expr, env);
        if (expr instanceof PostfixIncrementExpr) return checkPostfixIncrement((PostfixIncrementExpr) expr, env);
        if (expr instanceof PrefixIncrementExpr) return checkPrefixIncrement((PrefixIncrementExpr) expr, env);
        if (expr instanceof StaticAccessExpr) return checkStaticAccess((StaticAccessExpr) expr, env);
        if (expr instanceof StaticAssignExpr) return checkStaticAssign((StaticAssignExpr) expr, env);

        throw new TypeException("Unsupported expression type: " + expr.getClass().getSimpleName());
    }

    private String checkArray(ArrayExpr expr, TypeEnvironment env) {
        if (expr.getElements().isEmpty()) {
            return "unknown[]"; 
        }

        String elementType = checkExpr(expr.getElements().get(0), env);

        for (int i = 1; i < expr.getElements().size(); i++) {
            String currentType = checkExpr(expr.getElements().get(i), env);
            if (!isAssignable(currentType, elementType)) {
                error("Array elements must all have the same type. Expected '" + elementType +
                        "' but found '" + currentType + "' at index " + i + ".");
            }
        }

        return elementType + "[]";
    }

    private String checkIndex(IndexExpr expr, TypeEnvironment env) {
        String objectType = checkExpr(expr.getObject(), env);
        String indexType = checkExpr(expr.getIndex(), env);

        if (!objectType.endsWith("[]")) {
            error("Can only index arrays, got type '" + objectType + "'.");
        }

        if (!indexType.equals("num")) {
            error("Array index must be a number, got '" + indexType + "'.");
        }

        return objectType.substring(0, objectType.length() - 2);
    }

    private String checkIndexAssign(IndexAssignExpr expr, TypeEnvironment env) {
        String objectType = checkExpr(expr.getObject(), env);
        String indexType = checkExpr(expr.getIndex(), env);
        String valueType = checkExpr(expr.getValue(), env);

        if (!objectType.endsWith("[]")) {
            error("Can only assign to array elements, got type '" + objectType + "'.");
        }

        if (!indexType.equals("num")) {
            error("Array index must be a number, got '" + indexType + "'.");
        }

        String elementType = objectType.substring(0, objectType.length() - 2);
        if (!isAssignable(valueType, elementType)) {
            error("Cannot assign '" + valueType + "' to array of '" + elementType + "'.");
        }

        return valueType;
    }

    private String checkPostfixIncrement(PostfixIncrementExpr expr, TypeEnvironment env) {
        return checkIncrementTarget(expr.getTarget(), env, "postfix increment/decrement");
    }

    private String checkPrefixIncrement(PrefixIncrementExpr expr, TypeEnvironment env) {
        return checkIncrementTarget(expr.getTarget(), env, "prefix increment/decrement");
    }

    private String checkIncrementTarget(Expression target, TypeEnvironment env, String operation) {
        String targetType;
        
        if (target instanceof VariableExpr varExpr) {
            targetType = checkVariable(varExpr, env);
        } else if (target instanceof GetExpr getExpr) {
            targetType = checkGet(getExpr, env);
        } else if (target instanceof IndexExpr indexExpr) {
            targetType = checkIndex(indexExpr, env);
        } else {
            error("Invalid " + operation + " target. Must be a variable, property, or array element.");
            return null;
        }
        
        if (!isNumeric(targetType)) {
            error("Can only apply " + operation + " to numeric values, got '" + targetType + "'.");
        }
        
        return targetType;
    }

    private String checkLiteral(LiteralExpr expr) {
        if (expr.getValue() instanceof Long) return "num";
        if (expr.getValue() instanceof Double) return "duo";
        if (expr.getValue() instanceof Boolean) return "kya";
        if (expr.getValue() instanceof Character) return "ek";
        if (expr.getValue() instanceof String) return "sab";
        return "unknown";
    }

    private String checkVariable(VariableExpr expr, TypeEnvironment env) {
        try {
            return env.get(expr.getName().getLexeme());
        } catch (TypeException e) {
            throw new TypeException("Undefined variable '" + expr.getName().getLexeme() + "'.");
        }
    }

    private String checkUnary(UnaryExpr expr, TypeEnvironment env) {
        String rightType = checkExpr(expr.getRight(), env);
        TokenType op = expr.getOperator().getType();
        
        if (op == TokenType.MINUS) {
            if (!isNumeric(rightType)) {
                error("Operand for '-' must be a number, got '" + rightType + "'.");
            }
            return rightType;
        } else if (op == TokenType.NOT) {
            if (!rightType.equals("kya")) {
                error("Operand for '!' must be a boolean, got '" + rightType + "'.");
            }
            return "kya";
        }
        
        error("Unsupported unary operator: " + op);
        return null;
    }

    private String checkBinary(BinaryExpr expr, TypeEnvironment env) {
        String leftType = checkExpr(expr.getLeft(), env);
        String rightType = checkExpr(expr.getRight(), env);
        TokenType op = expr.getOperator().getType();
        
        switch (op) {
            case PLUS:
                if (leftType.equals("sab") || rightType.equals("sab")) {
                    return "sab"; 
                }
            case MINUS:
            case STAR:
            case MOD:
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    String opName = op == TokenType.PLUS ? "addition/concatenation" : "arithmetic";
                    error("Operands for " + opName + " must be numbers (or strings for '+'), got '" + 
                          leftType + "' and '" + rightType + "'.");
                }
                return (leftType.equals("duo") || rightType.equals("duo")) ? "duo" : "num";
                
            case SLASH:
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    error("Operands for division must be numbers, got '" + leftType + "' and '" + rightType + "'.");
                }
                return "duo"; // Division always returns double
                
            case GREATER:
            case GEQ:
            case LESS:
            case LEQ:
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    error("Operands for comparison must be numbers, got '" + leftType + "' and '" + rightType + "'.");
                }
                return "kya";
                
            case EQUALITY:
            case NEQ:
                if (!isAssignable(leftType, rightType) && !isAssignable(rightType, leftType)) {
                    error("Cannot compare incompatible types: '" + leftType + "' and '" + rightType + "'.");
                }
                return "kya";
                
            case AND:
            case OR:
                if (!leftType.equals("kya")) {
                    error("Left operand of logical operator must be boolean, got '" + leftType + "'.");
                }
                if (!rightType.equals("kya")) {
                    error("Right operand of logical operator must be boolean, got '" + rightType + "'.");
                }
                return "kya";
                
            default:
                error("Unsupported binary operator: " + op);
                return null;
        }
    }

    private String checkAssign(AssignmentExpr expr, TypeEnvironment env) {
        String varName = expr.getName().getLexeme();
        String varType;
        try {
            varType = env.get(varName);
        } catch (TypeException e) {
            throw new TypeException("Cannot assign to undefined variable '" + varName + "'.");
        }
        
        String valType = checkExpr(expr.getValue(), env);
        if (!isAssignable(valType, varType)) {
            error("Cannot assign type '" + valType + "' to variable '" + varName + "' of type '" + varType + "'.");
        }
        return valType;
    }

    private String checkNew(NewExpr expr, TypeEnvironment env) {
        String className = expr.getClassName();
        if (!classRegistry.containsKey(className)) {
            error("Cannot instantiate unknown class '" + className + "'.");
        }
        
        ClassDecl classDecl = classRegistry.get(className);
        FunctionDecl init = classDecl.findMethod("init");
        
        if (init != null) {
            checkFunctionArguments("init", init.getParameters(), expr.getArguments(), env);
        } else if (!expr.getArguments().isEmpty()) {
            error("Class '" + className + "' has no 'init' constructor and cannot be called with arguments.");
        }
        
        return className;
    }

    private String checkNewArray(NewArrayExpr expr, TypeEnvironment env) {
        String elementType = expr.getElementType();
        
        if (!elementType.equals("num") && !elementType.equals("duo") && 
            !elementType.equals("ek") && !elementType.equals("sab") && 
            !elementType.equals("kya") && !classRegistry.containsKey(elementType)) {
            error("Unknown array element type '" + elementType + "'.");
        }
        
        String sizeType = checkExpr(expr.getSize(), env);
        if (!isNumeric(sizeType)) {
            error("Array size must be numeric, got '" + sizeType + "'.");
        }
        
        return elementType + "[]";
    }

    private String checkGet(GetExpr expr, TypeEnvironment env) {
        String objectType = checkExpr(expr.getObject(), env);
        String propName = expr.getName().getLexeme();
        if (objectType.endsWith("[]") && propName.equals("length")) {
            return "num";
        }
        if (objectType.equals("sab") && propName.equals("length")) {
            return "num";
        }

        TypeEnvironment instanceEnv = classEnvironments.get(objectType);
        if (instanceEnv == null) {
            error("Can only access properties on class instances, got type '" + objectType + "'.");
        }
        
        try {
            return instanceEnv.get(propName);
        } catch (TypeException fieldError) {
            try {
                instanceEnv.getFunction(propName);
                return "method"; // Method placeholder type
            } catch (TypeException funcError) {
                error("Property '" + propName + "' not found on class '" + objectType + "'.");
                return null;
            }
        }
    }

    private String checkSet(SetExpr expr, TypeEnvironment env) {
        String objectType = checkExpr(expr.getObject(), env);
        TypeEnvironment instanceEnv = classEnvironments.get(objectType);
        if (instanceEnv == null) {
            error("Can only set properties on class instances, got type '" + objectType + "'.");
        }
        
        String fieldName = expr.getName().getLexeme();
        String fieldType;
        try {
            fieldType = instanceEnv.get(fieldName);
        } catch (TypeException e) {
            error("Field '" + fieldName + "' not found on class '" + objectType + "'.");
            return null;
        }
        
        String valueType = checkExpr(expr.getValue(), env);
        if (!isAssignable(valueType, fieldType)) {
            error("Cannot assign type '" + valueType + "' to field '" + fieldName + "' of type '" + fieldType + "'.");
        }
        return valueType;
    }

    private String checkThis() {
        if (currentClass == null) {
            error("Cannot use 'this' outside of a class.");
        }
        return currentClass.getName();
    }

    private String checkSuper(SuperExpr expr, TypeEnvironment env) {
        if (currentClass == null || currentClass.getSuperclass() == null) {
            error("Cannot use 'super' outside of a class with a superclass.");
        }
        
        String methodName = expr.method.getLexeme();
        String superclassName = currentClass.getSuperclass().getName().getLexeme();
        TypeEnvironment superEnv = classEnvironments.get(superclassName);
        
        try {
            superEnv.getFunction(methodName);
        } catch (TypeException e) {
            error("Method '" + methodName + "' not found in superclass '" + superclassName + "'.");
        }
        
        return "method";
    }
    
    private String checkStaticAccess(StaticAccessExpr expr, TypeEnvironment env) {
        String className = expr.className.getName().getLexeme();
        String memberName = expr.memberName.getLexeme();
        
        if (!classRegistry.containsKey(className)) {
            error("Unknown class '" + className + "' in static access.");
        }
        
        ClassDecl classDecl = classRegistry.get(className);
        
        for (VarDecl field : classDecl.getVariables()) {
            if (field.getName().equals(memberName) && field.hasModifier(Modifier.STATIC)) {
                if (!isAccessible(currentClass, classDecl, field.getModifiers())) {
                    error("Cannot access private/protected static field '" + memberName + "' from class '" + className + "'.");
                }
                return field.getType();
            }
        }
        
        for (FunctionDecl method : classDecl.getFunctions()) {
            if (method.getName().equals(memberName) && method.hasModifier(Modifier.STATIC)) {
                if (!isAccessible(currentClass, classDecl, method.getModifiers())) {
                    error("Cannot access private/protected static method '" + memberName + "' from class '" + className + "'.");
                }
                return "method";
            }
        }
        
        error("Static member '" + memberName + "' not found in class '" + className + "'.");
        return null;
    }
    
    private String checkStaticAssign(StaticAssignExpr expr, TypeEnvironment env) {
        String className = expr.className.getName().getLexeme();
        String memberName = expr.memberName.getLexeme();
        
        if (!classRegistry.containsKey(className)) {
            error("Unknown class '" + className + "' in static assignment.");
        }
        
        ClassDecl classDecl = classRegistry.get(className);
        
        for (VarDecl field : classDecl.getVariables()) {
            if (field.getName().equals(memberName) && field.hasModifier(Modifier.STATIC)) {
                if (!isAccessible(currentClass, classDecl, field.getModifiers())) {
                    error("Cannot access private/protected static field '" + memberName + "' from class '" + className + "'.");
                }
                
                String valueType = checkExpr(expr.value, env);
                if (!isAssignable(valueType, field.getType())) {
                    error("Cannot assign '" + valueType + "' to static field '" + memberName + "' of type '" + field.getType() + "'.");
                }
                
                return valueType;
            }
        }
        
        error("Static field '" + memberName + "' not found in class '" + className + "'.");
        return null;
    }

    private String checkCall(CallExpr call, TypeEnvironment env) {
        Expression callee = call.getCallee();
        FunctionSignature signature;
        String funcName;
        
        if (callee instanceof VariableExpr) {
            funcName = ((VariableExpr) callee).getName().getLexeme();
            if (isNativeFunction(funcName)) {
                return checkNativeFunction(funcName, call.getArguments(), env);
            }
            try {
                signature = env.getFunction(funcName);
            } catch (TypeException e) {
                error("Undefined function '" + funcName + "'.");
                return null;
            }
        } else if (callee instanceof GetExpr) {
            String objectType = checkExpr(((GetExpr) callee).getObject(), env);
            TypeEnvironment instanceEnv = classEnvironments.get(objectType);
            if (instanceEnv == null) {
                error("Can only call methods on class instances, got type '" + objectType + "'.");
            }
            funcName = ((GetExpr) callee).getName().getLexeme();
            try {
                signature = instanceEnv.getFunction(funcName);
            } catch (TypeException e) {
                error("Method '" + funcName + "' not found on class '" + objectType + "'.");
                return null;
            }
        } else if (callee instanceof SuperExpr) {
            if (currentClass == null || currentClass.getSuperclass() == null) {
                error("'super' used incorrectly.");
            }
            String superclassName = currentClass.getSuperclass().getName().getLexeme();
            funcName = ((SuperExpr) callee).method.getLexeme();
            try {
                signature = classEnvironments.get(superclassName).getFunction(funcName);
            } catch (TypeException e) {
                error("Method '" + funcName + "' not found in superclass '" + superclassName + "'.");
                return null;
            }
        } else if (callee instanceof StaticAccessExpr) {
            StaticAccessExpr staticAccess = (StaticAccessExpr) callee;
            String className = staticAccess.className.getName().getLexeme();
            funcName = staticAccess.memberName.getLexeme();
            
            TypeEnvironment classEnv = classEnvironments.get(className);
            if (classEnv == null) {
                error("Class '" + className + "' not found.");
                return null;
            }
            
            ClassDecl classDecl = classRegistry.get(className);
            if (classDecl == null) {
                error("Class '" + className + "' not found.");
                return null;
            }
            
            FunctionDecl methodDecl = classDecl.findMethod(funcName);
            if (methodDecl == null) {
                error("Static method '" + funcName + "' not found in class '" + className + "'.");
                return null;
            }
            
            if (!methodDecl.hasModifier(Modifier.STATIC)) {
                error("Cannot call non-static method '" + funcName + "' from static context.");
                return null;
            }
            
            try {
                signature = classEnv.getFunction(funcName);
            } catch (TypeException e) {
                error("Static method '" + funcName + "' not found in class '" + className + "'.");
                return null;
            }
        } else {
            error("Expression is not callable.");
            return null;
        }
        
        checkFunctionArguments(funcName, signature.getParameterTypes(), call.getArguments(), env);
        return signature.getReturnType();
    }

    private void checkFunctionArguments(String name, List<?> expectedParams, List<Expression> args, TypeEnvironment env) {
        if (args.size() != expectedParams.size()) {
            error("Function '" + name + "' expects " + expectedParams.size() + 
                  " arguments, but got " + args.size() + ".");
        }
        
        boolean typesAsStrings = !expectedParams.isEmpty() && expectedParams.get(0) instanceof String;
        
        for (int i = 0; i < args.size(); i++) {
            String argType = checkExpr(args.get(i), env);
            String expectedType = typesAsStrings ? 
                (String) expectedParams.get(i) : 
                ((VarDecl) expectedParams.get(i)).getType();
            
            if (!isAssignable(argType, expectedType)) {
                error("Argument " + (i + 1) + " for '" + name + "' should be '" + expectedType + 
                      "', but got '" + argType + "'.");
            }
        }
    }

    private boolean isNativeFunction(String name) {
        return name.equals("print") || name.equals("printLine") || name.equals("clock") ||
               name.equals("abs") || name.equals("sqrt") || name.equals("pow") ||
               name.equals("min") || name.equals("max") || name.equals("floor") ||
               name.equals("ceil") || name.equals("round") || name.equals("random") ||
               name.equals("length") || name.equals("substring") || name.equals("charAt") ||
               name.equals("toUpperCase") || name.equals("toLowerCase") || name.equals("indexOf") ||
               name.equals("replace") || name.equals("startsWith") || name.equals("endsWith") ||
               name.equals("trim") ||
               name.equals("readLine") || name.equals("readLineWithPrompt") ||
               name.equals("toNum") || name.equals("toDuo") || name.equals("toString") ||
               name.equals("arrayLength") || name.equals("arrayContains") ||
               name.equals("arrayIndexOf") || name.equals("arrayCopy") ||
               name.equals("split") || name.equals("join") || name.equals("repeat") ||
               name.equals("reverse") || name.equals("padLeft") || name.equals("padRight") ||
               name.equals("arrayReverse") || name.equals("arraySlice") || name.equals("arraySort") ||
               name.equals("arrayConcat") || name.equals("arrayFill") || name.equals("arraySum") ||
               name.equals("arrayAverage") || name.equals("arrayPush") || name.equals("arrayPop") ||
               name.equals("arrayInsert") ||
               name.equals("sin") || name.equals("cos") || name.equals("tan") ||
               name.equals("log") || name.equals("log10") || name.equals("exp") ||
               name.equals("randomRange") || name.equals("clamp") ||
               name.equals("isNum") || name.equals("isDuo") || name.equals("isSab") ||
               name.equals("isKya") || name.equals("isArray") || name.equals("typeOf") ||
               name.equals("range") || name.equals("sleep");
    }

    private String checkNativeFunction(String name, List<Expression> args, TypeEnvironment env) {
        switch (name) {
            case "print":
            case "printLine":
                if (args.size() != 1) {
                    error("'" + name + "' expects exactly 1 argument, got " + args.size() + ".");
                }
                checkExpr(args.get(0), env);
                return "kaam";
                
            case "clock":
                if (!args.isEmpty()) {
                    error("'clock' expects 0 arguments, got " + args.size() + ".");
                }
                return "duo";
                
            case "abs":
                if (args.size() != 1) {
                    error("'abs' expects exactly 1 argument, got " + args.size() + ".");
                }
                String absType = checkExpr(args.get(0), env);
                if (!isNumeric(absType)) {
                    error("'abs' requires a numeric argument, got '" + absType + "'.");
                }
                return absType;
                
            case "sqrt":
            case "floor":
            case "ceil":
                if (args.size() != 1) {
                    error("'" + name + "' expects exactly 1 argument, got " + args.size() + ".");
                }
                String argType = checkExpr(args.get(0), env);
                if (!isNumeric(argType)) {
                    error("'" + name + "' requires a numeric argument, got '" + argType + "'.");
                }
                return name.equals("sqrt") ? "duo" : "num";
                
            case "round":
                if (args.size() != 1) {
                    error("'round' expects exactly 1 argument, got " + args.size() + ".");
                }
                String roundType = checkExpr(args.get(0), env);
                if (!isNumeric(roundType)) {
                    error("'round' requires a numeric argument, got '" + roundType + "'.");
                }
                return "num";
                
            case "pow":
            case "min":
            case "max":
                if (args.size() != 2) {
                    error("'" + name + "' expects exactly 2 arguments, got " + args.size() + ".");
                }
                String leftType = checkExpr(args.get(0), env);
                String rightType = checkExpr(args.get(1), env);
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    error("'" + name + "' requires numeric arguments, got '" + leftType + "' and '" + rightType + "'.");
                }
                return name.equals("pow") ? "duo" : (leftType.equals("duo") || rightType.equals("duo") ? "duo" : "num");
                
            case "random":
                if (!args.isEmpty()) {
                    error("'random' expects 0 arguments, got " + args.size() + ".");
                }
                return "duo";
                
            // String functions
            case "length":
                if (args.size() != 1) {
                    error("'length' expects exactly 1 argument, got " + args.size() + ".");
                }
                String strType = checkExpr(args.get(0), env);
                if (!strType.equals("sab")) {
                    error("'length' requires a string argument, got '" + strType + "'.");
                }
                return "num";
                
            case "substring":
                if (args.size() != 3) {
                    error("'substring' expects exactly 3 arguments, got " + args.size() + ".");
                }
                String subStrType = checkExpr(args.get(0), env);
                String startType = checkExpr(args.get(1), env);
                String endType = checkExpr(args.get(2), env);
                if (!subStrType.equals("sab")) {
                    error("'substring' first argument must be a string, got '" + subStrType + "'.");
                }
                if (!isNumeric(startType) || !isNumeric(endType)) {
                    error("'substring' indices must be numeric, got '" + startType + "' and '" + endType + "'.");
                }
                return "sab";
                
            case "charAt":
                if (args.size() != 2) {
                    error("'charAt' expects exactly 2 arguments, got " + args.size() + ".");
                }
                String charStrType = checkExpr(args.get(0), env);
                String indexType = checkExpr(args.get(1), env);
                if (!charStrType.equals("sab")) {
                    error("'charAt' first argument must be a string, got '" + charStrType + "'.");
                }
                if (!isNumeric(indexType)) {
                    error("'charAt' index must be numeric, got '" + indexType + "'.");
                }
                return "sab";
                
            case "toUpperCase":
            case "toLowerCase":
            case "trim":
                if (args.size() != 1) {
                    error("'" + name + "' expects exactly 1 argument, got " + args.size() + ".");
                }
                String caseType = checkExpr(args.get(0), env);
                if (!caseType.equals("sab")) {
                    error("'" + name + "' requires a string argument, got '" + caseType + "'.");
                }
                return "sab";
                
            case "indexOf":
            case "startsWith":
            case "endsWith":
                if (args.size() != 2) {
                    error("'" + name + "' expects exactly 2 arguments, got " + args.size() + ".");
                }
                String baseType = checkExpr(args.get(0), env);
                String searchType = checkExpr(args.get(1), env);
                if (!baseType.equals("sab") || !searchType.equals("sab")) {
                    error("'" + name + "' requires string arguments, got '" + baseType + "' and '" + searchType + "'.");
                }
                return name.equals("indexOf") ? "num" : "kya";
                
            case "replace":
                if (args.size() != 3) {
                    error("'replace' expects exactly 3 arguments, got " + args.size() + ".");
                }
                String replaceStrType = checkExpr(args.get(0), env);
                String targetType = checkExpr(args.get(1), env);
                String replacementType = checkExpr(args.get(2), env);
                if (!replaceStrType.equals("sab") || !targetType.equals("sab") || !replacementType.equals("sab")) {
                    error("'replace' requires string arguments.");
                }
                return "sab";
                
            case "readLine":
                if (!args.isEmpty()) {
                    error("'readLine' expects 0 arguments, got " + args.size() + ".");
                }
                return "sab";
                
            case "readLineWithPrompt":
                if (args.size() != 1) {
                    error("'readLineWithPrompt' expects exactly 1 argument, got " + args.size() + ".");
                }
                String promptType = checkExpr(args.get(0), env);
                if (!promptType.equals("sab")) {
                    error("'readLineWithPrompt' requires a string prompt, got '" + promptType + "'.");
                }
                return "sab";
                
            case "toNum":
            case "toDuo":
                if (args.size() != 1) {
                    error("'" + name + "' expects exactly 1 argument, got " + args.size() + ".");
                }
                String parseType = checkExpr(args.get(0), env);
                if (!parseType.equals("sab")) {
                    error("'" + name + "' requires a string argument, got '" + parseType + "'.");
                }
                return name.equals("toNum") ? "num" : "duo";
                
            case "toString":
                if (args.size() != 1) {
                    error("'toString' expects exactly 1 argument, got " + args.size() + ".");
                }
                checkExpr(args.get(0), env); // Any type is acceptable
                return "sab";

            // Advanced String Functions
            case "split":
                if (args.size() != 2) {
                    error("'split' expects exactly 2 arguments, got " + args.size() + ".");
                }
                String splitStrType = checkExpr(args.get(0), env);
                String delimiterType = checkExpr(args.get(1), env);
                if (!splitStrType.equals("sab") || !delimiterType.equals("sab")) {
                    error("'split' requires string arguments, got '" + splitStrType + "' and '" + delimiterType + "'.");
                }
                return "sab[]";
                
            case "join":
                if (args.size() != 2) {
                    error("'join' expects exactly 2 arguments, got " + args.size() + ".");
                }
                String joinArrType = checkExpr(args.get(0), env);
                String joinDelimType = checkExpr(args.get(1), env);
                if (!joinArrType.equals("sab[]") || !joinDelimType.equals("sab")) {
                    error("'join' requires string array and string delimiter, got '" + joinArrType + "' and '" + joinDelimType + "'.");
                }
                return "sab";
                
            case "repeat":
                if (args.size() != 2) {
                    error("'repeat' expects exactly 2 arguments, got " + args.size() + ".");
                }
                String repeatStrType = checkExpr(args.get(0), env);
                String repeatCountType = checkExpr(args.get(1), env);
                if (!repeatStrType.equals("sab") || !isNumeric(repeatCountType)) {
                    error("'repeat' requires string and numeric arguments, got '" + repeatStrType + "' and '" + repeatCountType + "'.");
                }
                return "sab";
                
            case "reverse":
                if (args.size() != 1) {
                    error("'reverse' expects exactly 1 argument, got " + args.size() + ".");
                }
                String reverseType = checkExpr(args.get(0), env);
                if (!reverseType.equals("sab")) {
                    error("'reverse' requires a string argument, got '" + reverseType + "'.");
                }
                return "sab";
                
            case "padLeft":
            case "padRight":
                if (args.size() != 3) {
                    error("'" + name + "' expects exactly 3 arguments, got " + args.size() + ".");
                }
                String padStrType = checkExpr(args.get(0), env);
                String padLengthType = checkExpr(args.get(1), env);
                String padCharType = checkExpr(args.get(2), env);
                if (!padStrType.equals("sab") || !isNumeric(padLengthType) || !padCharType.equals("sab")) {
                    error("'" + name + "' requires string, numeric, and string arguments.");
                }
                return "sab";

            // Advanced Math Functions
            case "sin":
            case "cos":
            case "tan":
            case "log":
            case "log10":
            case "exp":
                if (args.size() != 1) {
                    error("'" + name + "' expects exactly 1 argument, got " + args.size() + ".");
                }
                String mathArgType = checkExpr(args.get(0), env);
                if (!isNumeric(mathArgType)) {
                    error("'" + name + "' requires a numeric argument, got '" + mathArgType + "'.");
                }
                return "duo";
                
            case "randomRange":
                if (args.size() != 2) {
                    error("'randomRange' expects exactly 2 arguments, got " + args.size() + ".");
                }
                String minType = checkExpr(args.get(0), env);
                String maxType = checkExpr(args.get(1), env);
                if (!isNumeric(minType) || !isNumeric(maxType)) {
                    error("'randomRange' requires numeric arguments, got '" + minType + "' and '" + maxType + "'.");
                }
                return "num";
                
            case "clamp":
                if (args.size() != 3) {
                    error("'clamp' expects exactly 3 arguments, got " + args.size() + ".");
                }
                String clampValueType = checkExpr(args.get(0), env);
                String clampMinType = checkExpr(args.get(1), env);
                String clampMaxType = checkExpr(args.get(2), env);
                if (!isNumeric(clampValueType) || !isNumeric(clampMinType) || !isNumeric(clampMaxType)) {
                    error("'clamp' requires numeric arguments.");
                }
                return clampValueType.equals("duo") || clampMinType.equals("duo") || clampMaxType.equals("duo") ? "duo" : "num";
                
            case "arrayLength":
                if (args.size() != 1) {
                    error("'arrayLength' expects exactly 1 argument, got " + args.size() + ".");
                }
                String arrType = checkExpr(args.get(0), env);
                if (!arrType.endsWith("[]")) {
                    error("'arrayLength' requires an array argument, got '" + arrType + "'.");
                }
                return "num";
                
            case "arrayContains":
            case "arrayIndexOf":
                if (args.size() != 2) {
                    error("'" + name + "' expects exactly 2 arguments, got " + args.size() + ".");
                }
                String containsArrType = checkExpr(args.get(0), env);
                checkExpr(args.get(1), env); 
                if (!containsArrType.endsWith("[]")) {
                    error("'" + name + "' first argument must be an array, got '" + containsArrType + "'.");
                }
                return name.equals("arrayContains") ? "kya" : "num";
                
            case "arrayCopy":
                if (args.size() != 1) {
                    error("'arrayCopy' expects exactly 1 argument, got " + args.size() + ".");
                }
                String copyType = checkExpr(args.get(0), env);
                if (!copyType.endsWith("[]")) {
                    error("'arrayCopy' requires an array argument, got '" + copyType + "'.");
                }
                return copyType;

            case "arrayReverse":
            case "arraySort":
                if (args.size() != 1) {
                    error("'" + name + "' expects exactly 1 argument, got " + args.size() + ".");
                }
                String reverseArrType = checkExpr(args.get(0), env);
                if (!reverseArrType.endsWith("[]")) {
                    error("'" + name + "' requires an array argument, got '" + reverseArrType + "'.");
                }
                return reverseArrType;
                
            case "arraySlice":
                if (args.size() != 3) {
                    error("'arraySlice' expects exactly 3 arguments, got " + args.size() + ".");
                }
                String sliceArrType = checkExpr(args.get(0), env);
                String startSliceType = checkExpr(args.get(1), env);
                String endSliceType = checkExpr(args.get(2), env);
                if (!sliceArrType.endsWith("[]") || !isNumeric(startSliceType) || !isNumeric(endSliceType)) {
                    error("'arraySlice' requires array and numeric arguments.");
                }
                return sliceArrType;
                
            case "arrayConcat":
                if (args.size() != 2) {
                    error("'arrayConcat' expects exactly 2 arguments, got " + args.size() + ".");
                }
                String concatArr1Type = checkExpr(args.get(0), env);
                String concatArr2Type = checkExpr(args.get(1), env);
                if (!concatArr1Type.endsWith("[]") || !concatArr2Type.endsWith("[]")) {
                    error("'arrayConcat' requires array arguments, got '" + concatArr1Type + "' and '" + concatArr2Type + "'.");
                }
                return concatArr1Type;
                
            case "arrayFill":
                if (args.size() != 2) {
                    error("'arrayFill' expects exactly 2 arguments, got " + args.size() + ".");
                }
                checkExpr(args.get(0), env); // Any type for fill value
                String fillSizeType = checkExpr(args.get(1), env);
                if (!isNumeric(fillSizeType)) {
                    error("'arrayFill' size must be numeric, got '" + fillSizeType + "'.");
                }
                return "unknown[]"; // Generic array type
                
            case "arraySum":
            case "arrayAverage":
                if (args.size() != 1) {
                    error("'" + name + "' expects exactly 1 argument, got " + args.size() + ".");
                }
                String numArrType = checkExpr(args.get(0), env);
                if (!numArrType.equals("num[]") && !numArrType.equals("duo[]")) {
                    error("'" + name + "' requires a numeric array, got '" + numArrType + "'.");
                }
                return name.equals("arraySum") ? (numArrType.equals("duo[]") ? "duo" : "num") : "duo";

            case "arrayPush":
                if (args.size() != 2) {
                    error("'arrayPush' expects exactly 2 arguments, got " + args.size() + ".");
                }
                String pushArrType = checkExpr(args.get(0), env);
                if (!pushArrType.endsWith("[]")) {
                    error("'arrayPush' first argument must be an array, got '" + pushArrType + "'.");
                }
                checkExpr(args.get(1), env); 
                return pushArrType; 
                
            case "arrayPop":
                if (args.size() != 1) {
                    error("'arrayPop' expects exactly 1 argument, got " + args.size() + ".");
                }
                String popArrType = checkExpr(args.get(0), env);
                if (!popArrType.endsWith("[]")) {
                    error("'arrayPop' requires an array argument, got '" + popArrType + "'.");
                }
                return popArrType;
                
            case "arrayInsert":
                if (args.size() != 3) {
                    error("'arrayInsert' expects exactly 3 arguments, got " + args.size() + ".");
                }
                String insertArrType = checkExpr(args.get(0), env);
                if (!insertArrType.endsWith("[]")) {
                    error("'arrayInsert' first argument must be an array, got '" + insertArrType + "'.");
                }
                String insertIndexType = checkExpr(args.get(1), env);
                if (!isNumeric(insertIndexType)) {
                    error("'arrayInsert' index must be numeric, got '" + insertIndexType + "'.");
                }
                checkExpr(args.get(2), env); 
                return insertArrType;

            case "isNum":
            case "isDuo":
            case "isSab":
            case "isKya":
            case "isArray":
                if (args.size() != 1) {
                    error("'" + name + "' expects exactly 1 argument, got " + args.size() + ".");
                }
                checkExpr(args.get(0), env); 
                return "kya";
                
            case "typeOf":
                if (args.size() != 1) {
                    error("'typeOf' expects exactly 1 argument, got " + args.size() + ".");
                }
                checkExpr(args.get(0), env);
                return "sab";
                
            case "range":
                if (args.size() != 2) {
                    error("'range' expects exactly 2 arguments, got " + args.size() + ".");
                }
                String rangeStartType = checkExpr(args.get(0), env);
                String rangeEndType = checkExpr(args.get(1), env);
                if (!isNumeric(rangeStartType) || !isNumeric(rangeEndType)) {
                    error("'range' requires numeric arguments, got '" + rangeStartType + "' and '" + rangeEndType + "'.");
                }
                return "num[]";
                
            case "sleep":
                if (args.size() != 1) {
                    error("'sleep' expects exactly 1 argument, got " + args.size() + ".");
                }
                String sleepType = checkExpr(args.get(0), env);
                if (!isNumeric(sleepType)) {
                    error("'sleep' requires a numeric argument, got '" + sleepType + "'.");
                }
                return "kaam";
                
            default:
                error("Unknown native function: " + name);
                return null;
        }
    }

    private boolean isNumeric(String type) {
        return type.equals("num") || type.equals("duo");
    }

    private boolean isAssignable(String from, String to) {
        return from.equals(to) ||
                (from.equals("num") && to.equals("duo")) ||
                (from.equals("unknown[]") && to.endsWith("[]")); 
    }

    private void error(String message) {
        throw new TypeException(message);
    }
    
    private void validateModifiers(VarDecl field) {
        Set<Modifier> modifiers = field.getModifiers();
        
        long accessModifierCount = modifiers.stream()
            .filter(m -> m == Modifier.PUBLIC || m == Modifier.PRIVATE || m == Modifier.PROTECTED)
            .count();
        
        if (accessModifierCount > 1) {
            error("Field '" + field.getName() + "' cannot have multiple access modifiers.");
        }
}
    
    private void validateModifiers(FunctionDecl function) {
        Set<Modifier> modifiers = function.getModifiers();
        long accessModifierCount = modifiers.stream()
            .filter(m -> m == Modifier.PUBLIC || m == Modifier.PRIVATE || m == Modifier.PROTECTED)
            .count();
        
        if (accessModifierCount > 1) {
            error("Method '" + function.getName() + "' cannot have multiple access modifiers.");
        }
        
    }
    
    private boolean isAccessible(ClassDecl fromClass, ClassDecl toClass, Set<Modifier> memberModifiers) {
        if (memberModifiers.contains(Modifier.PUBLIC)) {
            return true;
        }
        if (memberModifiers.contains(Modifier.PRIVATE)) {
            return fromClass == toClass;
        }
        if (memberModifiers.contains(Modifier.PROTECTED)) {
            return fromClass == toClass || isSubclass(fromClass, toClass);
        }
        return true;
    }
    
    private boolean isSubclass(ClassDecl child, ClassDecl parent) {
        if (child == null || parent == null) return false;
        if (child == parent) return true;
        
        VariableExpr superclass = child.getSuperclass();
        if (superclass == null) return false;
        
        ClassDecl superDecl = classRegistry.get(superclass.getName().getLexeme());
        return isSubclass(superDecl, parent);
    }
}