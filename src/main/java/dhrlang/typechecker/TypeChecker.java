package dhrlang.typechecker;

import dhrlang.ast.*;
import dhrlang.error.ErrorReporter;
import dhrlang.error.SourceLocation;
import dhrlang.lexer.TokenType;

import java.util.ArrayList;
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
    private ErrorReporter errorReporter;

    public TypeChecker() {
        this.errorReporter = null;
    }
    
    public TypeChecker(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
    }

    public void check(Program program) {
        for (ClassDecl classDecl : program.getClasses()) {
            if (classRegistry.containsKey(classDecl.getName())) {
                errorWithHint("Class '" + classDecl.getName() + "' is already defined.", classDecl.getSourceLocation(),
                             "Each class name must be unique. Rename one of the classes or check for duplicates");
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
        
        FunctionDecl mainMethod = null;
        for (ClassDecl classDecl : program.getClasses()) {
            FunctionDecl method = classDecl.findMethod("main");
            if (method != null && method.hasModifier(Modifier.STATIC)) {
                if (mainMethod != null) {
                    errorWithHint("Multiple static main methods found. Only one static main method is allowed.", method.getSourceLocation(),
                                 "Remove duplicate main methods - only one 'static kaam main()' should exist across all classes");
                }
                mainMethod = method;
            }
        }
        
        if (mainMethod == null) {
            SourceLocation loc = !program.getClasses().isEmpty() ? program.getClasses().get(0).getSourceLocation() : new SourceLocation("unknown", 1, 1);
            errorWithHint("Entry point error: No static main method found. Please define 'static kaam main()' in any class.", loc,
                         "Add a main method: 'static kaam main() { ... }' in any class to serve as the program entry point");
        } else if (!mainMethod.getParameters().isEmpty()) {
            errorWithHint("Entry point 'main' should not have parameters.", mainMethod.getSourceLocation(),
                         "The main method should be defined as: 'static kaam main()' without any parameters");
        }
    }

    private TypeEnvironment resolveClass(ClassDecl klass) {
        if (classEnvironments.containsKey(klass.getName())) {
            return classEnvironments.get(klass.getName());
        }
        if (klass.isBeingResolved()) {
            errorWithHint("Cyclic inheritance involving class " + klass.getName(), klass.getSourceLocation(),
                         "Remove circular inheritance - classes cannot inherit from each other in a cycle");
        }

        klass.setBeingResolved(true);

        TypeEnvironment parentEnv = globals;
        if (klass.getSuperclass() != null) {
            String superclassName = klass.getSuperclass().getName().getLexeme();
            if (!classRegistry.containsKey(superclassName)) {
                errorWithHint("Undefined superclass '" + superclassName + "'.", klass.getSuperclass().getSourceLocation(),
                             "Make sure the superclass is defined before this class, or check for typos in the class name");
            }
            parentEnv = resolveClass(classRegistry.get(superclassName));
        }

        TypeEnvironment classEnv = new TypeEnvironment(parentEnv);
        
        for (VarDecl field : klass.getVariables()) {
            if (classEnv.getLocalFields().containsKey(field.getName())) {
                errorWithHint("Field '" + field.getName() + "' is already defined in class '" + klass.getName() + "'.", field.getSourceLocation(),
                             "Rename the field or remove the duplicate - each field name must be unique within a class");
            }
            classEnv.define(field.getName(), field.getType());
        }
        
        for (FunctionDecl func : klass.getFunctions()) {
            List<String> paramTypes = func.getParameters().stream()
                    .map(VarDecl::getType)
                    .collect(Collectors.toList());
            
            String methodName = func.getName();
            if (classEnv.getLocalFunctions().containsKey(methodName)) {
                errorWithHint("Method '" + methodName + "' is already defined in class '" + klass.getName() + "'.", func.getSourceLocation(),
                             "Rename the method or remove the duplicate - method overloading is not supported in DhrLang");
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
                    String hint = dhrlang.error.ErrorMessages.getTypeErrorHint(initType, field.getType());
                    errorWithHint("Type mismatch in field '" + field.getName() + "': Cannot assign type '" + 
                          initType + "' to field of type '" + field.getType() + "'.", field.getSourceLocation(), hint);
                }
            }
        }
        
        for (FunctionDecl function : klass.getFunctions()) {
            // Validate modifiers
            validateModifiers(function);
            checkFunction(function, classEnv);
        }
        
        validateAbstractClass(klass);
        
        this.currentClass = null;
    }

    private void checkFunction(FunctionDecl function, TypeEnvironment env) {
        TypeEnvironment local = new TypeEnvironment(env);
        
        if (currentClass != null) {
            local.define("this", currentClass.getName());
        }
        
        for (VarDecl param : function.getParameters()) {
            if (local.getLocalFields().containsKey(param.getName())) {
                errorWithHint("Parameter '" + param.getName() + "' is already defined in function '" + function.getName() + "'.", param.getSourceLocation(),
                             "Rename the parameter - each parameter name must be unique within a function");
            }
            local.define(param.getName(), param.getType());
        }

        String previousReturnType = currentFunctionReturnType;
        currentFunctionReturnType = function.getReturnType();

        // Only check the body if it exists (abstract methods have null bodies)
        if (function.getBody() != null) {
            checkBlock(function.getBody(), local);
        }

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
            checkBreakStmt((BreakStmt) stmt);
        } else if (stmt instanceof ContinueStmt) {
            checkContinueStmt((ContinueStmt) stmt);
        } else if (stmt instanceof PrintStmt) {
            checkExpr(((PrintStmt) stmt).getExpression(), env);
        } else if (stmt instanceof FunctionDecl) {
            checkFunction((FunctionDecl) stmt, env);
        } else {
            errorWithHint("Unsupported statement type: " + stmt.getClass().getSimpleName(), stmt.getSourceLocation(),
                         "This statement type is not yet supported in DhrLang");
        }
    }

    private void checkVarDecl(VarDecl stmt, TypeEnvironment env) {
        if (env.getLocalFields().containsKey(stmt.getName())) {
            errorWithHint("Variable '" + stmt.getName() + "' is already defined in this scope.", stmt.getSourceLocation(),
                         "Rename the variable or remove the duplicate - each variable name must be unique within a scope");
        }

        if (stmt.getInitializer() != null) {
            String initType = checkExpr(stmt.getInitializer(), env);
            if (!isAssignable(initType, stmt.getType())) {
                String hint = dhrlang.error.ErrorMessages.getTypeErrorHint(initType, stmt.getType());
                errorWithHint("Type mismatch: Cannot assign type '" + initType + 
                      "' to variable '" + stmt.getName() + "' of type '" + stmt.getType() + "'.", 
                      stmt.getSourceLocation(), hint);
            }
        }
        
        env.define(stmt.getName(), stmt.getType());
    }

    private void checkIfStmt(IfStmt stmt, TypeEnvironment env) {
        String conditionType = checkExpr(stmt.getCondition(), env);
        if (!conditionType.equals("kya")) {
            errorWithHint("If condition must be a boolean ('kya'), got '" + conditionType + "'.", stmt.getSourceLocation(),
                         "If conditions require boolean expressions: if (x > 5) or if (isValid)");
        }
        checkStatement(stmt.getThenBranch(), env);
        if (stmt.getElseBranch() != null) {
            checkStatement(stmt.getElseBranch(), env);
        }
    }

    private void checkWhileStmt(WhileStmt stmt, TypeEnvironment env) {
        String conditionType = checkExpr(stmt.getCondition(), env);
        if (!conditionType.equals("kya")) {
            errorWithHint("While condition must be a boolean ('kya'), got '" + conditionType + "'.", stmt.getSourceLocation(),
                         "While conditions require boolean expressions: while (count < 10) or while (isRunning)");
        }
        
        boolean wasInLoop = inLoop;
        inLoop = true;
        checkStatement(stmt.getBody(), env);
        inLoop = wasInLoop;
    }

    private void checkBreakStmt(BreakStmt stmt) {
        if (!inLoop) {
            errorWithHint("'break' can only be used inside a loop.", stmt.getSourceLocation(),
                         "Place 'break' inside a 'while' or 'loop' statement to exit early");
        }
    }

    private void checkContinueStmt(ContinueStmt stmt) {
        if (!inLoop) {
            errorWithHint("'continue' can only be used inside a loop.", stmt.getSourceLocation(),
                         "Place 'continue' inside a 'while' or 'loop' statement to skip to next iteration");
        }
    }

    private void checkReturnStmt(ReturnStmt stmt, TypeEnvironment env) {
        if (currentFunctionReturnType == null) {
            errorWithHint("'return' used outside a function.", stmt.getSourceLocation(),
                         "Return statements can only be used inside function definitions");
        }
        
        if (stmt.getValue() == null) {
            if (!currentFunctionReturnType.equals("kaam")) {
                errorWithHint("Function with return type '" + currentFunctionReturnType + "' must return a value.", 
                             stmt.getSourceLocation(),
                             "Add a return value: 'return 42;' or change function return type to 'kaam'");
            }
        } else {
            String returnType = checkExpr(stmt.getValue(), env);
            if (!isAssignable(returnType, currentFunctionReturnType)) {
                errorWithHint("Cannot return '" + returnType + "' from a function expecting '" + currentFunctionReturnType + "'.", 
                             stmt.getSourceLocation(),
                             "Return a value of type '" + currentFunctionReturnType + "' or change the function's return type");
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
        if (expr instanceof ThisExpr) return checkThis((ThisExpr) expr);
        if (expr instanceof SuperExpr) return checkSuper((SuperExpr) expr, env);
        if (expr instanceof CallExpr) return checkCall((CallExpr) expr, env);
        if (expr instanceof ArrayExpr) return checkArray((ArrayExpr) expr, env);
        if (expr instanceof IndexExpr) return checkIndex((IndexExpr) expr, env);
        if (expr instanceof IndexAssignExpr) return checkIndexAssign((IndexAssignExpr) expr, env);
        if (expr instanceof PostfixIncrementExpr) return checkPostfixIncrement((PostfixIncrementExpr) expr, env);
        if (expr instanceof PrefixIncrementExpr) return checkPrefixIncrement((PrefixIncrementExpr) expr, env);
        if (expr instanceof StaticAccessExpr) return checkStaticAccess((StaticAccessExpr) expr, env);
        if (expr instanceof StaticAssignExpr) return checkStaticAssign((StaticAssignExpr) expr, env);

        errorWithHint("Unsupported expression type: " + expr.getClass().getSimpleName(), expr.getSourceLocation(),
                     "This expression type is not yet supported in DhrLang");
        return "unknown"; // Return fallback type to continue checking
    }

    private String checkArray(ArrayExpr expr, TypeEnvironment env) {
        if (expr.getElements().isEmpty()) {
            return "unknown[]"; 
        }

        String elementType = checkExpr(expr.getElements().get(0), env);

        for (int i = 1; i < expr.getElements().size(); i++) {
            String currentType = checkExpr(expr.getElements().get(i), env);
            if (!isAssignable(currentType, elementType)) {
                errorWithHint("Array elements must all have the same type. Expected '" + elementType +
                        "' but found '" + currentType + "' at index " + i + ".", expr.getSourceLocation(),
                        "All array elements must be the same type: [1, 2, 3] or ['a', 'b', 'c']");
            }
        }

        return elementType + "[]";
    }

    private String checkIndex(IndexExpr expr, TypeEnvironment env) {
        String objectType = checkExpr(expr.getObject(), env);
        String indexType = checkExpr(expr.getIndex(), env);

        if (!objectType.endsWith("[]")) {
            errorWithHint("Can only index arrays, got type '" + objectType + "'.", expr.getSourceLocation(),
                         "Array indexing syntax: myArray[0] - ensure the variable is an array type like num[] or sab[]");
        }

        if (!indexType.equals("num")) {
            errorWithHint("Array index must be a number, got '" + indexType + "'.", expr.getSourceLocation(),
                         "Array indices must be integers: array[0], array[i], or array[count-1]");
        }

        return objectType.substring(0, objectType.length() - 2);
    }

    private String checkIndexAssign(IndexAssignExpr expr, TypeEnvironment env) {
        String objectType = checkExpr(expr.getObject(), env);
        String indexType = checkExpr(expr.getIndex(), env);
        String valueType = checkExpr(expr.getValue(), env);

        if (!objectType.endsWith("[]")) {
            errorWithHint("Can only assign to array elements, got type '" + objectType + "'.", expr.getSourceLocation(),
                         "Array assignment syntax: myArray[index] = value - ensure the target is an array");
        }

        if (!indexType.equals("num")) {
            errorWithHint("Array index must be a number, got '" + indexType + "'.", expr.getSourceLocation(),
                         "Array indices must be integers: array[0] = value or array[i] = value");
        }

        String elementType = objectType.substring(0, objectType.length() - 2);
        if (!isAssignable(valueType, elementType)) {
            errorWithHint("Cannot assign '" + valueType + "' to array of '" + elementType + "'.", expr.getSourceLocation(),
                         "Array elements must match the array type: num[] accepts numbers, sab[] accepts strings");
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
            errorWithHint("Invalid " + operation + " target. Must be a variable, property, or array element.", target.getSourceLocation(),
                         "Use " + operation + " on variables, object properties, or array elements: x++, obj.count++, arr[i]++");
            return "unknown";
        }
        
        if (!isNumeric(targetType)) {
            errorWithHint("Can only apply " + operation + " to numeric values, got '" + targetType + "'.", target.getSourceLocation(),
                         "Increment/decrement operations work only on numbers: count++, value--, index++");
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
            errorWithHint("Undefined variable '" + expr.getName().getLexeme() + "'.", expr.getSourceLocation(),
                         "Make sure the variable is declared before use: num x = 42; or check for typos in variable name");
            return "unknown";
        }
    }

    private String checkUnary(UnaryExpr expr, TypeEnvironment env) {
        String rightType = checkExpr(expr.getRight(), env);
        TokenType op = expr.getOperator().getType();
        
        if (op == TokenType.MINUS) {
            if (!isNumeric(rightType)) {
                errorWithHint("Operand for '-' must be a number, got '" + rightType + "'.", 
                             expr.getSourceLocation(),
                             "Use numeric values like 42 or 3.14 with unary minus operator");
            }
            return rightType;
        } else if (op == TokenType.NOT) {
            if (!rightType.equals("kya")) {
                errorWithHint("Operand for '!' must be a boolean, got '" + rightType + "'.", 
                             expr.getSourceLocation(),
                             "Use boolean values (true/false) with the '!' operator");
            }
            return "kya";
        }
        
        errorWithHint("Unsupported unary operator: " + op, expr.getSourceLocation(),
                     "Use supported unary operators: - (minus) or ! (not)");
        return "unknown";
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
                    errorWithHint("Operands for " + opName + " must be numbers (or strings for '+'), got '" + 
                          leftType + "' and '" + rightType + "'.", expr.getSourceLocation(),
                          "Use numeric values for arithmetic operations, or strings for concatenation with '+'");
                }
                return (leftType.equals("duo") || rightType.equals("duo")) ? "duo" : "num";
                
            case SLASH:
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    errorWithHint("Operands for division must be numbers, got '" + leftType + "' and '" + rightType + "'.", expr.getSourceLocation(),
                                 "Division requires numeric operands like: 10 / 2 or 5.0 / 2.5");
                }
                return "duo"; 
                
            case GREATER:
            case GEQ:
            case LESS:
            case LEQ:
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    errorWithHint("Operands for comparison must be numbers, got '" + leftType + "' and '" + rightType + "'.", expr.getSourceLocation(),
                                 "Comparison operators (<, >, <=, >=) work with numbers: x > 5 or price <= 100.0");
                }
                return "kya";
                
            case EQUALITY:
            case NEQ:
                if (!isAssignable(leftType, rightType) && !isAssignable(rightType, leftType)) {
                    errorWithHint("Cannot compare incompatible types: '" + leftType + "' and '" + rightType + "'.", expr.getSourceLocation(),
                                 "Compare values of the same type: 'hello' == 'world' or 42 == 24");
                }
                return "kya";
                
            case AND:
            case OR:
                if (!leftType.equals("kya")) {
                    errorWithHint("Left operand of logical operator must be boolean, got '" + leftType + "'.", expr.getSourceLocation(),
                                 "Logical operators (&&, ||) require boolean values: true && false");
                }
                if (!rightType.equals("kya")) {
                    errorWithHint("Right operand of logical operator must be boolean, got '" + rightType + "'.", expr.getSourceLocation(),
                                 "Logical operators (&&, ||) require boolean values: true && false");
                }
                return "kya";
                
            default:
                errorWithHint("Unsupported binary operator: " + op, expr.getSourceLocation(),
                             "Use supported operators: +, -, *, /, %, ==, !=, <, >, <=, >=, &&, ||");
                return "unknown";
        }
    }

    private String checkAssign(AssignmentExpr expr, TypeEnvironment env) {
        String varName = expr.getName().getLexeme();
        String varType;
        try {
            varType = env.get(varName);
        } catch (TypeException e) {
            errorWithHint("Cannot assign to undefined variable '" + varName + "'.", expr.getSourceLocation(),
                         "Declare the variable first: num " + varName + " = 0; then assign: " + varName + " = value;");
            return "unknown"; 
        }
        
        String valType = checkExpr(expr.getValue(), env);
        if (!isAssignable(valType, varType)) {
            String hint = dhrlang.error.ErrorMessages.getTypeErrorHint(valType, varType);
            errorWithHint("Cannot assign type '" + valType + "' to variable '" + varName + "' of type '" + varType + "'.",
                         expr.getSourceLocation(), hint);
        }
        return valType;
    }

    private String checkNew(NewExpr expr, TypeEnvironment env) {
        String className = expr.getClassName();
        if (!classRegistry.containsKey(className)) {
            errorWithHint("Cannot instantiate unknown class '" + className + "'.", expr.getSourceLocation(),
                         "Make sure the class is defined before creating instances: class " + className + " { ... }");
        }
        
        ClassDecl classDecl = classRegistry.get(className);
        FunctionDecl init = classDecl.findMethod("init");
        
        if (init != null) {
            checkFunctionArguments("init", init.getParameters(), expr.getArguments(), env, expr.getSourceLocation());
        } else if (!expr.getArguments().isEmpty()) {
            errorWithHint("Class '" + className + "' has no 'init' constructor and cannot be called with arguments.", expr.getSourceLocation(),
                         "Remove arguments: new " + className + "(); or add an init method to the class");
        }
        
        return className;
    }

    private String checkNewArray(NewArrayExpr expr, TypeEnvironment env) {
        String elementType = expr.getElementType();
        
        if (!elementType.equals("num") && !elementType.equals("duo") && 
            !elementType.equals("ek") && !elementType.equals("sab") && 
            !elementType.equals("kya") && !classRegistry.containsKey(elementType)) {
            errorWithHint("Unknown array element type '" + elementType + "'.", expr.getSourceLocation(),
                         "Use valid DhrLang types: num, duo, sab, kya, ek, or a defined class name");
        }
        
        String sizeType = checkExpr(expr.getSize(), env);
        if (!isNumeric(sizeType)) {
            errorWithHint("Array size must be numeric, got '" + sizeType + "'.", expr.getSourceLocation(),
                         "Array size must be a number: new num[10] or new sab[count]");
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
            errorWithHint("Can only access properties on class instances, got type '" + objectType + "'.", expr.getSourceLocation(),
                         "Property access syntax: object.property - ensure the object is a class instance");
        }
        
        try {
            return instanceEnv.get(propName);
        } catch (TypeException fieldError) {
            try {
                instanceEnv.getFunction(propName);
                return "method"; 
            } catch (TypeException funcError) {
                errorWithHint("Property '" + propName + "' not found on class '" + objectType + "'.", expr.getSourceLocation(),
                             "Check the property name and ensure it's defined in the class");
                return "unknown";
            }
        }
    }

    private String checkSet(SetExpr expr, TypeEnvironment env) {
        String objectType = checkExpr(expr.getObject(), env);
        TypeEnvironment instanceEnv = classEnvironments.get(objectType);
        if (instanceEnv == null) {
            errorWithHint("Can only set properties on class instances, got type '" + objectType + "'.", expr.getSourceLocation(),
                         "Property assignment syntax: object.field = value - ensure the object is a class instance");
        }
        
        String fieldName = expr.getName().getLexeme();
        String fieldType;
        try {
            fieldType = instanceEnv.get(fieldName);
        } catch (TypeException e) {
            errorWithHint("Field '" + fieldName + "' not found on class '" + objectType + "'.", expr.getSourceLocation(),
                         "Check the field name and ensure it's defined in the class");
            return "unknown";
        }
        
        String valueType = checkExpr(expr.getValue(), env);
        if (!isAssignable(valueType, fieldType)) {
            errorWithHint("Cannot assign type '" + valueType + "' to field '" + fieldName + "' of type '" + fieldType + "'.", expr.getSourceLocation(),
                         "Assign a value of type '" + fieldType + "' to the field");
        }
        return valueType;
    }

    private String checkThis(ThisExpr expr) {
        if (currentClass == null) {
            errorWithHint("Cannot use 'this' outside of a class.", expr.getSourceLocation(),
                         "Use 'this' only inside class methods to refer to the current instance");
        }
        return currentClass.getName();
    }

    private String checkSuper(SuperExpr expr, TypeEnvironment env) {
        if (currentClass == null || currentClass.getSuperclass() == null) {
            errorWithHint("Cannot use 'super' outside of a class with a superclass.", expr.getSourceLocation(),
                         "Use 'super' only in classes that extend another class");
        }
        
        String methodName = expr.method.getLexeme();
        String superclassName = currentClass.getSuperclass().getName().getLexeme();
        TypeEnvironment superEnv = classEnvironments.get(superclassName);
        
        try {
            superEnv.getFunction(methodName);
        } catch (TypeException e) {
            errorWithHint("Method '" + methodName + "' not found in superclass '" + superclassName + "'.", expr.getSourceLocation(),
                         "Check the method name and ensure it exists in the parent class");
        }
        
        return "method";
    }
    
    private String checkStaticAccess(StaticAccessExpr expr, TypeEnvironment env) {
        String className = expr.className.getName().getLexeme();
        String memberName = expr.memberName.getLexeme();
        
        if (!classRegistry.containsKey(className)) {
            errorWithHint("Unknown class '" + className + "' in static access.", expr.getSourceLocation(),
                         "Make sure the class is defined before accessing static members");
        }
        
        ClassDecl classDecl = classRegistry.get(className);
        
        for (VarDecl field : classDecl.getVariables()) {
            if (field.getName().equals(memberName) && field.hasModifier(Modifier.STATIC)) {
                if (!isAccessible(currentClass, classDecl, field.getModifiers())) {
                    errorWithHint("Cannot access private/protected static field '" + memberName + "' from class '" + className + "'.", expr.getSourceLocation(),
                                 "Use public static fields or access from within the same class");
                }
                return field.getType();
            }
        }
        
        for (FunctionDecl method : classDecl.getFunctions()) {
            if (method.getName().equals(memberName) && method.hasModifier(Modifier.STATIC)) {
                if (!isAccessible(currentClass, classDecl, method.getModifiers())) {
                    errorWithHint("Cannot access private/protected static method '" + memberName + "' from class '" + className + "'.", expr.getSourceLocation(),
                                 "Use public static methods or access from within the same class");
                }
                return "method";
            }
        }
        
        errorWithHint("Static member '" + memberName + "' not found in class '" + className + "'.", expr.getSourceLocation(),
                     "Check the member name and ensure it's declared as static");
        return "unknown";
    }
    
    private String checkStaticAssign(StaticAssignExpr expr, TypeEnvironment env) {
        String className = expr.className.getName().getLexeme();
        String memberName = expr.memberName.getLexeme();
        
        if (!classRegistry.containsKey(className)) {
            errorWithHint("Unknown class '" + className + "' in static assignment.", expr.getSourceLocation(),
                         "Make sure the class is defined before assigning to static fields");
        }
        
        ClassDecl classDecl = classRegistry.get(className);
        
        for (VarDecl field : classDecl.getVariables()) {
            if (field.getName().equals(memberName) && field.hasModifier(Modifier.STATIC)) {
                if (!isAccessible(currentClass, classDecl, field.getModifiers())) {
                    errorWithHint("Cannot access private/protected static field '" + memberName + "' from class '" + className + "'.", expr.getSourceLocation(),
                                 "Use public static fields or access from within the same class");
                }
                
                String valueType = checkExpr(expr.value, env);
                if (!isAssignable(valueType, field.getType())) {
                    errorWithHint("Cannot assign '" + valueType + "' to static field '" + memberName + "' of type '" + field.getType() + "'.", expr.getSourceLocation(),
                                 "Assign a value of type '" + field.getType() + "' to the static field");
                }
                
                return valueType;
            }
        }
        
        errorWithHint("Static field '" + memberName + "' not found in class '" + className + "'.", expr.getSourceLocation(),
                     "Check the field name and ensure it's declared as static");
        return "unknown";
    }

    private String checkCall(CallExpr call, TypeEnvironment env) {
        Expression callee = call.getCallee();
        FunctionSignature signature;
        String funcName;
        
        if (callee instanceof VariableExpr) {
            funcName = ((VariableExpr) callee).getName().getLexeme();
            if (isNativeFunction(funcName)) {
                return checkNativeFunction(funcName, call.getArguments(), call, env);
            }
            try {
                signature = env.getFunction(funcName);
            } catch (TypeException e) {
                errorWithHint("Undefined function '" + funcName + "'.", call.getSourceLocation(),
                             "Make sure the function is declared before calling it");
                return "unknown";
            }
        } else if (callee instanceof GetExpr) {
            String objectType = checkExpr(((GetExpr) callee).getObject(), env);
            TypeEnvironment instanceEnv = classEnvironments.get(objectType);
            if (instanceEnv == null) {
                errorWithHint("Can only call methods on class instances, got type '" + objectType + "'.", call.getSourceLocation(),
                             "Method calls syntax: object.method() - ensure the object is a class instance");
                return "unknown";  // Return after error instead of continuing
            }
            funcName = ((GetExpr) callee).getName().getLexeme();
            try {
                signature = instanceEnv.getFunction(funcName);
            } catch (TypeException e) {
                errorWithHint("Method '" + funcName + "' not found on class '" + objectType + "'.", call.getSourceLocation(),
                             "Check the method name and ensure it's defined in the class");
                return "unknown";
            }
        } else if (callee instanceof SuperExpr) {
            SuperExpr superExpr = (SuperExpr) callee;
            if (currentClass == null || currentClass.getSuperclass() == null) {
                errorWithHint("'super' used incorrectly.", superExpr.getSourceLocation(),
                             "Use 'super' only in classes that extend another class to call parent methods");
            }
            String superclassName = currentClass.getSuperclass().getName().getLexeme();
            funcName = superExpr.method.getLexeme();
            try {
                signature = classEnvironments.get(superclassName).getFunction(funcName);
            } catch (TypeException e) {
                errorWithHint("Method '" + funcName + "' not found in superclass '" + superclassName + "'.", call.getSourceLocation(),
                             "Check the method name and ensure it exists in the parent class");
                return "unknown";
            }
        } else if (callee instanceof StaticAccessExpr) {
            StaticAccessExpr staticAccess = (StaticAccessExpr) callee;
            String className = staticAccess.className.getName().getLexeme();
            funcName = staticAccess.memberName.getLexeme();
            
            TypeEnvironment classEnv = classEnvironments.get(className);
            if (classEnv == null) {
                errorWithHint("Class '" + className + "' not found.", call.getSourceLocation(),
                             "Make sure the class is defined before calling static methods");
                return "unknown";
            }
            
            ClassDecl classDecl = classRegistry.get(className);
            if (classDecl == null) {
                errorWithHint("Class '" + className + "' not found.", call.getSourceLocation(),
                             "Make sure the class is defined before calling static methods");
                return "unknown";
            }
            
            FunctionDecl methodDecl = classDecl.findMethod(funcName);
            if (methodDecl == null) {
                errorWithHint("Static method '" + funcName + "' not found in class '" + className + "'.", call.getSourceLocation(),
                             "Check the method name and ensure it's declared as static in the class");
                return "unknown";
            }
            
            if (!methodDecl.hasModifier(Modifier.STATIC)) {
                errorWithHint("Cannot call non-static method '" + funcName + "' from static context.", call.getSourceLocation(),
                             "Add 'static' modifier to the method or create an instance to call it");
                return "unknown";
            }
            
            try {
                signature = classEnv.getFunction(funcName);
            } catch (TypeException e) {
                errorWithHint("Static method '" + funcName + "' not found in class '" + className + "'.", call.getSourceLocation(),
                             "Check the method name and ensure it's declared as static in the class");
                return "unknown";
            }
        } else {
            errorWithHint("Expression is not callable.", call.getSourceLocation(),
                         "Only functions and methods can be called. Use function_name() syntax");
            return "unknown";
        }
        
        checkFunctionArguments(funcName, signature.getParameterTypes(), call.getArguments(), env, call.getSourceLocation());
        return signature.getReturnType();
    }

    private void checkFunctionArguments(String name, List<?> expectedParams, List<Expression> args, TypeEnvironment env, SourceLocation callLocation) {
        if (args.size() != expectedParams.size()) {
            errorWithHint("Function '" + name + "' expects " + expectedParams.size() + 
                  " arguments, but got " + args.size() + ".", callLocation,
                  "Check the function definition and provide the correct number of arguments");
            return;  
        }
        
        boolean typesAsStrings = !expectedParams.isEmpty() && expectedParams.get(0) instanceof String;
        
        for (int i = 0; i < args.size(); i++) {
            String argType = checkExpr(args.get(i), env);
            String expectedType = typesAsStrings ? 
                (String) expectedParams.get(i) : 
                ((VarDecl) expectedParams.get(i)).getType();
            
            if (!isAssignable(argType, expectedType)) {
                errorWithHint("Argument " + (i + 1) + " for '" + name + "' should be '" + expectedType + 
                      "', but got '" + argType + "'.", args.get(i).getSourceLocation(),
                      "Pass an argument of type '" + expectedType + "' for parameter " + (i + 1));
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

    private String checkNativeFunction(String name, List<Expression> args, CallExpr call, TypeEnvironment env) {
        switch (name) {
            case "print":
            case "printLine":
                if (args.size() != 1) {
                    errorWithHint("'" + name + "' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(value) to print a single value to the console");
                    return "kaam";
                }
                checkExpr(args.get(0), env);
                return "kaam";
                
            case "clock":
                if (!args.isEmpty()) {
                    errorWithHint("'clock' expects 0 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use clock() without arguments to get the current timestamp");
                }
                return "duo";
                
            case "abs":
                if (args.size() != 1) {
                    errorWithHint("'abs' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use abs(number) to get the absolute value of a number");
                }
                String absType = checkExpr(args.get(0), env);
                if (!isNumeric(absType)) {
                    errorWithHint("'abs' requires a numeric argument, got '" + absType + "'.", call.getSourceLocation(),
                                 "Absolute value only works with numbers: abs(42) or abs(-3.14)");
                }
                return absType;
                
            case "sqrt":
            case "floor":
            case "ceil":
                if (args.size() != 1) {
                    errorWithHint("'" + name + "' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(number) to perform the mathematical operation");
                }
                String argType = checkExpr(args.get(0), env);
                if (!isNumeric(argType)) {
                    errorWithHint("'" + name + "' requires a numeric argument, got '" + argType + "'.", call.getSourceLocation(),
                                 "Mathematical functions only work with numbers: " + name + "(42) or " + name + "(3.14)");
                }
                return name.equals("sqrt") ? "duo" : "num";
                
            case "round":
                if (args.size() != 1) {
                    errorWithHint("'round' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use round(number) to round a number to the nearest integer");
                }
                String roundType = checkExpr(args.get(0), env);
                if (!isNumeric(roundType)) {
                    errorWithHint("'round' requires a numeric argument, got '" + roundType + "'.", call.getSourceLocation(),
                                 "Rounding only works with numbers: round(3.14) becomes 3");
                }
                return "num";
                
            case "pow":
            case "min":
            case "max":
                if (args.size() != 2) {
                    errorWithHint("'" + name + "' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(number1, number2) to perform the operation on two numbers");
                }
                String leftType = checkExpr(args.get(0), env);
                String rightType = checkExpr(args.get(1), env);
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    errorWithHint("'" + name + "' requires numeric arguments, got '" + leftType + "' and '" + rightType + "'.", call.getSourceLocation(),
                                 "Both arguments must be numbers: " + name + "(5, 3) or " + name + "(2.5, 1.8)");
                }
                return name.equals("pow") ? "duo" : (leftType.equals("duo") || rightType.equals("duo") ? "duo" : "num");
                
            case "random":
                if (!args.isEmpty()) {
                    errorWithHint("'random' expects 0 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use random() without arguments to get a random number between 0.0 and 1.0");
                }
                return "duo";
                
            // String functions
            case "length":
                if (args.size() != 1) {
                    errorWithHint("'length' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use length(string) to get the number of characters in a string");
                }
                String strType = checkExpr(args.get(0), env);
                if (!strType.equals("sab")) {
                    errorWithHint("'length' requires a string argument, got '" + strType + "'.", call.getSourceLocation(),
                                 "String length only works with strings: length('hello') returns 5");
                }
                return "num";
                
            case "substring":
                if (args.size() != 3) {
                    errorWithHint("'substring' expects exactly 3 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use substring(string, startIndex, endIndex) to extract a portion of a string");
                }
                String subStrType = checkExpr(args.get(0), env);
                String startType = checkExpr(args.get(1), env);
                String endType = checkExpr(args.get(2), env);
                if (!subStrType.equals("sab")) {
                    errorWithHint("'substring' first argument must be a string, got '" + subStrType + "'.", call.getSourceLocation(),
                                 "Substring requires a string as first argument: substring('hello', 1, 3)");
                }
                if (!isNumeric(startType) || !isNumeric(endType)) {
                    errorWithHint("'substring' indices must be numeric, got '" + startType + "' and '" + endType + "'.", call.getSourceLocation(),
                                 "Start and end indices must be numbers: substring('hello', 0, 3)");
                }
                return "sab";
                
            case "charAt":
                if (args.size() != 2) {
                    errorWithHint("'charAt' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use charAt(string, index) to get the character at a specific position");
                }
                String charStrType = checkExpr(args.get(0), env);
                String indexType = checkExpr(args.get(1), env);
                if (!charStrType.equals("sab")) {
                    errorWithHint("'charAt' first argument must be a string, got '" + charStrType + "'.", call.getSourceLocation(),
                                 "charAt requires a string as first argument: charAt('hello', 0)");
                }
                if (!isNumeric(indexType)) {
                    errorWithHint("'charAt' index must be numeric, got '" + indexType + "'.", call.getSourceLocation(),
                                 "Index must be a number: charAt('hello', 1) returns 'e'");
                }
                return "sab";
                
            case "toUpperCase":
            case "toLowerCase":
            case "trim":
                if (args.size() != 1) {
                    errorWithHint("'" + name + "' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(string) to transform the string");
                }
                String caseType = checkExpr(args.get(0), env);
                if (!caseType.equals("sab")) {
                    errorWithHint("'" + name + "' requires a string argument, got '" + caseType + "'.", call.getSourceLocation(),
                                 "String transformation only works with strings: " + name + "('Hello')");
                }
                return "sab";
                
            case "indexOf":
            case "startsWith":
            case "endsWith":
                if (args.size() != 2) {
                    errorWithHint("'" + name + "' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(string, searchString) to search within a string");
                }
                String baseType = checkExpr(args.get(0), env);
                String searchType = checkExpr(args.get(1), env);
                if (!baseType.equals("sab") || !searchType.equals("sab")) {
                    errorWithHint("'" + name + "' requires string arguments, got '" + baseType + "' and '" + searchType + "'.", call.getSourceLocation(),
                                 "Both arguments must be strings: " + name + "('hello', 'lo')");
                }
                return name.equals("indexOf") ? "num" : "kya";
                
            case "replace":
                if (args.size() != 3) {
                    errorWithHint("'replace' expects exactly 3 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use replace(string, target, replacement) to replace text in a string");
                }
                String replaceStrType = checkExpr(args.get(0), env);
                String targetType = checkExpr(args.get(1), env);
                String replacementType = checkExpr(args.get(2), env);
                if (!replaceStrType.equals("sab") || !targetType.equals("sab") || !replacementType.equals("sab")) {
                    errorWithHint("'replace' requires string arguments.", call.getSourceLocation(),
                                 "All arguments must be strings: replace('hello', 'l', 'x') becomes 'hexxo'");
                }
                return "sab";
                
            case "readLine":
                if (!args.isEmpty()) {
                    errorWithHint("'readLine' expects 0 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use readLine() to read user input from the console");
                }
                return "sab";
                
            case "readLineWithPrompt":
                if (args.size() != 1) {
                    errorWithHint("'readLineWithPrompt' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use readLineWithPrompt('Enter name: ') to show a prompt and read input");
                }
                String promptType = checkExpr(args.get(0), env);
                if (!promptType.equals("sab")) {
                    errorWithHint("'readLineWithPrompt' requires a string prompt, got '" + promptType + "'.", call.getSourceLocation(),
                                 "Prompt must be a string: readLineWithPrompt('Enter your age: ')");
                }
                return "sab";
                
            case "toNum":
            case "toDuo":
                if (args.size() != 1) {
                    errorWithHint("'" + name + "' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(string) to convert a string to a number");
                }
                String parseType = checkExpr(args.get(0), env);
                if (!parseType.equals("sab")) {
                    errorWithHint("'" + name + "' requires a string argument, got '" + parseType + "'.", call.getSourceLocation(),
                                 "Number conversion only works with strings: " + name + "('42')");
                }
                return name.equals("toNum") ? "num" : "duo";
                
            case "toString":
                if (args.size() != 1) {
                    errorWithHint("'toString' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use toString(value) to convert any value to a string representation");
                }
                checkExpr(args.get(0), env); // Any type is acceptable
                return "sab";

            // Advanced String Functions
            case "split":
                if (args.size() != 2) {
                    errorWithHint("'split' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use split(string, delimiter) to split a string into an array");
                }
                String splitStrType = checkExpr(args.get(0), env);
                String delimiterType = checkExpr(args.get(1), env);
                if (!splitStrType.equals("sab") || !delimiterType.equals("sab")) {
                    errorWithHint("'split' requires string arguments, got '" + splitStrType + "' and '" + delimiterType + "'.", call.getSourceLocation(),
                                 "Both arguments must be strings: split('a,b,c', ',') returns ['a', 'b', 'c']");
                }
                return "sab[]";
                
            case "join":
                if (args.size() != 2) {
                    errorWithHint("'join' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use join(stringArray, delimiter) to join array elements into a string");
                }
                String joinArrType = checkExpr(args.get(0), env);
                String joinDelimType = checkExpr(args.get(1), env);
                if (!joinArrType.equals("sab[]") || !joinDelimType.equals("sab")) {
                    errorWithHint("'join' requires string array and string delimiter, got '" + joinArrType + "' and '" + joinDelimType + "'.", call.getSourceLocation(),
                                 "First argument must be a string array, second a delimiter: join(['a', 'b'], ',')");
                }
                return "sab";
                
            case "repeat":
                if (args.size() != 2) {
                    errorWithHint("'repeat' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use repeat(string, count) to repeat a string a specified number of times");
                }
                String repeatStrType = checkExpr(args.get(0), env);
                String repeatCountType = checkExpr(args.get(1), env);
                if (!repeatStrType.equals("sab") || !isNumeric(repeatCountType)) {
                    errorWithHint("'repeat' requires string and numeric arguments, got '" + repeatStrType + "' and '" + repeatCountType + "'.", call.getSourceLocation(),
                                 "First argument must be a string, second a number: repeat('hi', 3) returns 'hihihi'");
                }
                return "sab";
                
            case "reverse":
                if (args.size() != 1) {
                    errorWithHint("'reverse' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use reverse(string) to reverse the characters in a string");
                }
                String reverseType = checkExpr(args.get(0), env);
                if (!reverseType.equals("sab")) {
                    errorWithHint("'reverse' requires a string argument, got '" + reverseType + "'.", call.getSourceLocation(),
                                 "String reversal only works with strings: reverse('hello') returns 'olleh'");
                }
                return "sab";
                
            case "padLeft":
            case "padRight":
                if (args.size() != 3) {
                    errorWithHint("'" + name + "' expects exactly 3 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(string, length, padChar) to pad a string to a specific length");
                }
                String padStrType = checkExpr(args.get(0), env);
                String padLengthType = checkExpr(args.get(1), env);
                String padCharType = checkExpr(args.get(2), env);
                if (!padStrType.equals("sab") || !isNumeric(padLengthType) || !padCharType.equals("sab")) {
                    errorWithHint("'" + name + "' requires string, numeric, and string arguments.", call.getSourceLocation(),
                                 "All arguments must be: string, number, string: " + name + "('hi', 5, '0') becomes '000hi'");
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
                    errorWithHint("'" + name + "' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(number) to perform the mathematical operation");
                }
                String mathArgType = checkExpr(args.get(0), env);
                if (!isNumeric(mathArgType)) {
                    errorWithHint("'" + name + "' requires a numeric argument, got '" + mathArgType + "'.", call.getSourceLocation(),
                                 "Mathematical functions only work with numbers: " + name + "(1.5) or " + name + "(45)");
                }
                return "duo";
                
            case "randomRange":
                if (args.size() != 2) {
                    errorWithHint("'randomRange' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use randomRange(min, max) to get a random integer between min and max");
                }
                String minType = checkExpr(args.get(0), env);
                String maxType = checkExpr(args.get(1), env);
                if (!isNumeric(minType) || !isNumeric(maxType)) {
                    errorWithHint("'randomRange' requires numeric arguments, got '" + minType + "' and '" + maxType + "'.", call.getSourceLocation(),
                                 "Both arguments must be numbers: randomRange(1, 10) returns integer between 1-10");
                }
                return "num";
                
            case "clamp":
                if (args.size() != 3) {
                    errorWithHint("'clamp' expects exactly 3 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use clamp(value, min, max) to constrain a value between bounds");
                }
                String clampValueType = checkExpr(args.get(0), env);
                String clampMinType = checkExpr(args.get(1), env);
                String clampMaxType = checkExpr(args.get(2), env);
                if (!isNumeric(clampValueType) || !isNumeric(clampMinType) || !isNumeric(clampMaxType)) {
                    errorWithHint("'clamp' requires numeric arguments.", call.getSourceLocation(),
                                 "All arguments must be numbers: clamp(15, 0, 10) returns 10");
                }
                return clampValueType.equals("duo") || clampMinType.equals("duo") || clampMaxType.equals("duo") ? "duo" : "num";
                
            case "arrayLength":
                if (args.size() != 1) {
                    errorWithHint("'arrayLength' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use arrayLength(array) to get the number of elements in an array");
                }
                String arrType = checkExpr(args.get(0), env);
                if (!arrType.endsWith("[]")) {
                    errorWithHint("'arrayLength' requires an array argument, got '" + arrType + "'.", call.getSourceLocation(),
                                 "Array length only works with arrays: arrayLength(myArray) where myArray is num[] or sab[]");
                }
                return "num";
                
            case "arrayContains":
            case "arrayIndexOf":
                if (args.size() != 2) {
                    errorWithHint("'" + name + "' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(array, value) to search for a value in an array");
                }
                String containsArrType = checkExpr(args.get(0), env);
                checkExpr(args.get(1), env); 
                if (!containsArrType.endsWith("[]")) {
                    errorWithHint("'" + name + "' first argument must be an array, got '" + containsArrType + "'.", call.getSourceLocation(),
                                 "First argument must be an array: " + name + "(myArray, searchValue)");
                }
                return name.equals("arrayContains") ? "kya" : "num";
                
            case "arrayCopy":
                if (args.size() != 1) {
                    errorWithHint("'arrayCopy' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use arrayCopy(array) to create a shallow copy of an array");
                }
                String copyType = checkExpr(args.get(0), env);
                if (!copyType.endsWith("[]")) {
                    errorWithHint("'arrayCopy' requires an array argument, got '" + copyType + "'.", call.getSourceLocation(),
                                 "Array copy only works with arrays: arrayCopy(myArray) returns a new array");
                }
                return copyType;

            case "arrayReverse":
            case "arraySort":
                if (args.size() != 1) {
                    errorWithHint("'" + name + "' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(array) to modify the array in place");
                }
                String reverseArrType = checkExpr(args.get(0), env);
                if (!reverseArrType.endsWith("[]")) {
                    errorWithHint("'" + name + "' requires an array argument, got '" + reverseArrType + "'.", call.getSourceLocation(),
                                 "Array manipulation only works with arrays: " + name + "(myArray)");
                }
                return reverseArrType;
                
            case "arraySlice":
                if (args.size() != 3) {
                    errorWithHint("'arraySlice' expects exactly 3 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use arraySlice(array, startIndex, endIndex) to extract a portion of an array");
                }
                String sliceArrType = checkExpr(args.get(0), env);
                String startSliceType = checkExpr(args.get(1), env);
                String endSliceType = checkExpr(args.get(2), env);
                if (!sliceArrType.endsWith("[]") || !isNumeric(startSliceType) || !isNumeric(endSliceType)) {
                    errorWithHint("'arraySlice' requires array and numeric arguments.", call.getSourceLocation(),
                                 "First argument must be an array, others numbers: arraySlice(myArray, 1, 3)");
                }
                return sliceArrType;
                
            case "arrayConcat":
                if (args.size() != 2) {
                    errorWithHint("'arrayConcat' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use arrayConcat(array1, array2) to combine two arrays");
                }
                String concatArr1Type = checkExpr(args.get(0), env);
                String concatArr2Type = checkExpr(args.get(1), env);
                if (!concatArr1Type.endsWith("[]") || !concatArr2Type.endsWith("[]")) {
                    errorWithHint("'arrayConcat' requires array arguments, got '" + concatArr1Type + "' and '" + concatArr2Type + "'.", call.getSourceLocation(),
                                 "Both arguments must be arrays: arrayConcat(arr1, arr2)");
                }
                return concatArr1Type;
                
            case "arrayFill":
                if (args.size() != 2) {
                    errorWithHint("'arrayFill' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use arrayFill(value, size) to create an array filled with a value");
                }
                checkExpr(args.get(0), env); // Any type for fill value
                String fillSizeType = checkExpr(args.get(1), env);
                if (!isNumeric(fillSizeType)) {
                    errorWithHint("'arrayFill' size must be numeric, got '" + fillSizeType + "'.", call.getSourceLocation(),
                                 "Size must be a number: arrayFill('hello', 5) creates array with 5 'hello' strings");
                }
                return "unknown[]"; // Generic array type
                
            case "arraySum":
            case "arrayAverage":
                if (args.size() != 1) {
                    errorWithHint("'" + name + "' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(numericArray) to perform calculation on array elements");
                }
                String numArrType = checkExpr(args.get(0), env);
                if (!numArrType.equals("num[]") && !numArrType.equals("duo[]")) {
                    errorWithHint("'" + name + "' requires a numeric array, got '" + numArrType + "'.", call.getSourceLocation(),
                                 "Array must contain numbers: " + name + "(numArray) where numArray is num[] or duo[]");
                }
                return name.equals("arraySum") ? (numArrType.equals("duo[]") ? "duo" : "num") : "duo";

            case "arrayPush":
                if (args.size() != 2) {
                    errorWithHint("'arrayPush' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use arrayPush(array, element) to add an element to the end of an array");
                }
                String pushArrType = checkExpr(args.get(0), env);
                if (!pushArrType.endsWith("[]")) {
                    errorWithHint("'arrayPush' first argument must be an array, got '" + pushArrType + "'.", call.getSourceLocation(),
                                 "First argument must be an array: arrayPush(myArray, newElement)");
                }
                checkExpr(args.get(1), env); 
                return pushArrType; 
                
            case "arrayPop":
                if (args.size() != 1) {
                    errorWithHint("'arrayPop' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use arrayPop(array) to remove and return the last element");
                }
                String popArrType = checkExpr(args.get(0), env);
                if (!popArrType.endsWith("[]")) {
                    errorWithHint("'arrayPop' requires an array argument, got '" + popArrType + "'.", call.getSourceLocation(),
                                 "Array pop only works with arrays: arrayPop(myArray) removes the last element");
                }
                return popArrType;
                
            case "arrayInsert":
                if (args.size() != 3) {
                    errorWithHint("'arrayInsert' expects exactly 3 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use arrayInsert(array, index, element) to insert an element at a specific position");
                }
                String insertArrType = checkExpr(args.get(0), env);
                if (!insertArrType.endsWith("[]")) {
                    errorWithHint("'arrayInsert' first argument must be an array, got '" + insertArrType + "'.", call.getSourceLocation(),
                                 "First argument must be an array: arrayInsert(myArray, 2, element)");
                }
                String insertIndexType = checkExpr(args.get(1), env);
                if (!isNumeric(insertIndexType)) {
                    errorWithHint("'arrayInsert' index must be numeric, got '" + insertIndexType + "'.", call.getSourceLocation(),
                                 "Index must be a number: arrayInsert(myArray, 2, element)");
                }
                checkExpr(args.get(2), env); 
                return insertArrType;

            case "isNum":
            case "isDuo":
            case "isSab":
            case "isKya":
            case "isArray":
                if (args.size() != 1) {
                    errorWithHint("'" + name + "' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(value) to check if a value is of a specific type");
                }
                checkExpr(args.get(0), env); 
                return "kya";
                
            case "typeOf":
                if (args.size() != 1) {
                    errorWithHint("'typeOf' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use typeOf(value) to get the type name of a value as a string");
                }
                checkExpr(args.get(0), env);
                return "sab";
                
            case "range":
                if (args.size() != 2) {
                    errorWithHint("'range' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use range(start, end) to create an array of numbers from start to end");
                }
                String rangeStartType = checkExpr(args.get(0), env);
                String rangeEndType = checkExpr(args.get(1), env);
                if (!isNumeric(rangeStartType) || !isNumeric(rangeEndType)) {
                    errorWithHint("'range' requires numeric arguments, got '" + rangeStartType + "' and '" + rangeEndType + "'.", call.getSourceLocation(),
                                 "Both arguments must be numbers: range(1, 10) creates [1, 2, 3, ..., 10]");
                }
                return "num[]";
                
            case "sleep":
                if (args.size() != 1) {
                    errorWithHint("'sleep' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use sleep(milliseconds) to pause execution for a specified time");
                }
                String sleepType = checkExpr(args.get(0), env);
                if (!isNumeric(sleepType)) {
                    errorWithHint("'sleep' requires a numeric argument, got '" + sleepType + "'.", call.getSourceLocation(),
                                 "Sleep duration must be a number in milliseconds: sleep(1000) pauses for 1 second");
                }
                return "kaam";
                
            default:
                errorWithHint("Unknown native function: " + name, call.getSourceLocation(),
                             "Check the function name for typos or refer to the DhrLang documentation for available functions");
                return "unknown";
        }
    }

    private boolean isNumeric(String type) {
        return type.equals("num") || type.equals("duo");
    }

    private boolean isAssignable(String from, String to) {
        if (from == null || to == null) return false;
        return from.equals(to) ||
                (from.equals("num") && to.equals("duo")) ||
                (from.equals("unknown[]") && to.endsWith("[]")); 
    }
    
    
    private void errorWithHint(String message, SourceLocation location, String hint) {
        if (errorReporter != null) {
            errorReporter.error(location, message, hint);
        } else {
            throw new TypeException(message);
        }
    }
    
    private void validateModifiers(VarDecl field) {
        Set<Modifier> modifiers = field.getModifiers();
        
        long accessModifierCount = modifiers.stream()
            .filter(m -> m == Modifier.PUBLIC || m == Modifier.PRIVATE || m == Modifier.PROTECTED)
            .count();
        
        if (accessModifierCount > 1) {
            errorWithHint("Field '" + field.getName() + "' cannot have multiple access modifiers.", field.getSourceLocation(),
                         "Use only one access modifier: public, private, or protected");
        }
}
    
    private void validateModifiers(FunctionDecl function) {
        Set<Modifier> modifiers = function.getModifiers();
        long accessModifierCount = modifiers.stream()
            .filter(m -> m == Modifier.PUBLIC || m == Modifier.PRIVATE || m == Modifier.PROTECTED)
            .count();
        
        if (accessModifierCount > 1) {
            errorWithHint("Method '" + function.getName() + "' cannot have multiple access modifiers.", function.getSourceLocation(),
                         "Use only one access modifier: public, private, or protected");
        }
        
        if (modifiers.contains(Modifier.ABSTRACT)) {
            if (modifiers.contains(Modifier.STATIC)) {
                errorWithHint("Method '" + function.getName() + "' cannot be both abstract and static.", function.getSourceLocation(),
                             "Abstract methods cannot be static - remove either 'abstract' or 'static'");
            }
            if (modifiers.contains(Modifier.PRIVATE)) {
                errorWithHint("Method '" + function.getName() + "' cannot be both abstract and private.", function.getSourceLocation(),
                             "Abstract methods must be overridable - use public or protected instead of private");
            }
            if (currentClass != null && !currentClass.isAbstract()) {
                errorWithHint("Abstract method '" + function.getName() + "' can only be declared in an abstract class.", function.getSourceLocation(),
                             "Declare the class as abstract or provide an implementation for the method");
            }
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
    
    private void validateAbstractClass(ClassDecl klass) {
        boolean hasAbstractMethods = false;
        
        for (FunctionDecl method : klass.getFunctions()) {
            if (method.hasModifier(Modifier.ABSTRACT)) {
                hasAbstractMethods = true;
                
                if (method.getBody() != null && !method.getBody().getStatements().isEmpty()) {
                    errorWithHint("Abstract method '" + method.getName() + "' cannot have a method body.", method.getSourceLocation(),
                                 "Abstract methods should only have a declaration - remove the method body { ... }");
                }
                
                if (method.hasModifier(Modifier.PRIVATE)) {
                    errorWithHint("Abstract method '" + method.getName() + "' cannot be private.", method.getSourceLocation(),
                                 "Abstract methods must be overridable - use public or protected instead of private");
                }
                
                if (method.hasModifier(Modifier.STATIC)) {
                    errorWithHint("Abstract method '" + method.getName() + "' cannot be static.", method.getSourceLocation(),
                                 "Abstract methods cannot be static - remove the static modifier");
                }
            }
        }
        
        if (hasAbstractMethods && !klass.isAbstract()) {
            errorWithHint("Class '" + klass.getName() + "' must be declared abstract because it contains abstract methods.", klass.getSourceLocation(),
                         "Add 'abstract' before the class declaration or provide implementations for all abstract methods");
        }
        
        if (klass.getSuperclass() != null && !klass.isAbstract()) {
            validateAbstractMethodImplementation(klass);
        }
    }
    
    private void validateAbstractMethodImplementation(ClassDecl klass) {
        ClassDecl superclass = classRegistry.get(klass.getSuperclass().getName().getLexeme());
        if (superclass == null) return;
        
        List<FunctionDecl> abstractMethods = collectAbstractMethods(superclass);
        
        for (FunctionDecl abstractMethod : abstractMethods) {
            FunctionDecl implementation = klass.findMethod(abstractMethod.getName());
            if (implementation == null) {
                errorWithHint("Class '" + klass.getName() + "' must implement abstract method '" + 
                      abstractMethod.getName() + "' from class '" + superclass.getName() + "'.", klass.getSourceLocation(),
                      "Add implementation: " + abstractMethod.getReturnType() + " " + abstractMethod.getName() + "() { ... }");
            } else if (implementation.hasModifier(Modifier.ABSTRACT)) {
                if (!klass.isAbstract()) {
                    errorWithHint("Class '" + klass.getName() + "' must be declared abstract or implement abstract method '" + 
                          abstractMethod.getName() + "'.", klass.getSourceLocation(),
                          "Either add 'abstract' to the class or provide implementation for " + abstractMethod.getName());
                }
            }
        }
    }
    
    private List<FunctionDecl> collectAbstractMethods(ClassDecl klass) {
        List<FunctionDecl> abstractMethods = new ArrayList<>();
        
        for (FunctionDecl method : klass.getFunctions()) {
            if (method.hasModifier(Modifier.ABSTRACT)) {
                abstractMethods.add(method);
            }
        }
        
        if (klass.getSuperclass() != null) {
            ClassDecl superclass = classRegistry.get(klass.getSuperclass().getName().getLexeme());
            if (superclass != null) {
                abstractMethods.addAll(collectAbstractMethods(superclass));
            }
        }
        
        return abstractMethods;
    }
}