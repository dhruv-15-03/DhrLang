package dhrlang.typechecker;

import dhrlang.ast.*;
import dhrlang.error.ErrorReporter;
import dhrlang.error.ErrorCode;
import dhrlang.error.SourceLocation;
import dhrlang.lexer.TokenType;
import dhrlang.interpreter.GenericTypeManager;
import dhrlang.stdlib.NativeSignatures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;

public class TypeChecker {
    private final Map<String, ClassDecl> classRegistry = new HashMap<>();
    private final Map<String, InterfaceDecl> interfaceRegistry = new HashMap<>();
    private final Map<String, TypeEnvironment> classEnvironments = new HashMap<>();
    private final Map<String, TypeEnvironment> interfaceEnvironments = new HashMap<>();
    private final TypeEnvironment globals = new TypeEnvironment();
    private final GenericTypeManager genericTypeManager = new GenericTypeManager();
    private ClassDecl currentClass = null;
    private InterfaceDecl currentInterface = null;
    private String currentFunctionReturnType = null;
    private boolean currentFunctionIsStatic = false;
    private boolean inLoop = false;
    private Set<String> currentTypeParameters = new HashSet<>();
    private ErrorReporter errorReporter;
    private Set<String> nonNullVars = new HashSet<>();
    private final Map<String, Map<String,String>> genericInstanceBindings = new HashMap<>();

    public TypeChecker() {
        this.errorReporter = null;
    }
    
    public TypeChecker(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
    }

    public void check(Program program) {
    // Instrumentation reset
    exprTypeCache.clear();
    exprCacheMissCount = 0L;
    checkStartNanos = System.nanoTime();
        for (InterfaceDecl interfaceDecl : program.getInterfaces()) {
            if (interfaceRegistry.containsKey(interfaceDecl.getName())) {
                errorWithHint("Interface '" + interfaceDecl.getName() + "' is already defined.", interfaceDecl.getSourceLocation(),
                             "Each interface name must be unique. Rename one of the interfaces or check for duplicates", ErrorCode.REDECLARATION);
            }
            interfaceRegistry.put(interfaceDecl.getName(), interfaceDecl);
            globals.define(interfaceDecl.getName(), interfaceDecl.getName());
        }
        
        for (ClassDecl classDecl : program.getClasses()) {
            if (classRegistry.containsKey(classDecl.getName())) {
                errorWithHint("Class '" + classDecl.getName() + "' is already defined.", classDecl.getSourceLocation(),
                             "Each class name must be unique. Rename one of the classes or check for duplicates", ErrorCode.REDECLARATION);
            }
            if (interfaceRegistry.containsKey(classDecl.getName())) {
                errorWithHint("Name '" + classDecl.getName() + "' is already used by an interface.", classDecl.getSourceLocation(),
                             "Classes and interfaces cannot have the same name. Choose a different name", ErrorCode.REDECLARATION);
            }
            classRegistry.put(classDecl.getName(), classDecl);
            globals.define(classDecl.getName(), classDecl.getName());
        }

        for (InterfaceDecl interfaceDecl : program.getInterfaces()) {
            resolveInterface(interfaceDecl);
        }

        for (ClassDecl classDecl : program.getClasses()) {
            resolveClass(classDecl);
        }

        for (InterfaceDecl interfaceDecl : program.getInterfaces()) {
            checkInterfaceBody(interfaceDecl);
        }

        for (ClassDecl classDecl : program.getClasses()) {
            checkClassBody(classDecl);
        }

        // Future generic validation hook: invoke validation routines for generic declarations (no-op for non-generic)
        for (ClassDecl classDecl : program.getClasses()) {
            if (classDecl instanceof GenericClassDecl gc) {
                validateGenericClass(gc);
            }
        }
        for (InterfaceDecl interfaceDecl : program.getInterfaces()) {
            if (interfaceDecl instanceof GenericInterfaceDecl gi) {
                validateGenericInterface(gi);
            }
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
            if (program.getClasses().isEmpty()) {
                // No classes at all - show first non-interface declaration or start of file
                SourceLocation loc = !program.getInterfaces().isEmpty() ? 
                    program.getInterfaces().get(0).getSourceLocation() : 
                    new SourceLocation("file", 1, 1);
                errorWithHint("Entry point error: No classes found. Please define at least one class with 'static kaam main()' method.", loc,
                             "Add a class with main method: class Main { static kaam main() { ... } }");
            } else {
                // Classes exist but no main method - point to first class
                SourceLocation loc = program.getClasses().get(0).getSourceLocation();
                errorWithHint("Entry point error: No static main method found. Please define 'static kaam main()' in any class.", loc,
                             "Add a main method: 'static kaam main() { ... }' in any class to serve as the program entry point");
            }
    } else if (!mainMethod.getParameters().isEmpty()) {
            errorWithHint("Entry point 'main' should not have parameters.", mainMethod.getSourceLocation(),
                         "The main method should be defined as: 'static kaam main()' without any parameters");
        }
    lastElapsedNanos = System.nanoTime() - checkStartNanos;
    if(Boolean.getBoolean("dhrlang.profile")){
        System.out.println(getPerformanceSummary());
    }
    }

    // Cache computed descriptor types for expressions within a single type-check pass.
    // This avoids repeated string->TypeDesc parsing and repeated dispatch for identical subtrees.
    private final Map<Expression, TypeDesc> exprTypeCache = new IdentityHashMap<>();
    // Instrumentation counters (reset per check(Program))
    private long checkStartNanos = 0L;
    private long lastElapsedNanos = 0L;
    private long exprCacheMissCount = 0L;

    private TypeDesc checkExprDesc(Expression expr, TypeEnvironment env) {
        TypeDesc cached = exprTypeCache.get(expr);
        if (cached != null) return cached;
        String raw = checkExpr(expr, env); // delegate to existing logic returning string form
        TypeDesc desc = TypeDesc.parse(raw);
        exprTypeCache.put(expr, desc);
        exprCacheMissCount++;
        return desc;
    }

