package dhrlang.runtime;

import dhrlang.ast.*;
import dhrlang.error.ErrorFactory;
import dhrlang.error.SourceLocation;
import dhrlang.interpreter.*;

import java.util.*;

/**
 * Loads interfaces & classes, validates inheritance & interface contracts,
 * initializes static fields, and invokes the static main entry point.
 */
public final class ProgramLoader {
    private ProgramLoader() {}

    public static void loadAndRun(Program program, Interpreter interpreter, Environment globals) {
        Map<String, DhrInterface> interfaceRegistry = new HashMap<>();
        for (InterfaceDecl interfaceDecl : program.getInterfaces()) {
            DhrInterface dhrInterface = new DhrInterface(interfaceDecl);
            interfaceRegistry.put(interfaceDecl.getName(), dhrInterface);
            globals.define(interfaceDecl.getName(), dhrInterface);
        }

        for (InterfaceDecl interfaceDecl : program.getInterfaces()) {
            DhrInterface dhrInterface = interfaceRegistry.get(interfaceDecl.getName());
            dhrInterface.validateInheritance(interfaceRegistry, interfaceDecl.getSourceLocation());
        }

        for (ClassDecl classDecl : program.getClasses()) {
            globals.define(classDecl.getName(), null); // predeclare for circular refs
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
                Function function = new Function(method, globals, classDecl.getName());
                if (method.hasModifier(Modifier.STATIC)) staticMethods.put(method.getName(), function); else methods.put(method.getName(), function);
            }

            for (VarDecl field : classDecl.getVariables()) {
                if (field.hasModifier(Modifier.STATIC)) {
                    Object placeholder = RuntimeDefaults.getDefaultValue(field.getType());
                    staticFields.put(field.getName(), placeholder);
                }
            }

            Set<String> implementedInterfaces = new HashSet<>();
            for (VariableExpr interfaceExpr : classDecl.getInterfaces()) {
                String interfaceName = interfaceExpr.getName().getLexeme();
                if (!interfaceRegistry.containsKey(interfaceName)) {
                    throw ErrorFactory.validationError(
                        "Undefined interface '" + interfaceName + "'. Make sure it is defined before implementing.",
                        interfaceExpr.getSourceLocation()
                    );
                }
                implementedInterfaces.add(interfaceName);
                DhrInterface dhrInterface = interfaceRegistry.get(interfaceName);
                DhrClass tempClass = new DhrClass(classDecl.getName(), superclass, methods, staticMethods, staticFields, classDecl.isAbstract(), implementedInterfaces);
                dhrInterface.validateImplementation(tempClass, interfaceExpr.getSourceLocation(), interfaceRegistry);
            }

            DhrClass klass = new DhrClass(classDecl.getName(), superclass, methods, staticMethods, staticFields, classDecl.isAbstract(), implementedInterfaces, classDecl.getFunctions(), classDecl.getVariables());
            for (String iName : implementedInterfaces) klass.addImplementedInterface(iName);
            globals.assign(classDecl.getName(), klass);
        }

        for (ClassDecl classDecl : program.getClasses()) {
            Object val = globals.get(classDecl.getName());
            if(!(val instanceof DhrClass dhrClass)) continue;
            Environment staticInitEnv = new Environment(globals);
            for (VarDecl field : classDecl.getVariables()) {
                if (field.hasModifier(Modifier.STATIC)) {
                    Object def = dhrClass.getStaticField(field.getName()); // placeholder set earlier
                    staticInitEnv.define(field.getName(), def);
                }
            }
            for(VarDecl field : classDecl.getVariables()) {
                if(!field.hasModifier(Modifier.STATIC)) continue;
                Object value = dhrClass.getStaticField(field.getName()); // start from existing (default) value
                if(field.getInitializer()!=null) {
                    value = interpreter.evaluate(field.getInitializer(), staticInitEnv);
                }
                dhrClass.setStaticField(field.getName(), value);
                staticInitEnv.assign(field.getName(), value);
            }
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
            throw ErrorFactory.accessError("Entry point error: No static main method found. Please define 'static kaam main()' in any class.", (SourceLocation) null);
        }
        if (staticMainMethod.arity() != 0) {
            throw ErrorFactory.typeError("Entry point 'main' should not have parameters.", (SourceLocation) null);
        }
        staticMainMethod.call(interpreter, List.of());
    }
}