    public long getLastCheckElapsedNanos(){ return lastElapsedNanos; }
    public long getExprCacheMissCount(){ return exprCacheMissCount; }
    public String getPerformanceSummary(){
        double ms = lastElapsedNanos/1_000_000.0;
        return String.format("TypeChecker Performance: %.3f ms (%,d ns), expr cache misses=%d", ms, lastElapsedNanos, exprCacheMissCount);
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
                             "Make sure the superclass is defined before this class, or check for typos in the class name", ErrorCode.UNDECLARED_IDENTIFIER);
            } else {
                parentEnv = resolveClass(classRegistry.get(superclassName));
            }
        }

        TypeEnvironment classEnv = new TypeEnvironment(parentEnv);
        
    for (VarDecl field : klass.getVariables()) {
            if (classEnv.getLocalFields().containsKey(field.getName())) {
                errorWithHint("Field '" + field.getName() + "' is already defined in class '" + klass.getName() + "'.", field.getSourceLocation(),
                             "Rename the field or remove the duplicate - each field name must be unique within a class", ErrorCode.REDECLARATION);
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
                             "Rename the method or remove the duplicate - method overloading is not supported in DhrLang", ErrorCode.REDECLARATION);
            }
            
            classEnv.defineFunction(methodName, new FunctionSignature(paramTypes, func.getReturnType()));
        }

        classEnvironments.put(klass.getName(), classEnv);
        klass.setBeingResolved(false);
        return classEnv;
    }
    
    private TypeEnvironment resolveInterface(InterfaceDecl interfaceDecl) {
        if (interfaceEnvironments.containsKey(interfaceDecl.getName())) {
            return interfaceEnvironments.get(interfaceDecl.getName());
        }
        if (interfaceDecl.isBeingResolved()) {
            errorWithHint("Cyclic interface dependency involving interface " + interfaceDecl.getName(), interfaceDecl.getSourceLocation(),
                         "Remove circular dependencies - interfaces cannot depend on each other in a cycle");
        }

        interfaceDecl.setBeingResolved(true);

        TypeEnvironment interfaceEnv = new TypeEnvironment(globals);
        
        for (FunctionDecl method : interfaceDecl.getMethods()) {
            List<String> paramTypes = method.getParameters().stream()
                    .map(VarDecl::getType)
                    .collect(Collectors.toList());
            
            String methodName = method.getName();
            if (interfaceEnv.getLocalFunctions().containsKey(methodName)) {
                errorWithHint("Method '" + methodName + "' is already defined in interface '" + interfaceDecl.getName() + "'.", method.getSourceLocation(),
                             "Rename the method or remove the duplicate - each method signature must be unique within an interface", ErrorCode.REDECLARATION);
            }
            
            interfaceEnv.defineFunction(methodName, new FunctionSignature(paramTypes, method.getReturnType()));
        }

        interfaceEnvironments.put(interfaceDecl.getName(), interfaceEnv);
        interfaceDecl.setBeingResolved(false);
        return interfaceEnv;
    }
    
    private void checkInterfaceBody(InterfaceDecl interfaceDecl) {
        this.currentInterface = interfaceDecl;
        
        for (FunctionDecl method : interfaceDecl.getMethods()) {
            validateInterfaceMethod(method);
        }
        
        this.currentInterface = null;
    }
    
    private void validateInterfaceMethod(FunctionDecl method) {
        if (method.getBody() != null && !method.getBody().getStatements().isEmpty()) {
            errorWithHint("Interface method '" + method.getName() + "' cannot have a method body.", method.getSourceLocation(),
                         "Interface methods should only declare signatures - remove the method body { ... }");
        }
        
        if (method.hasModifier(Modifier.PRIVATE)) {
            errorWithHint("Interface method '" + method.getName() + "' cannot be private.", method.getSourceLocation(),
                         "Interface methods are implicitly public - remove the 'private' modifier");
        }
        
        if (method.hasModifier(Modifier.STATIC)) {
            errorWithHint("Interface method '" + method.getName() + "' cannot be static.", method.getSourceLocation(),
                         "Interface methods cannot be static - remove the 'static' modifier");
        }
        
        if (method.hasModifier(Modifier.FINAL)) {
            errorWithHint("Interface method '" + method.getName() + "' cannot be final.", method.getSourceLocation(),
                         "Interface methods are meant to be implemented - remove the 'final' modifier");
        }
    }

    private void checkClassBody(ClassDecl klass) {
        this.currentClass = klass;
        
        // Set current type parameters for generic classes
        if (klass instanceof GenericClassDecl) {
            GenericClassDecl genericClass = (GenericClassDecl) klass;
            for (TypeParameter typeParam : genericClass.getTypeParameters()) {
                currentTypeParameters.add(typeParam.getNameString());
            }
        }
        
        TypeEnvironment classEnv = classEnvironments.get(klass.getName());

        // Step 6: Static field forward reference and cycle detection
        // Build ordered list and name->index for static fields of this class
        List<VarDecl> staticFields = new ArrayList<>();
        Map<String, Integer> staticIndex = new HashMap<>();
        for (int i = 0; i < klass.getVariables().size(); i++) {
            VarDecl f = klass.getVariables().get(i);
            if (f.hasModifier(Modifier.STATIC)) {
                staticIndex.put(f.getName(), staticFields.size());
                staticFields.add(f);
            }
        }
        // Collect dependencies from each static field's initializer: same-class static reads
        List<Set<String>> dependencies = new ArrayList<>();
        for (int i = 0; i < staticFields.size(); i++) dependencies.add(new HashSet<>());
        for (int i = 0; i < staticFields.size(); i++) {
            VarDecl f = staticFields.get(i);
            if (f.getInitializer() != null) {
                Set<String> refs = collectSameClassStaticFieldReads(f.getInitializer(), klass.getName(), staticIndex.keySet());
                // Record and check illegal forward references (reads of later-declared fields)
                for (String ref : refs) {
                    Integer j = staticIndex.get(ref);
                    if (j != null) {
                        dependencies.get(i).add(ref);
                        if (j > i) {
                            // Forward read detected
                            errorWithHint(
                                "Illegal forward reference to static field '" + ref + "' in initializer of '" + f.getName() + "'.",
                                f.getSourceLocation(),
                                "Reorder fields or remove forward reference to later-declared static field",
                                ErrorCode.STATIC_FORWARD_REFERENCE
                            );
                        }
                    }
                }
            }
        }
        // Detect dependency cycles among static fields (regardless of order)
        // Simple DFS for cycles over indices
        int n = staticFields.size();
        int[] color = new int[n]; // 0=unvisited,1=visiting,2=done
        List<Integer> stack = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (color[i] == 0) {
                detectStaticCycleDFS(i, staticFields, staticIndex, dependencies, color, stack);
            }
        }

        for (VarDecl field : klass.getVariables()) {
            // Validate field type early
            validateTypeReference(field.getType(), field.getSourceLocation());
            validateModifiers(field);
            
            if (field.getInitializer() != null) {
                TypeDesc from = checkExprDesc(field.getInitializer(), classEnv);
                TypeDesc to = TypeDesc.parse(field.getType());
                if (!isAssignable(from, to)) {
                  errorWithHint("Type mismatch in field '" + field.getName() + "': Cannot assign type '" + 
                      from + "' to field of type '" + field.getType() + "'.", field.getSourceLocation(),
                      buildTypeMismatchHint(from, to, "field initializer"), ErrorCode.TYPE_MISMATCH);
                }
            }
        }
        
        for (FunctionDecl function : klass.getFunctions()) {
            validateModifiers(function);
            checkFunction(function, classEnv);
        }
        
        validateAbstractClass(klass);
        validateInterfaceImplementations(klass);
        
        // Clear current type parameters
        currentTypeParameters.clear();
        this.currentClass = null;
    }

    // DFS helper to report a single cycle per back-edge discovered
    private void detectStaticCycleDFS(int u,
                                      List<VarDecl> staticFields,
                                      Map<String,Integer> staticIndex,
                                      List<Set<String>> dependencies,
                                      int[] color,
                                      List<Integer> stack){
        color[u] = 1; // visiting
        stack.add(u);
        for(String depName : dependencies.get(u)){
            Integer v = staticIndex.get(depName);
            if(v==null) continue;
            if(color[v]==0){
                detectStaticCycleDFS(v, staticFields, staticIndex, dependencies, color, stack);
            } else if(color[v]==1){
                // Found a cycle; reconstruct cycle path from v to u
                int startIdx = stack.indexOf(v);
                if(startIdx>=0){
                    List<String> cycleNames = new ArrayList<>();
                    for(int k=startIdx;k<stack.size();k++){
                        cycleNames.add(staticFields.get(stack.get(k)).getName());
                    }
                    cycleNames.add(staticFields.get(v).getName()); // close the loop
                    String path = String.join(" -> ", cycleNames);
                    VarDecl at = staticFields.get(u);
                    errorWithHint(
                        "Static initialization cycle detected: " + path + ".",
                        at.getSourceLocation(),
                        "Break the cycle by removing circular references or using a method call",
                        ErrorCode.STATIC_INIT_CYCLE
                    );
                }
            }
        }
        stack.remove(stack.size()-1);
        color[u] = 2;
    }

    // Collect reads of same-class static fields via A.x or unqualified? Here, static reads appear as StaticAccessExpr of the same class
    private Set<String> collectSameClassStaticFieldReads(Expression expr, String className, java.util.Set<String> staticNames){
        Set<String> refs = new HashSet<>();
        collectStaticReads(expr, className, staticNames, refs);
        return refs;
    }

    private void collectStaticReads(Expression expr, String className, java.util.Set<String> staticNames, Set<String> out){
        if(expr == null) return;
        if(expr instanceof StaticAccessExpr sae){
            String cls = sae.className.getName().getLexeme();
            if(className.equals(cls)){
                out.add(sae.memberName.getLexeme());
            }
            return; // StaticAccessExpr has no additional children beyond identifiers
        }
        if(expr instanceof VariableExpr ve){
            String name = ve.getName().getLexeme();
            if(staticNames.contains(name)){
                out.add(name);
            }
            return;
        }
        // Traverse common expression forms for potential nested StaticAccessExpr
        if(expr instanceof BinaryExpr b){
            collectStaticReads(b.getLeft(), className, staticNames, out);
            collectStaticReads(b.getRight(), className, staticNames, out);
        } else if(expr instanceof UnaryExpr u){
            collectStaticReads(u.getRight(), className, staticNames, out);
        } else if(expr instanceof CallExpr c){
            collectStaticReads(c.getCallee(), className, staticNames, out);
            for(Expression a : c.getArguments()) collectStaticReads(a, className, staticNames, out);
        } else if(expr instanceof GetExpr g){
            collectStaticReads(g.getObject(), className, staticNames, out);
        } else if(expr instanceof SetExpr s){
            collectStaticReads(s.getObject(), className, staticNames, out);
            collectStaticReads(s.getValue(), className, staticNames, out);
        } else if(expr instanceof AssignmentExpr a){
            collectStaticReads(a.getValue(), className, staticNames, out);
        } else if(expr instanceof ArrayExpr arr){
            for(Expression e : arr.getElements()) collectStaticReads(e, className, staticNames, out);
        } else if(expr instanceof IndexExpr idx){
            collectStaticReads(idx.getObject(), className, staticNames, out);
            collectStaticReads(idx.getIndex(), className, staticNames, out);
        } else if(expr instanceof IndexAssignExpr ia){
            collectStaticReads(ia.getObject(), className, staticNames, out);
            collectStaticReads(ia.getIndex(), className, staticNames, out);
            collectStaticReads(ia.getValue(), className, staticNames, out);
        } else if(expr instanceof NewExpr ne){
            for(Expression a : ne.getArguments()) collectStaticReads(a, className, staticNames, out);
        } else if(expr instanceof NewArrayExpr na){
            if(na.getSizes()!=null){
                for(Expression d : na.getSizes()) collectStaticReads(d, className, staticNames, out);
            } else if(na.getSize()!=null){
                collectStaticReads(na.getSize(), className, staticNames, out);
            }
        } else if(expr instanceof PostfixIncrementExpr pi){
            collectStaticReads(pi.getTarget(), className, staticNames, out);
        } else if(expr instanceof PrefixIncrementExpr pri){
            collectStaticReads(pri.getTarget(), className, staticNames, out);
        } else if(expr instanceof StaticAssignExpr sas){
            // static assignment in initializer could reference same-class static in value
            collectStaticReads(sas.value, className, staticNames, out);
        } // VariableExpr, LiteralExpr, ThisExpr, SuperExpr have no static reads to collect here
    }
    
    private void validateInterfaceImplementations(ClassDecl klass) {
        for (VariableExpr interfaceExpr : klass.getInterfaces()) {
            String interfaceName = interfaceExpr.getName().getLexeme();
            
            if (!interfaceRegistry.containsKey(interfaceName)) {
                errorWithHint("Undefined interface '" + interfaceName + "'.", interfaceExpr.getSourceLocation(),
                             "Make sure the interface is defined before implementing it, or check for typos in the interface name", ErrorCode.UNDECLARED_IDENTIFIER);
                continue;
            }
            
            InterfaceDecl interfaceDecl = interfaceRegistry.get(interfaceName);
            
            for (FunctionDecl interfaceMethod : interfaceDecl.getMethods()) {
                FunctionDecl classMethod = klass.findMethod(interfaceMethod.getName());
                
                if (classMethod == null) {
                    errorWithHint("Class '" + klass.getName() + "' must implement method '" + interfaceMethod.getName() + 
                          "' from interface '" + interfaceName + "'.", klass.getSourceLocation(),
                          "Add implementation: " + interfaceMethod.getReturnType() + " " + interfaceMethod.getName() + "(" + 
                          getParameterSignature(interfaceMethod) + ") { ... }");
                    continue;
                }
                
                if (!interfaceMethod.getReturnType().equals(classMethod.getReturnType())) {
                    errorWithHint("Implementation of '" + interfaceMethod.getName() + "' has mismatched return type. Expected '" +
                          interfaceMethod.getReturnType() + "', got '" + classMethod.getReturnType() + "'.", classMethod.getSourceLocation(),
                          "Change the return type to match the interface: " + interfaceMethod.getReturnType() + " " + interfaceMethod.getName() + "(...)");
                }
                
                if (!parametersMatch(interfaceMethod.getParameters(), classMethod.getParameters())) {
                    errorWithHint("Implementation of '" + interfaceMethod.getName() + "' has mismatched parameters.", classMethod.getSourceLocation(),
                          "Method parameters must match exactly: " + interfaceMethod.getReturnType() + " " + interfaceMethod.getName() + 
                          "(" + getParameterSignature(interfaceMethod) + ")");
                }
                
                if (classMethod.hasModifier(Modifier.OVERRIDE)) {
                } else {
                    // Could add a warning here for missing @Override annotation
                }
            }
        }
    }
    
    private String getParameterSignature(FunctionDecl function) {
        return function.getParameters().stream()
                .map(param -> param.getType() + " " + param.getName())
                .collect(Collectors.joining(", "));
    }
    
    private boolean parametersMatch(List<VarDecl> interfaceParams, List<VarDecl> classParams) {
        if (interfaceParams.size() != classParams.size()) {
            return false;
        }
        
        for (int i = 0; i < interfaceParams.size(); i++) {
            if (!interfaceParams.get(i).getType().equals(classParams.get(i).getType())) {
                return false;
            }
        }
        
        return true;
    }

    private void checkFunction(FunctionDecl function, TypeEnvironment env) {
        TypeEnvironment local = new TypeEnvironment(env);
    // Reset non-null facts at function entry
    nonNullVars = new HashSet<>();
        boolean prevStatic = currentFunctionIsStatic;
        currentFunctionIsStatic = function.hasModifier(Modifier.STATIC);
        
        if (currentClass != null) {
            local.define("this", currentClass.getName());
        }
        
        for (VarDecl param : function.getParameters()) {
            validateTypeReference(param.getType(), param.getSourceLocation());
            if (local.getLocalFields().containsKey(param.getName())) {
                errorWithHint("Parameter '" + param.getName() + "' is already defined in function '" + function.getName() + "'.", param.getSourceLocation(),
                                 "Rename the parameter - each parameter name must be unique within a function", ErrorCode.REDECLARATION);
            }
            local.define(param.getName(), param.getType());
        }

        String previousReturnType = currentFunctionReturnType;
        currentFunctionReturnType = function.getReturnType();

        if (function.getBody() != null) {
            checkBlock(function.getBody(), local);
            if(errorReporter!=null){
                for (VarDecl param : function.getParameters()) {
                    Boolean used = local.getLocalUsageMap().get(param.getName());
                    if(used!=null && !used){
                        errorReporter.warning(param.getSourceLocation(), "Parameter '"+param.getName()+"' is never used.", "Remove it or use it inside the function", ErrorCode.UNUSED_PARAMETER);
                    }
                }
            }
        }

        currentFunctionReturnType = previousReturnType;
        currentFunctionIsStatic = prevStatic;
    }

    private void checkBlock(Block block, TypeEnvironment env) {
        TypeEnvironment blockEnv = new TypeEnvironment(env);
        boolean unreachable = false;
        // Empty block (no statements) warning (skip if it's function body already handled upstream?)
        if(block.getStatements().isEmpty() && errorReporter!=null){
            errorReporter.warning(block.getSourceLocation(), "Empty block.", "Remove or add statements", ErrorCode.EMPTY_BLOCK);
        }
        for (Statement stmt : block.getStatements()) {
            if(unreachable){
                if(errorReporter!=null){
                    errorReporter.warning(stmt.getSourceLocation(), "Unreachable code.", "Remove or refactor code after a terminating statement", ErrorCode.UNREACHABLE_CODE);
                }
                continue;
            }
            checkStatement(stmt, blockEnv);
            if(stmt instanceof ReturnStmt || stmt instanceof BreakStmt || stmt instanceof ContinueStmt){
                unreachable = true;
            }
        }
        if(errorReporter!=null){
            // Dead store detection: any local variable with a write not subsequently read in this block scope.
            for(var entry: blockEnv.getLocalReadSinceWriteMap().entrySet()){
                String var = entry.getKey();
                boolean read = entry.getValue();
                if(!read){
                    Boolean everRead = blockEnv.getLocalUsageMap().get(var);
                    if(everRead!=null && everRead){
                        var loc = blockEnv.getLocalLastWriteLocations().get(var);
                        if(loc==null) loc = blockEnv.getVariableLocation(var);
                        errorReporter.warning(loc, "Value written to variable '"+var+"' is never read.", "Remove the write or use the value before scope ends", ErrorCode.DEAD_STORE);
                    }
                }
            }
        }
        if(errorReporter!=null){
            for(var e: blockEnv.getLocalUsageMap().entrySet()){
                if(!e.getValue() && !e.getKey().equals("this")){
                    var loc = blockEnv.getVariableLocation(e.getKey());
                    if(loc==null) loc = block.getSourceLocation();
                    errorReporter.warning(loc, "Variable '"+e.getKey()+"' declared but never used.", "Remove it or use it in logic", ErrorCode.UNUSED_VARIABLE);
                }
            }
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
                             "Rename the variable or remove the duplicate - each variable name must be unique within a scope", ErrorCode.REDECLARATION);
        } else if (env.exists(stmt.getName())) {
            if(errorReporter!=null){
                errorReporter.warning(stmt.getSourceLocation(), "Variable '"+stmt.getName()+"' shadows a variable from an outer scope.", "Consider renaming to avoid confusion", ErrorCode.VARIABLE_SHADOWING);
            }
        }

    // Validate declared type existence
    validateTypeReference(stmt.getType(), stmt.getSourceLocation());
    if (stmt.getInitializer() != null) {
            TypeDesc from = checkExprDesc(stmt.getInitializer(), env);
            TypeDesc to = TypeDesc.parse(stmt.getType());
            if (!isAssignable(from, to)) {
                errorWithHint("Type mismatch: Cannot assign type '" + from + 
                      "' to variable '" + stmt.getName() + "' of type '" + stmt.getType() + "'.", 
                      stmt.getSourceLocation(), buildTypeMismatchHint(from, to, "variable initializer"), ErrorCode.TYPE_MISMATCH);
            }
        }
        
    env.define(stmt.getName(), stmt.getType());
    env.recordLocation(stmt.getName(), stmt.getSourceLocation());
    env.recordWrite(stmt.getName(), stmt.getSourceLocation());
    }

    private void checkIfStmt(IfStmt stmt, TypeEnvironment env) {
        String conditionType = checkExpr(stmt.getCondition(), env);
        if (!"kya".equals(conditionType)) {
            errorWithHint("If condition must be a boolean ('kya'), got '" + conditionType + "'.", stmt.getSourceLocation(),
                    "If conditions require boolean expressions: if (x > 5) or if (isValid)");
        }
        if (errorReporter != null) {
            if (stmt.getCondition() instanceof LiteralExpr le && le.getValue() instanceof Boolean) {
                errorReporter.warning(stmt.getCondition().getSourceLocation(), "Constant condition.", "Remove or refactor constant 'if' condition", ErrorCode.CONSTANT_CONDITION);
            } else if (stmt.getCondition() instanceof BinaryExpr be) {
                if (be.getLeft() instanceof LiteralExpr && be.getRight() instanceof LiteralExpr) {
                    errorReporter.warning(stmt.getCondition().getSourceLocation(), "Constant condition.", "Remove or refactor constant 'if' condition", ErrorCode.CONSTANT_CONDITION);
                }
            }
        }
        // Flow-sensitive merging logic
        Set<String> entry = new HashSet<>(nonNullVars);
        Set<String> thenEntry = new HashSet<>(entry);
        Set<String> elseEntry = new HashSet<>(entry);
        if (stmt.getCondition() instanceof BinaryExpr be) {
            String varName = extractNullComparedVariable(be);
            if (varName != null) {
                TokenType op = be.getOperator().getType();
                if (op == TokenType.NEQ) { // var != null => then: var non-null
                    thenEntry.add(varName);
                } else if (op == TokenType.EQUALITY) { // var == null => else: var non-null
                    elseEntry.add(varName);
                }
            }
        }
        // Execute then branch
        nonNullVars = new HashSet<>(thenEntry);
        checkStatement(stmt.getThenBranch(), env);
        Set<String> thenExit = new HashSet<>(nonNullVars);
        Set<String> elseExit = null;
        if (stmt.getElseBranch() != null) {
            nonNullVars = new HashSet<>(elseEntry);
            checkStatement(stmt.getElseBranch(), env);
            elseExit = new HashSet<>(nonNullVars);
        }
        // Merge
        if (elseExit != null) {
            thenExit.retainAll(elseExit); // intersection
            nonNullVars = thenExit;
        } else {
            nonNullVars = entry; // no safe refinement without else
        }
    }

    private boolean isNullLiteral(Expression expr){
        if(expr instanceof LiteralExpr le){
            return le.getValue() == null; 
        }
        return false;
    }

    private String extractNullComparedVariable(BinaryExpr be){
        Expression l = be.getLeft();
        Expression r = be.getRight();
        if(l instanceof VariableExpr && isNullLiteral(r)) return ((VariableExpr) l).getName().getLexeme();
        if(r instanceof VariableExpr && isNullLiteral(l)) return ((VariableExpr) r).getName().getLexeme();
        return null;
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
            TypeDesc ret = checkExprDesc(stmt.getValue(), env);
            TypeDesc expected = TypeDesc.parse(currentFunctionReturnType);
            if (!isAssignable(ret, expected)) {
                errorWithHint("Cannot return '" + ret + "' from a function expecting '" + currentFunctionReturnType + "'.", 
                             stmt.getSourceLocation(),
                             buildTypeMismatchHint(ret, expected, "return statement"), ErrorCode.TYPE_MISMATCH);
            }
        }
    }

    private String buildTypeMismatchHint(TypeDesc from, TypeDesc to, String context){
        if(from.isArray() && to.isArray()){
            return "Ensure element types align in " + context + ": expected elements of '"+to.element+"'";
        }
        if(from.isNumeric() && to.isNumeric()){
            return "Convert numeric type or adjust expected type: numeric widening only supports num->duo";
        }
        if(to.kind==TypeKind.ANY){
            return "'any' accepts any value – this mismatch indicates an internal issue; report this";
        }
    if(from.kind==TypeKind.CLASS && to.kind==TypeKind.CLASS && !from.name.equals(to.name)){
            return "Use an instance of '"+to.name+"' or a compatible subtype";
        }
        if(!from.typeArgs.isEmpty() || !to.typeArgs.isEmpty()){
            if(!from.name.equals(to.name)) return "Generic base types differ; use '"+to.name+"'";
            if(from.typeArgs.size()!=to.typeArgs.size()) return "Generic arity mismatch: expected "+to.typeArgs.size()+" type argument(s)";
            for(int i=0;i<Math.min(from.typeArgs.size(), to.typeArgs.size());i++){
                if(!from.typeArgs.get(i).equals(to.typeArgs.get(i))) return "Type argument "+(i+1)+" mismatch: expected '"+to.typeArgs.get(i)+"'";
            }
        }
        return "Provide a value of type '"+to+"'";
    }

    private void checkTryStmt(TryStmt stmt, TypeEnvironment env) {
        checkStatement(stmt.getTryBlock(), env);
        
        for (CatchClause catchClause : stmt.getCatchClauses()) {
            TypeEnvironment catchEnv = new TypeEnvironment(env);
            
            // Validate exception type
            String exceptionType = catchClause.getExceptionType();
            if (!isValidExceptionType(exceptionType)) {
                errorWithHint("Invalid exception type '" + exceptionType + "'. " +
                                "Use 'any' to catch all exceptions or a specific exception class name.",
                                catchClause.getSourceLocation()!=null?catchClause.getSourceLocation():stmt.getSourceLocation(),
                                "Valid exception types: any, ArithmeticException, IndexOutOfBoundsException, TypeException, NullPointerException");
            }
            
            // Define the exception parameter with the appropriate type
            catchEnv.define(catchClause.getParameter(), exceptionType.equals("any") ? "sab" : exceptionType);
            checkStatement(catchClause.getBody(), catchEnv);
        }
        
        if (stmt.getFinallyBlock() != null) {
            checkStatement(stmt.getFinallyBlock(), env);
        }
    }
    
    private boolean isValidExceptionType(String exceptionType) {
        return "any".equals(exceptionType) || 
               "ArithmeticException".equals(exceptionType) ||
               "IndexOutOfBoundsException".equals(exceptionType) ||
               "TypeException".equals(exceptionType) ||
               "NullPointerException".equals(exceptionType) ||
               "DhrException".equals(exceptionType) ||
               "Error".equals(exceptionType);
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

        TypeDesc elementDesc = checkExprDesc(expr.getElements().get(0), env);

        for (int i = 1; i < expr.getElements().size(); i++) {
            TypeDesc currentDesc = checkExprDesc(expr.getElements().get(i), env);
            if (!isAssignable(currentDesc, elementDesc)) {
                errorWithHint("Array elements must all have the same type. Expected '" + elementDesc +
                        "' but found '" + currentDesc + "' at index " + i + ".", expr.getSourceLocation(),
                        buildTypeMismatchHint(currentDesc, elementDesc, "array literal element"), ErrorCode.TYPE_MISMATCH);
            }
        }

        return elementDesc + "[]";
    }

    private String checkIndex(IndexExpr expr, TypeEnvironment env) {
        TypeDesc objectDesc = checkExprDesc(expr.getObject(), env);
        TypeDesc indexDesc = checkExprDesc(expr.getIndex(), env);
        String objectType = objectDesc.toString();
        String indexType = indexDesc.toString();

        if (!objectDesc.isArray()) {
            errorWithHint("Can only index arrays, got type '" + objectType + "'.", expr.getSourceLocation(),
                         "Array indexing syntax: myArray[0] - ensure the variable is an array type like num[] or sab[]");
        }

        if (!indexDesc.equals(TypeDesc.num())) {
            errorWithHint("Array index must be a number, got '" + indexType + "'.", expr.getSourceLocation(),
                         "Array indices must be integers: array[0], array[i], or array[count-1]");
        }
        // Constant index bounds detection
        if(expr.getIndex() instanceof LiteralExpr lit && lit.getValue() instanceof Number n){
            long idx = n.longValue();
            if(idx < 0){
                errorWithHint("Negative array index " + idx + " is out of bounds.", expr.getSourceLocation(),
                             "Use a non-negative index between 0 and length-1", ErrorCode.BOUNDS_VIOLATION);
            } else {
                if(expr.getObject() instanceof ArrayExpr arrLit){
                    int len = arrLit.getElements().size();
                    if(idx >= len){
                        errorWithHint("Array index " + idx + " out of bounds for literal length " + len + ".", expr.getSourceLocation(),
                                     "Valid indices: 0 to " + (len-1), ErrorCode.BOUNDS_VIOLATION);
                    }
                } else if(expr.getObject() instanceof NewArrayExpr na && na.getSize() instanceof LiteralExpr sz && sz.getValue() instanceof Number sn){
                    long size = ((Number) sn).longValue();
                    if(idx >= size){
                        errorWithHint("Array index " + idx + " out of bounds for size " + size + ".", expr.getSourceLocation(),
                                     "Valid indices: 0 to " + (size-1), ErrorCode.BOUNDS_VIOLATION);
                    }
                }
            }
        }
    if(!objectDesc.isArray()) return "unknown"; // keep pipeline alive if already reported
    return objectDesc.element.toString();
    }

    private String checkIndexAssign(IndexAssignExpr expr, TypeEnvironment env) {
        TypeDesc objectDesc = checkExprDesc(expr.getObject(), env);
        TypeDesc indexDesc = checkExprDesc(expr.getIndex(), env);
        TypeDesc valueDesc = checkExprDesc(expr.getValue(), env);
        String objectType = objectDesc.toString();

        if (!objectType.endsWith("[]")) {
            errorWithHint("Can only assign to array elements, got type '" + objectType + "'.", expr.getSourceLocation(),
                         "Array assignment syntax: myArray[index] = value - ensure the target is an array");
        }

        if (!indexDesc.equals(TypeDesc.num())) {
            errorWithHint("Array index must be a number, got '" + indexDesc + "'.", expr.getSourceLocation(),
                         "Array indices must be integers: array[0] = value or array[i] = value");
        }
        // Constant index bounds detection for assignments
        if(expr.getIndex() instanceof LiteralExpr lit && lit.getValue() instanceof Number n){
            long idx = n.longValue();
            if(idx < 0){
                errorWithHint("Negative array index " + idx + " is out of bounds.", expr.getSourceLocation(),
                             "Use a non-negative index between 0 and length-1", ErrorCode.BOUNDS_VIOLATION);
            } else {
                if(expr.getObject() instanceof ArrayExpr arrLit){
                    int len = arrLit.getElements().size();
                    if(idx >= len){
                        errorWithHint("Array index " + idx + " out of bounds for literal length " + len + ".", expr.getSourceLocation(),
                                     "Valid indices: 0 to " + (len-1), ErrorCode.BOUNDS_VIOLATION);
                    }
                } else if(expr.getObject() instanceof NewArrayExpr na && na.getSize() instanceof LiteralExpr sz && sz.getValue() instanceof Number sn){
                    long size = ((Number) sn).longValue();
                    if(idx >= size){
                        errorWithHint("Array index " + idx + " out of bounds for size " + size + ".", expr.getSourceLocation(),
                                     "Valid indices: 0 to " + (size-1), ErrorCode.BOUNDS_VIOLATION);
                    }
                }
            }
        }

        String elementType = objectType.substring(0, objectType.length() - 2);
        TypeDesc elementDesc = TypeDesc.parse(elementType);
        if (!isAssignable(valueDesc, elementDesc)) {
            errorWithHint("Cannot assign '" + valueDesc + "' to array of '" + elementType + "'.", expr.getSourceLocation(),
                         buildTypeMismatchHint(valueDesc, elementDesc, "array element assignment"), ErrorCode.TYPE_MISMATCH);
        }

        return valueDesc.toString();
    }

    private String checkPostfixIncrement(PostfixIncrementExpr expr, TypeEnvironment env) {
        return checkIncrementTarget(expr.getTarget(), env, "postfix increment/decrement");
    }

    private String checkPrefixIncrement(PrefixIncrementExpr expr, TypeEnvironment env) {
        return checkIncrementTarget(expr.getTarget(), env, "prefix increment/decrement");
    }

    private String checkIncrementTarget(Expression target, TypeEnvironment env, String operation) {
        TypeDesc targetDesc;
        String targetType;
        if (target instanceof VariableExpr varExpr) {
            targetType = checkVariable(varExpr, env);
            targetDesc = TypeDesc.parse(targetType);
        } else if (target instanceof GetExpr getExpr) {
            targetType = checkGet(getExpr, env);
            targetDesc = TypeDesc.parse(targetType);
        } else if (target instanceof IndexExpr indexExpr) {
            targetType = checkIndex(indexExpr, env);
            targetDesc = TypeDesc.parse(targetType);
        } else {
            errorWithHint("Invalid " + operation + " target. Must be a variable, property, or array element.", target.getSourceLocation(),
                    "Use " + operation + " on variables, object properties, or array elements: x++, obj.count++, arr[i]++");
            return "unknown";
        }
        if (!targetDesc.isNumeric()) {
            errorWithHint("Can only apply " + operation + " to numeric values, got '" + targetDesc + "'.", target.getSourceLocation(),
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
        String name = expr.getName().getLexeme();
        try {
            return env.get(name);
        } catch (TypeException e) {
            // If inside a non-static class method, allow implicit access to fields via 'this'
            if (currentClass != null && !currentFunctionIsStatic) {
                // Look up field on current class
                String baseType = currentClass.getName();
                TypeEnvironment classEnv = classEnvironments.get(baseType);
                if (classEnv != null && classEnv.getLocalFields().containsKey(name)) {
                    // Enforce access modifier
                    VarDecl fieldDecl = currentClass.getVariables().stream().filter(v -> v.getName().equals(name)).findFirst().orElse(null);
                    if (fieldDecl != null && !isAccessible(currentClass, currentClass, fieldDecl.getModifiers())) {
                        errorWithHint("Cannot access field '" + name + "' due to access modifier.", expr.getSourceLocation(),
                                     "Use a public/protected field or access within allowed scope", ErrorCode.ACCESS_MODIFIER);
                        return "unknown";
                    }
                    String fieldType = classEnv.getLocalFields().get(name);
                    // If 'this' is a generic instantiation, substitute type parameters in field type
                    // Our type for 'this' in env is the class name; reconstruct potential instantiation from locals if available
                    try {
                        String thisType = env.get("this");
                        if (thisType != null && thisType.contains("<")) {
                            fieldType = substituteTypeParameters(fieldType, thisType);
                        }
                    } catch (Exception ignored) { }
                    return fieldType;
                }
            }
            errorWithHint("Undefined variable '" + name + "'.", expr.getSourceLocation(),
                         "Make sure the variable is declared before use: num x = 42; or check for typos in variable name", ErrorCode.UNDECLARED_IDENTIFIER);
            return "unknown";
        }
    }

    private String checkUnary(UnaryExpr expr, TypeEnvironment env) {
        TypeDesc rightDesc = checkExprDesc(expr.getRight(), env);
        String rightType = rightDesc.toString();
        TokenType op = expr.getOperator().getType();
        
        if (op == TokenType.MINUS) {
            if (!rightDesc.isNumeric()) {
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
        // Special short-circuit handling for logical AND to propagate non-null facts from left into right
        if(expr.getOperator().getType()==TokenType.AND){
            String leftTypePre = checkExpr(expr.getLeft(), env);
            if(!leftTypePre.equals("kya")){
                errorWithHint("Left operand of logical operator must be boolean, got '" + leftTypePre + "'.", expr.getSourceLocation(),
                                 "Logical operators (&&, ||) require boolean values: true && false");
            }
            // If pattern var != null on left, add refinement for right evaluation
            if(expr.getLeft() instanceof BinaryExpr be){
                TokenType opLeft = be.getOperator().getType();
                if(opLeft==TokenType.NEQ){
                    String varName = extractNullComparedVariable(be);
                    if(varName!=null){
                        nonNullVars.add(varName);
                    }
                }
            }
            String rightTypeAfter = checkExpr(expr.getRight(), env);
            if(!rightTypeAfter.equals("kya")){
                errorWithHint("Right operand of logical operator must be boolean, got '" + rightTypeAfter + "'.", expr.getSourceLocation(),
                                 "Logical operators (&&, ||) require boolean values: true && false");
            }
            return "kya";
        }
        if(expr.getOperator().getType()==TokenType.OR){
            String leftTypePre = checkExpr(expr.getLeft(), env);
            if(!leftTypePre.equals("kya")){
                errorWithHint("Left operand of logical operator must be boolean, got '" + leftTypePre + "'.", expr.getSourceLocation(),
                                 "Logical operators (&&, ||) require boolean values: true && false");
            }
            Set<String> saved = nonNullVars;
            boolean refined = false;
            if(expr.getLeft() instanceof BinaryExpr be){
                TokenType opLeft = be.getOperator().getType();
                if(opLeft==TokenType.EQUALITY){
                    String varName = extractNullComparedVariable(be);
                    if(varName!=null){
                        Set<String> thenSet = new HashSet<>(saved);
                        thenSet.add(varName);
                        nonNullVars = thenSet;
                        refined = true;
                    }
                }
            }
            String rightTypeAfter = checkExpr(expr.getRight(), env);
            if(!rightTypeAfter.equals("kya")){
                errorWithHint("Right operand of logical operator must be boolean, got '" + rightTypeAfter + "'.", expr.getSourceLocation(),
                                 "Logical operators (&&, ||) require boolean values: true && false");
            }
            if(refined) nonNullVars = saved; 
            return "kya";
        }
    TypeDesc leftDesc = checkExprDesc(expr.getLeft(), env);
    TypeDesc rightDesc = checkExprDesc(expr.getRight(), env);
    String leftType = leftDesc.toString();
    String rightType = rightDesc.toString();
        TokenType op = expr.getOperator().getType();
        
        switch (op) {
            case PLUS:
                if (leftType.equals("sab") || rightType.equals("sab")) {
                    return "sab"; 
                }
            case MINUS:
            case STAR:
            case MOD:
                if (!leftDesc.isNumeric() || !rightDesc.isNumeric()) {
                    String opName = op == TokenType.PLUS ? "addition/concatenation" : "arithmetic";
                    errorWithHint("Operands for " + opName + " must be numbers (or strings for '+'), got '" + 
                          leftType + "' and '" + rightType + "'.", expr.getSourceLocation(),
                          "Use numeric values for arithmetic operations, or strings for concatenation with '+'");
                }
                return (leftDesc.kind==TypeKind.DUO || rightDesc.kind==TypeKind.DUO) ? "duo" : "num";
                
            case SLASH:
                if (!leftDesc.isNumeric() || !rightDesc.isNumeric()) {
                    errorWithHint("Operands for division must be numbers, got '" + leftType + "' and '" + rightType + "'.", expr.getSourceLocation(),
                                 "Division requires numeric operands like: 10 / 2 or 5.0 / 2.5");
                }
                return "duo"; 
                
            case GREATER:
            case GEQ:
            case LESS:
            case LEQ:
                if (!leftDesc.isNumeric() || !rightDesc.isNumeric()) {
                    errorWithHint("Operands for comparison must be numbers, got '" + leftType + "' and '" + rightType + "'.", expr.getSourceLocation(),
                                 "Comparison operators (<, >, <=, >=) work with numbers: x > 5 or price <= 100.0");
                }
                return "kya";
                
            case EQUALITY:
            case NEQ:
                if (!isAssignable(leftDesc, rightDesc) && !isAssignable(rightDesc, leftDesc)) {
                    errorWithHint("Cannot compare incompatible types: '" + leftDesc + "' and '" + rightDesc + "'.", expr.getSourceLocation(),
                            buildTypeMismatchHint(leftDesc, rightDesc, "equality comparison"), ErrorCode.TYPE_MISMATCH);
                }
                // Redundant null check warning (x != null or x == null) if fact already known
                if(expr.getLeft() instanceof VariableExpr || expr.getRight() instanceof VariableExpr){
                    String varName = extractNullComparedVariable(expr);
                    if(varName!=null){
                        boolean isInequality = expr.getOperator().getType()==TokenType.NEQ;
                        if(isInequality && nonNullVars.contains(varName) && errorReporter!=null){
                            errorReporter.warning(expr.getSourceLocation(), "Redundant null check: variable '"+varName+"' already known non-null.", "Remove unnecessary '!= null'", ErrorCode.REDUNDANT_NULL_CHECK);
                        }
                    }
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
            // If in non-static instance context, allow implicit 'this.field = ...'
            if (currentClass != null && !currentFunctionIsStatic) {
                String baseType = currentClass.getName();
                TypeEnvironment classEnv = classEnvironments.get(baseType);
                if (classEnv != null && classEnv.getLocalFields().containsKey(varName)) {
                    String fieldType = classEnv.getLocalFields().get(varName);
                    try {
                        String thisType = env.get("this");
                        if (thisType != null && thisType.contains("<")) fieldType = substituteTypeParameters(fieldType, thisType);
                    } catch (Exception ignored) { }
                    TypeDesc valueDesc = checkExprDesc(expr.getValue(), env);
                    TypeDesc targetDesc = TypeDesc.parse(fieldType);
                    if (!isAssignable(valueDesc, targetDesc)) {
                        errorWithHint("Cannot assign type '" + valueDesc + "' to field '" + varName + "' of type '" + fieldType + "'.",
                                     expr.getSourceLocation(), buildTypeMismatchHint(valueDesc, targetDesc, "field assignment"), ErrorCode.TYPE_MISMATCH);
                    }
                    return valueDesc.toString();
                }
            }
            errorWithHint("Cannot assign to undefined variable '" + varName + "'.", expr.getSourceLocation(),
                         "Declare the variable first: num " + varName + " = 0; then assign: " + varName + " = value;", ErrorCode.UNDECLARED_IDENTIFIER);
            return "unknown"; 
        }
        // Dead store: previous value overwritten without read
        if(env.hadUnreadWrite(varName) && errorReporter!=null){
            dhrlang.error.SourceLocation prevLoc = env.getLastWriteLocation(varName);
            if(prevLoc==null) prevLoc = expr.getSourceLocation();
            errorReporter.warning(prevLoc, "Value written to variable '"+varName+"' is never read before being overwritten.", "Remove the previous assignment or use the value", ErrorCode.DEAD_STORE);
        }
        env.recordWrite(varName, expr.getSourceLocation());
        TypeDesc valueDesc = checkExprDesc(expr.getValue(), env);
        TypeDesc targetDesc = TypeDesc.parse(varType);
        if (!isAssignable(valueDesc, targetDesc)) {
            errorWithHint("Cannot assign type '" + valueDesc + "' to variable '" + varName + "' of type '" + varType + "'.",
                         expr.getSourceLocation(), buildTypeMismatchHint(valueDesc, targetDesc, "assignment"), ErrorCode.TYPE_MISMATCH);
        }
        return valueDesc.toString();
    }

    private String checkNew(NewExpr expr, TypeEnvironment env) {
        String className = expr.getClassName();
        String baseClassName = className;
        
        if (className.contains("<")) {
            baseClassName = className.substring(0, className.indexOf('<'));
        }

        // Built-in synthetic 'Error' behaves like a class for instantiation/catching.
        if ("Error".equals(baseClassName)) {
            // Validate no generic args and no constructor args (language design: Error() takes none)
            if (className.contains("<")) {
                errorWithHint("'Error' is not generic.", expr.getSourceLocation(), "Use: new Error() without type arguments");
            }
            if (!expr.getArguments().isEmpty()) {
                errorWithHint("Error() takes no arguments.", expr.getSourceLocation(), "Remove arguments: new Error()", ErrorCode.TYPE_MISMATCH);
            }
            return "Error"; // treat as its own class type
        }
        
        if (!classRegistry.containsKey(baseClassName)) {
            errorWithHint("Cannot instantiate unknown class '" + baseClassName + "'.", expr.getSourceLocation(),
                         "Make sure the class is defined before creating instances: class " + baseClassName + " { ... }");
            return "unknown";
        }
        
        ClassDecl classDecl = classRegistry.get(baseClassName);
        
    if (classDecl instanceof GenericClassDecl && className.contains("<")) {
            try {
                validateGenericInstantiation((GenericClassDecl) classDecl, className, expr.getSourceLocation());
            } catch (TypeException e) {
        errorWithHint(e.getMessage(), expr.getSourceLocation(), 
                 "Ensure type arguments match the class's generic parameters.", ErrorCode.GENERIC_ARITY);
            }
        } else if (!(classDecl instanceof GenericClassDecl) && className.contains("<")) {
        errorWithHint("Class '" + baseClassName + "' is not generic but type arguments were provided.", 
             expr.getSourceLocation(), "Remove type arguments: new " + baseClassName + "()", ErrorCode.GENERIC_ARITY);
        } else if (classDecl instanceof GenericClassDecl && !className.contains("<")) {
        errorWithHint("Generic class '" + baseClassName + "' requires type arguments.", 
             expr.getSourceLocation(), "Provide type arguments: new " + baseClassName + "<Type>()", ErrorCode.GENERIC_ARITY);
        }
        
        FunctionDecl init = classDecl.findMethod("init");
        
        if (init != null) {
            if (className.contains("<")) {
                // Handle generic constructor call
                checkGenericConstructorCall(className, init, expr.getArguments(), env, expr.getSourceLocation());
            } else {
                checkFunctionArguments("init", init.getParameters(), expr.getArguments(), env, expr.getSourceLocation());
            }
        } else if (!expr.getArguments().isEmpty()) {
            errorWithHint("Class '" + className + "' has no 'init' constructor and cannot be called with arguments.", expr.getSourceLocation(),
                         "Remove arguments: new " + className + "(); or add an init method to the class");
        }
        
        return className;
    }

    private String checkNewArray(NewArrayExpr expr, TypeEnvironment env) {
        String elementType = expr.getElementType();
        validateTypeReference(elementType, expr.getSourceLocation());
        // Validate each dimension
        List<Expression> dims = expr.getSizes();
        if(dims==null || dims.isEmpty()){
            errorWithHint("Array creation requires at least one dimension.", expr.getSourceLocation(),
                          "Use: new num[capacity] or multi-d: new num[rows][cols]");
            return elementType + "[]";
        }
        for(Expression dimExpr : dims){
            TypeDesc sizeDesc = checkExprDesc(dimExpr, env);
            if (!sizeDesc.isNumeric()) {
                errorWithHint("Array size must be numeric, got '" + sizeDesc + "'.", expr.getSourceLocation(),
                        "Array size must be a number: new num[10] or new sab[count]", ErrorCode.BOUNDS_VIOLATION);
            } else if (dimExpr instanceof LiteralExpr le) {
                Object lit = le.getValue();
                if (lit instanceof Number n && n.longValue() < 0) {
                    errorWithHint("Array size cannot be negative (" + n + ").", expr.getSourceLocation(),
                            "Use a non-negative size: new num[0] for empty array", ErrorCode.BOUNDS_VIOLATION);
                }
            }
        }
        StringBuilder sb = new StringBuilder(elementType);
        for(int i=0;i<dims.size();i++) sb.append("[]");
        return sb.toString();
    }
    
    private void checkGenericConstructorCall(String className, FunctionDecl init, List<Expression> args, TypeEnvironment env, SourceLocation location) {
        String baseClassName = extractBaseType(className);
        ClassDecl classDecl = classRegistry.get(baseClassName);
        
        if (!(classDecl instanceof GenericClassDecl)) {
            checkFunctionArguments("init", init.getParameters(), args, env, location);
            return;
        }
        
        String[] typeArguments = extractTypeArguments(className);
        GenericClassDecl genericClassDecl = (GenericClassDecl) classDecl;
        
        List<VarDecl> parameters = init.getParameters();
        if (args.size() != parameters.size()) {
            errorWithHint("Constructor 'init' expects " + parameters.size() + 
                         " arguments, but got " + args.size() + ".", location,
                         "Check the constructor definition and provide the correct number of arguments");
            return;
        }
        
        String[] typeParameters = genericClassDecl.getTypeParameters().stream()
                                                  .map(TypeParameter::getNameString)
                                                  .toArray(String[]::new);
        
        for (int i = 0; i < args.size(); i++) {
            String argType = checkExpr(args.get(i), env);
            String expectedType = resolveGenericReturnType(parameters.get(i).getType(), typeParameters, typeArguments);
            
            if (!isAssignable(TypeDesc.parse(argType), TypeDesc.parse(expectedType))) {
                errorWithHint("Argument " + (i + 1) + " for constructor 'init' should be '" + expectedType + 
                             "', but got '" + argType + "'.", args.get(i).getSourceLocation(),
                             "Pass an argument of type '" + expectedType + "' for parameter " + (i + 1));
            }
        }
    }

    private String checkGet(GetExpr expr, TypeEnvironment env) {
        String objectType = checkExpr(expr.getObject(), env);
        if (objectType.equals("null")) {
            errorWithHint("Cannot access property on null value.", expr.getSourceLocation(),
                         "Ensure the expression before '.' is not null (add a null check)", ErrorCode.NULL_DEREFERENCE);
            return "unknown";
        }
        // Suppress potential future null warnings if variable proven non-null
        if(expr.getObject() instanceof VariableExpr v){
            // If objectType were 'null' we'd have returned already; tracking reserved for future enhancements
            if(!nonNullVars.contains(v.getName().getLexeme()) && errorReporter!=null){
                // Heuristic: if original static type is a class (not primitive) and not proven non-null, warn
                String name = v.getName().getLexeme();
                try {
                    String staticType = env.get(name);
                    if(!isPrimitive(staticType) && !staticType.endsWith("[]")){
                        errorReporter.warning(expr.getSourceLocation(), "Possible null dereference of '"+name+"'.", "Ensure '"+name+"' is checked for null before property access", ErrorCode.POSSIBLE_NULL_DEREFERENCE);
                    }
                } catch (TypeException ignored) {}
            }
        }
        String propName = expr.getName().getLexeme();
        if (objectType.endsWith("[]") && propName.equals("length")) {
            return "num";
        }
        if (objectType.equals("sab") && propName.equals("length")) {
            return "num";
        }
        
        // Handle built-in string methods for sab type
        if (objectType.equals("sab") && isBuiltInStringMethod(propName)) {
            return "method";
        }

        // Handle generic types: Container<num> -> Container
        String baseType = extractBaseType(objectType);
        if("unknown".equals(objectType) && baseType.contains("<")) {
            String possible = extractBaseType(baseType);
            if(classEnvironments.containsKey(possible)) baseType = possible;
        }
        TypeEnvironment instanceEnv = classEnvironments.get(baseType);
        if (instanceEnv == null) {
            // If this is a generic instantiation whose base type exists, treat as instance
            if(objectType.contains("<") && classEnvironments.containsKey(baseType)) {
                instanceEnv = classEnvironments.get(baseType);
            } else {
                errorWithHint("Can only access properties on class instances, got type '" + objectType + "'.", expr.getSourceLocation(),
                             "Property access syntax: object.property - ensure the object is a class instance");
                return "unknown";
            }
        }
        
        try {
            String fieldType = instanceEnv.get(propName);
            ClassDecl owner = classRegistry.get(baseType);
            if (owner != null) {
                VarDecl fieldDecl = owner.getVariables().stream().filter(v -> v.getName().equals(propName)).findFirst().orElse(null);
                if (fieldDecl != null && !isAccessible(currentClass, owner, fieldDecl.getModifiers())) {
                    errorWithHint("Cannot access field '" + propName + "' of class '" + baseType + "' due to access modifier.", expr.getSourceLocation(),
                             "Use a public/protected member or access within the same class", ErrorCode.ACCESS_MODIFIER);
                }
            }
            // Substitute generic parameters if this is a generic instantiation
            if(objectType.contains("<")) {
                fieldType = substituteTypeParameters(fieldType, objectType);
            }
            return fieldType;
        } catch (TypeException fieldError) {
            try {
                if (objectType.contains("<")) {
                    String ret = checkGenericMethodCall(objectType, propName, new ArrayList<>(), env, expr.getSourceLocation());
                    enforceInstanceMethodAccess(baseType, propName, expr.getSourceLocation());
                    return ret;
                } else {
                    instanceEnv.getFunction(propName);
                    enforceInstanceMethodAccess(baseType, propName, expr.getSourceLocation());
                    return "method";
                }
            } catch (TypeException funcError) {
                errorWithHint("Property '" + propName + "' not found on class '" + objectType + "'.", expr.getSourceLocation(),
                             "Check the property name and ensure it's defined in the class");
                return "unknown";
            }
        }
    }

    private String checkSet(SetExpr expr, TypeEnvironment env) {
    String objectType = checkExpr(expr.getObject(), env);
    String baseType = extractBaseType(objectType);
    if("unknown".equals(objectType) && baseType.contains("<")) {
        String possible = extractBaseType(baseType);
        if(classEnvironments.containsKey(possible)) baseType = possible;
    }
    TypeEnvironment instanceEnv = classEnvironments.get(baseType);
        if (instanceEnv == null) {
            if(objectType.contains("<") && classEnvironments.containsKey(baseType)) {
                instanceEnv = classEnvironments.get(baseType);
            } else {
                errorWithHint("Can only set properties on class instances, got type '" + objectType + "'.", expr.getSourceLocation(),
                             "Property assignment syntax: object.field = value - ensure the object is a class instance");
                return "unknown";
            }
        }
        
        String fieldName = expr.getName().getLexeme();
        String fieldType;
        try {
            fieldType = instanceEnv.get(fieldName);
            ClassDecl owner = classRegistry.get(baseType);
            if (owner != null) {
                VarDecl fieldDecl = owner.getVariables().stream().filter(v -> v.getName().equals(fieldName)).findFirst().orElse(null);
                if (fieldDecl != null && !isAccessible(currentClass, owner, fieldDecl.getModifiers())) {
                    errorWithHint("Cannot assign to field '" + fieldName + "' of class '" + baseType + "' due to access modifier.", expr.getSourceLocation(),
                                 "Use a public/protected field or provide a setter method", ErrorCode.ACCESS_MODIFIER);
                }
            }
        } catch (TypeException e) {
            errorWithHint("Field '" + fieldName + "' not found on class '" + objectType + "'.", expr.getSourceLocation(),
                         "Check the field name and ensure it's defined in the class");
            return "unknown";
        }
        if(objectType.contains("<")) {
            fieldType = substituteTypeParameters(fieldType, objectType);
        }
        
        TypeDesc valueDesc = checkExprDesc(expr.getValue(), env);
        TypeDesc fieldDesc = TypeDesc.parse(fieldType);
        if (!isAssignable(valueDesc, fieldDesc)) {
            errorWithHint("Cannot assign type '" + valueDesc + "' to field '" + fieldName + "' of type '" + fieldType + "'.", expr.getSourceLocation(),
                         buildTypeMismatchHint(valueDesc, fieldDesc, "field assignment"), ErrorCode.TYPE_MISMATCH);
        }
        return valueDesc.toString();
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
                         "Make sure the class is defined before accessing static members", ErrorCode.UNDECLARED_IDENTIFIER);
            return "unknown";
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
             "Check the member name and ensure it's declared as static", ErrorCode.UNDECLARED_IDENTIFIER);
        return "unknown";
    }
    
    private String checkStaticAssign(StaticAssignExpr expr, TypeEnvironment env) {
        String className = expr.className.getName().getLexeme();
        String memberName = expr.memberName.getLexeme();
        
        if (!classRegistry.containsKey(className)) {
            errorWithHint("Unknown class '" + className + "' in static assignment.", expr.getSourceLocation(),
                         "Make sure the class is defined before assigning to static fields", ErrorCode.UNDECLARED_IDENTIFIER);
            return "unknown";
        }
        
        ClassDecl classDecl = classRegistry.get(className);
        
        for (VarDecl field : classDecl.getVariables()) {
            if (field.getName().equals(memberName) && field.hasModifier(Modifier.STATIC)) {
                if (!isAccessible(currentClass, classDecl, field.getModifiers())) {
                    errorWithHint("Cannot access private/protected static field '" + memberName + "' from class '" + className + "'.", expr.getSourceLocation(),
                                 "Use public static fields or access from within the same class");
                }
                
                TypeDesc valueDesc = checkExprDesc(expr.value, env);
                TypeDesc fieldDesc = TypeDesc.parse(field.getType());
                if (!isAssignable(valueDesc, fieldDesc)) {
                    errorWithHint("Cannot assign '" + valueDesc + "' to static field '" + memberName + "' of type '" + field.getType() + "'.", expr.getSourceLocation(),
                                 buildTypeMismatchHint(valueDesc, fieldDesc, "static field assignment"), ErrorCode.TYPE_MISMATCH);
                }
                return valueDesc.toString();
            }
        }
        
    errorWithHint("Static field '" + memberName + "' not found in class '" + className + "'.", expr.getSourceLocation(),
             "Check the field name and ensure it's declared as static", ErrorCode.UNDECLARED_IDENTIFIER);
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
                             "Make sure the function is declared before calling it", ErrorCode.UNDECLARED_IDENTIFIER);
                return "unknown";
            }
        } else if (callee instanceof GetExpr) {
            String objectType = checkExpr(((GetExpr) callee).getObject(), env);
            funcName = ((GetExpr) callee).getName().getLexeme();
            
            if (objectType.equals("sab") && isBuiltInStringMethod(funcName)) {
                return checkBuiltInStringMethodCall(funcName, call.getArguments(), env);
            }
            
            if (objectType.contains("<")) {
                return checkGenericMethodCall(objectType, funcName, call.getArguments(), env, call.getSourceLocation());
            }
            
            String baseType = extractBaseType(objectType);
            TypeEnvironment instanceEnv = classEnvironments.get(baseType);
            if (instanceEnv == null) {
                errorWithHint("Can only call methods on class instances, got type '" + objectType + "'.", call.getSourceLocation(),
                             "Method calls syntax: object.method() - ensure the object is a class instance");
                return "unknown";  // Return after error instead of continuing
            }
            
            try {
                signature = instanceEnv.getFunction(funcName);
                enforceInstanceMethodAccess(baseType, funcName, call.getSourceLocation());
            } catch (TypeException e) {
                errorWithHint("Method '" + funcName + "' not found on class '" + baseType + "'.", call.getSourceLocation(),
                             "Check the method name and ensure it's defined in the class");
                return "unknown";
            }
        } else if (callee instanceof SuperExpr) {
            SuperExpr superExpr = (SuperExpr) callee;
            if (currentClass == null || currentClass.getSuperclass() == null) {
                errorWithHint("'super' used incorrectly.", superExpr.getSourceLocation(),
                             "Use 'super' only in classes that extend another class to call parent methods");
                return "unknown";
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
                             "Make sure the class is defined before calling static methods", ErrorCode.UNDECLARED_IDENTIFIER);
                return "unknown";
            }
            
            ClassDecl classDecl = classRegistry.get(className);
            if (classDecl == null) {
                errorWithHint("Class '" + className + "' not found.", call.getSourceLocation(),
                             "Make sure the class is defined before calling static methods", ErrorCode.UNDECLARED_IDENTIFIER);
                return "unknown";
            }
            
            FunctionDecl methodDecl = classDecl.findMethod(funcName);
            if (methodDecl == null) {
                errorWithHint("Static method '" + funcName + "' not found in class '" + className + "'.", call.getSourceLocation(),
                             "Check the method name and ensure it's declared as static in the class", ErrorCode.UNDECLARED_IDENTIFIER);
                return "unknown";
            }
            
            if (!methodDecl.hasModifier(Modifier.STATIC)) {
                errorWithHint("Cannot call non-static method '" + funcName + "' from static context.", call.getSourceLocation(),
                             "Add 'static' modifier to the method or create an instance to call it");
                return "unknown";
            }
            // Enforce access modifiers for static methods as well (private/protected)
            if (!isAccessible(currentClass, classDecl, methodDecl.getModifiers())) {
                errorWithHint("Cannot access private/protected static method '" + funcName + "' from class '" + className + "'.",
                              call.getSourceLocation(),
                              "Use public static methods or access from within the same class / subclass");
                return "unknown";
            }
            
            try {
                signature = classEnv.getFunction(funcName);
            } catch (TypeException e) {
                errorWithHint("Static method '" + funcName + "' not found in class '" + className + "'.", call.getSourceLocation(),
                             "Check the method name and ensure it's declared as static in the class", ErrorCode.UNDECLARED_IDENTIFIER);
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

    private void enforceInstanceMethodAccess(String baseType, String methodName, SourceLocation location) {
        ClassDecl owner = classRegistry.get(baseType);
        if (owner == null) return;
        FunctionDecl methodDecl = owner.getFunctions().stream().filter(m -> m.getName().equals(methodName)).findFirst().orElse(null);
        if (methodDecl == null) return;
        if (!isAccessible(currentClass, owner, methodDecl.getModifiers())) {
            errorWithHint("Cannot access method '" + methodName + "' of class '" + baseType + "' due to access modifier.", location,
                         "Use a public/protected method or call from within the same class / subclass");
        }
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

            TypeDesc argDesc = TypeDesc.parse(argType);
            TypeDesc expectedDesc = TypeDesc.parse(expectedType);

            if (!TypeDesc.assignable(argDesc, expectedDesc)) {
                String hint;
                if (argDesc.isArray() && expectedDesc.isArray()) {
                    hint = "Ensure element types match: expected elements of type '" + expectedDesc.element + "'";
                } else if (argDesc.isNumeric() && expectedDesc.isNumeric()) {
                    hint = "Convert numeric type if needed or adjust parameter type"; // Though widening handled, keep generic message
                } else if (expectedDesc.kind == TypeKind.ANY) {
                    hint = "'any' accepts any type – this should not normally fail; report if reproducible";
                } else {
                    hint = "Pass an argument of type '" + expectedType + "' for parameter " + (i + 1);
                }
                errorWithHint("Argument " + (i + 1) + " for '" + name + "' should be '" + expectedType +
                                "', but got '" + argType + "'.", args.get(i).getSourceLocation(),
                        hint, ErrorCode.TYPE_MISMATCH);
            }
        }
    }

    private boolean isNativeFunction(String name) {
    return NativeSignatures.exists(name);
    }

    private String checkNativeFunction(String name, List<Expression> args, CallExpr call, TypeEnvironment env) {
        dhrlang.stdlib.NativeSignatures.Signature sig = NativeSignatures.get(name);
        if(sig != null) {
            int expected = sig.params.size();
            if(args.size()!=expected) {
                errorWithHint("Native function '"+name+"' expects "+expected+" arguments, got "+args.size()+".", call.getSourceLocation(),
                    "Call '"+name+"' with exactly "+expected+" argument(s)", ErrorCode.NATIVE_ARITY);
            }
        }
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
                TypeDesc absDesc = checkExprDesc(args.get(0), env);
                if (!absDesc.isNumeric()) {
                    errorWithHint("'abs' requires a numeric argument, got '" + absDesc + "'.", call.getSourceLocation(),
                                 "Absolute value only works with numbers: abs(42) or abs(-3.14)");
                }
                return absDesc.toString();
                
            case "sqrt":
            case "floor":
            case "ceil":
                if (args.size() != 1) {
                    errorWithHint("'" + name + "' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use " + name + "(number) to perform the mathematical operation");
                }
                TypeDesc mathUnaryDesc = checkExprDesc(args.get(0), env);
                if (!mathUnaryDesc.isNumeric()) {
                    errorWithHint("'" + name + "' requires a numeric argument, got '" + mathUnaryDesc + "'.", call.getSourceLocation(),
                                 "Mathematical functions only work with numbers: " + name + "(42) or " + name + "(3.14)");
                }
                return name.equals("sqrt") ? "duo" : "num";
                
            case "round":
                if (args.size() != 1) {
                    errorWithHint("'round' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use round(number) to round a number to the nearest integer");
                }
                TypeDesc roundDesc = checkExprDesc(args.get(0), env);
                if (!roundDesc.isNumeric()) {
                    errorWithHint("'round' requires a numeric argument, got '" + roundDesc + "'.", call.getSourceLocation(),
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
                TypeDesc leftDesc = checkExprDesc(args.get(0), env);
                TypeDesc rightDesc = checkExprDesc(args.get(1), env);
                if (!leftDesc.isNumeric() || !rightDesc.isNumeric()) {
                    errorWithHint("'" + name + "' requires numeric arguments, got '" + leftDesc + "' and '" + rightDesc + "'.", call.getSourceLocation(),
                                 "Both arguments must be numbers: " + name + "(5, 3) or " + name + "(2.5, 1.8)");
                }
                return name.equals("pow") ? "duo" : (leftDesc.kind==TypeKind.DUO || rightDesc.kind==TypeKind.DUO ? "duo" : "num");
                
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
                                 "Use substring(string, startIndex, endIndex) to extract a portion of a string", ErrorCode.NATIVE_ARITY);
                    if(args.size() < 3) return "sab"; 
                }
                String subStrType = checkExpr(args.get(0), env);
                String startType = checkExpr(args.get(1), env);
                String endType = checkExpr(args.get(2), env);
                if (!subStrType.equals("sab")) {
                    errorWithHint("'substring' first argument must be a string, got '" + subStrType + "'.", call.getSourceLocation(),
                                 "Substring requires a string as first argument: substring('hello', 1, 3)");
                }
                    TypeDesc startDesc = TypeDesc.parse(startType);
                    TypeDesc endDesc = TypeDesc.parse(endType);
                    if (!startDesc.isNumeric() || !endDesc.isNumeric()) {
                        errorWithHint("'substring' indices must be numeric, got '" + startDesc + "' and '" + endDesc + "'.", call.getSourceLocation(),
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
                TypeDesc charIndexDesc = TypeDesc.parse(indexType);
                if (!charIndexDesc.isNumeric()) {
                    errorWithHint("'charAt' index must be numeric, got '" + charIndexDesc + "'.", call.getSourceLocation(),
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
                TypeDesc repeatCountDesc = checkExprDesc(args.get(1), env);
                if (!repeatStrType.equals("sab") || !repeatCountDesc.isNumeric()) {
                    errorWithHint("'repeat' requires string and numeric arguments, got '" + repeatStrType + "' and '" + repeatCountDesc + "'.", call.getSourceLocation(),
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
                TypeDesc padLengthDesc = checkExprDesc(args.get(1), env);
                String padCharType = checkExpr(args.get(2), env);
                if (!padStrType.equals("sab") || !padLengthDesc.isNumeric() || !padCharType.equals("sab")) {
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
                TypeDesc mathArgDesc = checkExprDesc(args.get(0), env);
                if (!mathArgDesc.isNumeric()) {
                    errorWithHint("'" + name + "' requires a numeric argument, got '" + mathArgDesc + "'.", call.getSourceLocation(),
                            "Mathematical functions only work with numbers: " + name + "(1.5) or " + name + "(45)");
                }
                return "duo";
                
            case "randomRange":
                if (args.size() != 2) {
                    errorWithHint("'randomRange' expects exactly 2 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use randomRange(min, max) to get a random integer between min and max");
                }
                TypeDesc minDesc = checkExprDesc(args.get(0), env);
                TypeDesc maxDesc = checkExprDesc(args.get(1), env);
                if (!minDesc.isNumeric() || !maxDesc.isNumeric()) {
                    errorWithHint("'randomRange' requires numeric arguments, got '" + minDesc + "' and '" + maxDesc + "'.", call.getSourceLocation(),
                                 "Both arguments must be numbers: randomRange(1, 10) returns integer between 1-10");
                }
                return "num";
                
            case "clamp":
                if (args.size() != 3) {
                    errorWithHint("'clamp' expects exactly 3 arguments, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use clamp(value, min, max) to constrain a value between bounds");
                }
                TypeDesc clampValDesc = checkExprDesc(args.get(0), env);
                TypeDesc clampMinDesc = checkExprDesc(args.get(1), env);
                TypeDesc clampMaxDesc = checkExprDesc(args.get(2), env);
                if (!clampValDesc.isNumeric() || !clampMinDesc.isNumeric() || !clampMaxDesc.isNumeric()) {
                    errorWithHint("'clamp' requires numeric arguments.", call.getSourceLocation(),
                                 "All arguments must be numbers: clamp(15, 0, 10) returns 10");
                }
                return (clampValDesc.kind==TypeKind.DUO || clampMinDesc.kind==TypeKind.DUO || clampMaxDesc.kind==TypeKind.DUO) ? "duo" : "num";
                
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
                TypeDesc startSliceDesc = checkExprDesc(args.get(1), env);
                TypeDesc endSliceDesc = checkExprDesc(args.get(2), env);
                if (!sliceArrType.endsWith("[]") || !startSliceDesc.isNumeric() || !endSliceDesc.isNumeric()) {
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
                TypeDesc fillSizeDesc = checkExprDesc(args.get(1), env);
                if (!fillSizeDesc.isNumeric()) {
                    errorWithHint("'arrayFill' size must be numeric, got '" + fillSizeDesc + "'.", call.getSourceLocation(),
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
                TypeDesc insertArrDesc = TypeDesc.parse(checkExpr(args.get(0), env));
                if (!insertArrDesc.isArray()) {
                    errorWithHint("'arrayInsert' first argument must be an array, got '" + insertArrDesc + "'.", call.getSourceLocation(),
                            "First argument must be an array: arrayInsert(myArray, 2, element)");
                }
                TypeDesc insertIndexDesc = checkExprDesc(args.get(1), env);
                if (!insertIndexDesc.isNumeric()) {
                    errorWithHint("'arrayInsert' index must be numeric, got '" + insertIndexDesc + "'.", call.getSourceLocation(),
                            "Index must be a number: arrayInsert(myArray, 2, element)");
                }
                checkExpr(args.get(2), env);
                return insertArrDesc.toString();

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
                TypeDesc rangeStart = checkExprDesc(args.get(0), env);
                TypeDesc rangeEnd = checkExprDesc(args.get(1), env);
                if (!rangeStart.isNumeric() || !rangeEnd.isNumeric()) {
                    errorWithHint("'range' requires numeric arguments, got '" + rangeStart + "' and '" + rangeEnd + "'.", call.getSourceLocation(),
                            "Both arguments must be numbers: range(1, 10) creates [1, 2, 3, ..., 10]");
                }
                return "num[]";
                
            case "sleep":
                if (args.size() != 1) {
                    errorWithHint("'sleep' expects exactly 1 argument, got " + args.size() + ".", call.getSourceLocation(),
                                 "Use sleep(milliseconds) to pause execution for a specified time");
                }
                TypeDesc sleepDesc = checkExprDesc(args.get(0), env);
                if (!sleepDesc.isNumeric()) {
                    errorWithHint("'sleep' requires a numeric argument, got '" + sleepDesc + "'.", call.getSourceLocation(),
                            "Sleep duration must be a number in milliseconds: sleep(1000) pauses for 1 second");
                }
                return "kaam";
                
            default:
                errorWithHint("Unknown native function: " + name, call.getSourceLocation(),
                             "Check the function name for typos or refer to the DhrLang documentation for available functions", ErrorCode.UNKNOWN_NATIVE);
                return "unknown";
        }
    }


    private boolean isPrimitive(String type) {
        return type.equals("num") || type.equals("duo") || type.equals("sab") || type.equals("kya") || type.equals("ek");
    }
    
    
    private String extractBaseType(String type) {
        if (type.contains("<")) {
            return type.substring(0, type.indexOf('<'));
        }
        return type;
    }
    
   
    private String[] extractTypeArguments(String type) {
        if (!type.contains("<")) {
            return new String[0];
        }
        String typeArgsStr = type.substring(type.indexOf('<') + 1, type.lastIndexOf('>'));
        String[] typeArgs = typeArgsStr.split(",");
        for (int i = 0; i < typeArgs.length; i++) {
            typeArgs[i] = typeArgs[i].trim();
        }
        return typeArgs;
    }
    
    
    private String resolveGenericReturnType(String returnType, String[] typeParameters, String[] typeArguments) {
        if (returnType == null) return "kaam";
        
        for (int i = 0; i < typeParameters.length && i < typeArguments.length; i++) {
            if (returnType.equals(typeParameters[i])) {
                return typeArguments[i];
            }
        }
        
        if (returnType.contains("<")) {
            String baseType = extractBaseType(returnType);
            String[] returnTypeArgs = extractTypeArguments(returnType);
            StringBuilder resolvedType = new StringBuilder(baseType);
            if (returnTypeArgs.length > 0) {
                resolvedType.append("<");
                for (int i = 0; i < returnTypeArgs.length; i++) {
                    if (i > 0) resolvedType.append(", ");
                    resolvedType.append(resolveGenericReturnType(returnTypeArgs[i], typeParameters, typeArguments));
                }
                resolvedType.append(">");
            }
            return resolvedType.toString();
        }
        
        return returnType;
    }
    
    
    private String checkGenericMethodCall(String objectType, String methodName, List<Expression> args, TypeEnvironment env, SourceLocation location) {
        String baseType = extractBaseType(objectType);
        String[] typeArguments = extractTypeArguments(objectType);
        
        if (!classRegistry.containsKey(baseType)) {
            errorWithHint("Unknown class '" + baseType + "' in method call.", location,
                         "Make sure the class is defined before calling methods on it");
            return "unknown";
        }
        
        ClassDecl classDecl = classRegistry.get(baseType);
        FunctionDecl method = classDecl.findMethod(methodName);
        
        if (method == null) {
            errorWithHint("Method '" + methodName + "' not found in class '" + baseType + "'.", location,
                         "Check the method name and ensure it exists in the class");
            return "unknown";
        }
        
        List<VarDecl> parameters = method.getParameters();
        if (args.size() != parameters.size()) {
            errorWithHint("Method '" + methodName + "' expects " + parameters.size() + 
                         " arguments, but got " + args.size() + ".", location,
                         "Check the method definition and provide the correct number of arguments");
            return "unknown";
        }
        
        String[] typeParameters = new String[0];
        if (classDecl instanceof GenericClassDecl) {
            List<TypeParameter> genericParams = ((GenericClassDecl) classDecl).getTypeParameters();
            typeParameters = genericParams.stream().map(TypeParameter::getNameString).toArray(String[]::new);
        }
        
        for (int i = 0; i < args.size(); i++) {
            String argType = checkExpr(args.get(i), env);
            String expectedType = resolveGenericReturnType(parameters.get(i).getType(), typeParameters, typeArguments);
            
            if (!isAssignable(argType, expectedType)) {
                errorWithHint("Argument " + (i + 1) + " for '" + methodName + "' should be '" + expectedType + 
                             "', but got '" + argType + "'.", args.get(i).getSourceLocation(),
                             "Pass an argument of type '" + expectedType + "' for parameter " + (i + 1));
            }
        }
        
        String returnType = method.getReturnType();
        return resolveGenericReturnType(returnType, typeParameters, typeArguments);
    }
    
    
    private void validateTypeReference(String type, SourceLocation location) {
        if (type.endsWith("[]")) {
            String baseType = type.substring(0, type.length() - 2);
            validateTypeReference(baseType, location);
            return;
        }
        
        if (type.contains("<")) {
            String baseType = type.substring(0, type.indexOf('<'));
            
            if (!isPrimitive(baseType) && !classRegistry.containsKey(baseType) && !interfaceRegistry.containsKey(baseType)) {
                errorWithHint("Unknown type '" + baseType + "'.", location,
                             "Make sure the class or interface is defined: class " + baseType + " { ... }");
            }
            
            if (classRegistry.containsKey(baseType) && classRegistry.get(baseType) instanceof GenericClassDecl) {
                try {
                    validateGenericInstantiation((GenericClassDecl) classRegistry.get(baseType), type, location);
                } catch (TypeException e) {
                    errorWithHint(e.getMessage(), location, 
                                 "Check that type arguments match the generic parameters.");
                }
            } else if (classRegistry.containsKey(baseType)) {
                errorWithHint("Class '" + baseType + "' is not generic but type arguments were provided.", location,
                             "Remove type arguments: " + baseType);
            }
            return;
        }
        
        // Allow references to current generic type parameters (e.g., 'T') inside generic classes/interfaces
        if (currentTypeParameters != null && currentTypeParameters.contains(type)) {
            return;
        }
        
    if (!isPrimitive(type) && !classRegistry.containsKey(type) && !interfaceRegistry.containsKey(type)) {
        errorWithHint("Unknown type '" + type + "'.", location,
            "Make sure the type is defined or use a valid primitive type: num, duo, sab, kya, ek");
        }
    }
    
    
    private void validateGenericInstantiation(GenericClassDecl genericClass, String instanceType, SourceLocation location) throws TypeException {
        String typeArgsStr = instanceType.substring(instanceType.indexOf('<') + 1, instanceType.lastIndexOf('>'));
        String[] typeArgs = typeArgsStr.split(",");
        
        for (int i = 0; i < typeArgs.length; i++) {
            typeArgs[i] = typeArgs[i].trim();
        }
        
        List<TypeParameter> typeParameters = genericClass.getTypeParameters();
        
        if (typeArgs.length != typeParameters.size()) {
            throw new TypeException("Generic class '" + genericClass.getName() + "' expects " + 
                                  typeParameters.size() + " type arguments but got " + typeArgs.length + ".");
        }
        
        for (int i = 0; i < typeArgs.length; i++) {
            TypeParameter param = typeParameters.get(i);
            String typeArg = typeArgs[i];
            
            if (!currentTypeParameters.contains(typeArg) && 
                !isPrimitive(typeArg) && 
                !classRegistry.containsKey(typeArg) && 
                !interfaceRegistry.containsKey(typeArg) &&
                !isGenericType(typeArg)) {
                throw new TypeException("Unknown type '" + typeArg + "' used as type argument for parameter '" + 
                                      param.getName() + "'.");
            }

            // Enforce bounds: each bound must be a supertype of the argument (simplified: exact or ANY for now)
            if (param.hasBounds()) {
                for (GenericType bound : param.getBounds()) {
                    String boundName = visitGenericType(bound);
                    TypeDesc argDesc = TypeDesc.parse(typeArg);
                    TypeDesc boundDesc = TypeDesc.parse(boundName);
                    if (!TypeDesc.assignable(argDesc, boundDesc)) {
                        errorWithHint("Type argument '" + typeArg + "' does not satisfy bound '" + boundName + "' for parameter '" + param.getNameString() + "'.",
                                     location,
                                     "Ensure the type argument extends/implements required bound", ErrorCode.BOUNDS_VIOLATION);
                    }
                }
            }
        }
    }
    
    private boolean isGenericType(String type) {
        if (type.contains("<") && type.contains(">")) {
            String baseType = extractBaseType(type);
            return classRegistry.containsKey(baseType) || interfaceRegistry.containsKey(baseType);
        }
        return false;
    }

    private boolean isAssignable(String from, String to) {
    if (from == null || to == null) return false;
    return isAssignable(TypeDesc.parse(from), TypeDesc.parse(to));
    }
    // New descriptor-based assignability (to gradually replace string variant)
    private boolean isAssignable(TypeDesc from, TypeDesc to){
        return TypeDesc.assignable(from,to);
    }

    /* -------------------- Generic Instantiation Support (Foundational) -------------------- */
    /**
     * Obtain (or create) the binding map for an instantiation like Foo<num, sab> mapping T->num, U->sab.
     */
    private Map<String,String> bindingsForInstance(String instanceType){
        if(!instanceType.contains("<")) return Collections.emptyMap();
        return genericInstanceBindings.computeIfAbsent(instanceType, key -> {
            String base = extractBaseType(key);
            ClassDecl cd = classRegistry.get(base);
            if(!(cd instanceof GenericClassDecl g)) return Collections.emptyMap();
            String[] args = extractTypeArguments(key);
            List<TypeParameter> params = g.getTypeParameters();
            if(args.length != params.size()) return Collections.emptyMap(); // validation elsewhere
            Map<String,String> map = new HashMap<>();
            for(int i=0;i<params.size();i++){
                map.put(params.get(i).getNameString(), args[i].trim());
            }
            return map;
        });
    }
    /**
     * Substitute generic parameter occurrences in a type relative to a concrete instantiation.
     * Conservative textual replacement of whole identifiers; nested generics handled recursively by re-parsing segments.
     */
    private String substituteTypeParameters(String originalType, String instanceType){
        if(originalType==null) return null;
        Map<String,String> bindings = bindingsForInstance(instanceType);
        if(bindings.isEmpty()) return originalType;
        String result = originalType;
        for(Map.Entry<String,String> e : bindings.entrySet()){
            String param = e.getKey();
            String arg = e.getValue();
            // Replace standalone param tokens (boundaries: start, end, non-word chars)
            result = result.replaceAll("(?<![A-Za-z0-9_])"+param+"(?![A-Za-z0-9_])", arg);
        }
        return result;
    }
    
    
    private void errorWithHint(String message, SourceLocation location, String hint) { errorWithHint(message, location, hint, null); }
    private void errorWithHint(String message, SourceLocation location, String hint, ErrorCode code) {
        if (errorReporter != null) {
            if(code==null) errorReporter.error(location, message, hint); else errorReporter.error(location, message, hint, code);
        } else { throw new TypeException(message); }
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
    
    
    public String visitGenericType(GenericType genericType) {
        try {
            return genericTypeManager.resolveGenericType(genericType);
        } catch (Exception e) {
            errorWithHint("Failed to resolve generic type '" + genericType + "': " + e.getMessage(), 
                         genericType.getSourceLocation(),
                         "Check that all type parameters are properly declared and in scope");
            return "unknown";
        }
    }
    
    public String visitTypeParameter(TypeParameter typeParameter) {
        return typeParameter.getNameString();
    }
    
    
    private void validateGenericClass(GenericClassDecl genericClass) {
        genericTypeManager.enterContext(genericClass.getName());
        
        try {
            for (TypeParameter typeParam : genericClass.getTypeParameters()) {
                validateTypeParameter(typeParam);
            }
            
            Set<String> paramNames = new java.util.HashSet<>();
            for (TypeParameter typeParam : genericClass.getTypeParameters()) {
                String paramName = typeParam.getNameString();
                if (paramNames.contains(paramName)) {
                    errorWithHint("Duplicate type parameter '" + paramName + "' in class '" + genericClass.getName() + "'.",
                                 typeParam.getSourceLocation(),
                                 "Use unique names for each type parameter: class MyClass<T, U, V>");
                }
                paramNames.add(paramName);
            }
            
        } finally {
            genericTypeManager.exitContext();
        }
    }
    
    
    private void validateGenericInterface(GenericInterfaceDecl genericInterface) {
        genericTypeManager.enterContext(genericInterface.getName());
        
        try {
            for (TypeParameter typeParam : genericInterface.getTypeParameters()) {
                validateTypeParameter(typeParam);
            }
            
            Set<String> paramNames = new java.util.HashSet<>();
            for (TypeParameter typeParam : genericInterface.getTypeParameters()) {
                String paramName = typeParam.getNameString();
                if (paramNames.contains(paramName)) {
                    errorWithHint("Duplicate type parameter '" + paramName + "' in interface '" + genericInterface.getName() + "'.",
                                 typeParam.getSourceLocation(),
                                 "Use unique names for each type parameter: interface MyInterface<T, U, V>");
                }
                paramNames.add(paramName);
            }
            
        } finally {
            genericTypeManager.exitContext();
        }
    }
   
    private void validateTypeParameter(TypeParameter typeParam) {
        for (GenericType bound : typeParam.getBounds()) {
            String boundType = visitGenericType(bound);
            
            if (!isValidType(boundType)) {
                errorWithHint("Type parameter bound '" + boundType + "' is not a valid type.",
                             bound.getSourceLocation(),
                             "Use existing class or interface names as bounds: T extends MyClass");
            }
        }
        
        for (GenericType bound : typeParam.getBounds()) {
            if (bound.getBaseNameString().equals(typeParam.getNameString())) {
                errorWithHint("Type parameter '" + typeParam.getNameString() + "' cannot extend itself.",
                             bound.getSourceLocation(),
                             "Use a different class or interface as bound: T extends Number");
            }
        }
    }
    
    
    private boolean isValidType(String typeName) {
        if (isPrimitive(typeName)) {
            return true;
        }
        
        return classRegistry.containsKey(typeName) || interfaceRegistry.containsKey(typeName);
    }
    
    
    // Removed unused wrapper methods checkClassWithGenerics / checkInterfaceWithGenerics (logic inlined elsewhere)
    
    private boolean isBuiltInStringMethod(String methodName) {
        return switch (methodName) {
            case "length", "charAt", "substring", "indexOf", "toUpperCase", 
                 "toLowerCase", "trim", "startsWith", "endsWith", "equals",
                 "replace", "split", "repeat", "contains" -> true;
            default -> false;
        };
    }
    
    private String checkBuiltInStringMethodCall(String methodName, List<Expression> args, TypeEnvironment env) {
        // Check arguments for each built-in string method
        switch (methodName) {
            case "length" -> {
                if (!args.isEmpty()) {
                    errorWithHint("Method '" + methodName + "' takes no arguments, but got " + args.size() + ".", 
                                 args.get(0).getSourceLocation(), "Remove the arguments: text." + methodName + "()");
                }
                return "num";  // length returns a number
            }
            case "toUpperCase", "toLowerCase", "trim" -> {
                if (!args.isEmpty()) {
                    errorWithHint("Method '" + methodName + "' takes no arguments, but got " + args.size() + ".", 
                                 args.get(0).getSourceLocation(), "Remove the arguments: text." + methodName + "()");
                }
                return "sab";
            }
            case "charAt" -> {
                if (args.size() != 1) {
                    errorWithHint("Method 'charAt' expects 1 argument, but got " + args.size() + ".", 
                                 getArgumentLocation(args), "Use: text.charAt(index)");
                    return "unknown";
                }
                String argType = checkExpr(args.get(0), env);
                if (!"num".equals(argType)) {
                    errorWithHint("charAt index must be a number, got '" + argType + "'.", 
                                 args.get(0).getSourceLocation(), "Use a numeric index: text.charAt(0)");
                }
                return "sab";
            }
            case "substring" -> {
                if (args.size() != 2) {
                    errorWithHint("Method 'substring' expects 2 arguments, but got " + args.size() + ".", 
                                 getArgumentLocation(args), "Use: text.substring(start, end)");
                    return "unknown";
                }
                for (int i = 0; i < args.size(); i++) {
                    String argType = checkExpr(args.get(i), env);
                    if (!"num".equals(argType)) {
                        errorWithHint("substring argument " + (i + 1) + " must be a number, got '" + argType + "'.", 
                                     args.get(i).getSourceLocation(), "Use numeric indices: text.substring(0, 5)");
                    }
                }
                return "sab";
            }
            case "indexOf", "startsWith", "endsWith", "contains" -> {
                if (args.size() != 1) {
                    errorWithHint("Method '" + methodName + "' expects 1 argument, but got " + args.size() + ".", 
                                 getArgumentLocation(args), "Use: text." + methodName + "(\"searchString\")");
                    return "unknown";
                }
                String argType = checkExpr(args.get(0), env);
                if (!"sab".equals(argType)) {
                    errorWithHint(methodName + " argument must be a string, got '" + argType + "'.", 
                                 args.get(0).getSourceLocation(), "Use a string argument: text." + methodName + "(\"search\")");
                }
                return methodName.equals("indexOf") ? "num" : "kya";
            }
            case "replace" -> {
                if (args.size() != 2) {
                    errorWithHint("Method 'replace' expects 2 arguments, but got " + args.size() + ".", 
                                 getArgumentLocation(args), "Use: text.replace(\"target\", \"replacement\")");
                    return "unknown";
                }
                for (int i = 0; i < args.size(); i++) {
                    String argType = checkExpr(args.get(i), env);
                    if (!"sab".equals(argType)) {
                        errorWithHint("replace argument " + (i + 1) + " must be a string, got '" + argType + "'.", 
                                     args.get(i).getSourceLocation(), "Use string arguments: text.replace(\"old\", \"new\")");
                    }
                }
                return "sab";
            }
            case "equals" -> {
                if (args.size() != 1) {
                    errorWithHint("Method 'equals' expects 1 argument, but got " + args.size() + ".", 
                                 getArgumentLocation(args), "Use: text.equals(\"other\")");
                    return "unknown";
                }
                checkExpr(args.get(0), env);
                return "kya";
            }
            default -> {
                return "unknown";
            }
        }
    }
    
    private SourceLocation getArgumentLocation(List<Expression> args) {
        return args.isEmpty() ? null : args.get(0).getSourceLocation();
    }
}