package dhrlang.interpreter;

import dhrlang.error.ErrorFactory;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class DhrClass implements Callable {
    final String name;
    final DhrClass superclass;
    private final Map<String, Function> methods;
    private final Map<String, Function> staticMethods;
    private final Map<String, Object> staticFields;
    private final boolean isAbstract;
    private final Set<String> implementedInterfaces; // Track implemented interfaces
    
    public DhrClass(String name, DhrClass superclass, Map<String, Function> methods) {
        this(name, superclass, methods, new HashMap<>(), new HashMap<>(), false);
    }
    
    public DhrClass(String name, DhrClass superclass, Map<String, Function> methods, 
                    Map<String, Function> staticMethods, Map<String, Object> staticFields) {
        this(name, superclass, methods, staticMethods, staticFields, false);
    }
    
    public DhrClass(String name, DhrClass superclass, Map<String, Function> methods, 
                    Map<String, Function> staticMethods, Map<String, Object> staticFields, boolean isAbstract) {
        this(name, superclass, methods, staticMethods, staticFields, isAbstract, new HashSet<>());
    }
    
    public DhrClass(String name, DhrClass superclass, Map<String, Function> methods, 
                    Map<String, Function> staticMethods, Map<String, Object> staticFields, 
                    boolean isAbstract, Set<String> implementedInterfaces) {
        this.name = name;
        this.superclass = superclass;
        this.methods = methods;
        this.staticMethods = staticMethods != null ? staticMethods : new HashMap<>();
        this.staticFields = staticFields != null ? staticFields : new HashMap<>();
        this.isAbstract = isAbstract;
        this.implementedInterfaces = implementedInterfaces != null ? implementedInterfaces : new HashSet<>();
    }

    public Function findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }
        if (superclass != null) {
            return superclass.findMethod(name);
        }
        return null;
    }
    
    public Function findStaticMethod(String name) {
        if (staticMethods.containsKey(name)) {
            return staticMethods.get(name);
        }
        if (superclass != null) {
            return superclass.findStaticMethod(name);
        }
        return null;
    }
    
    public Object getStaticField(String name) {
        return getStaticField(name, null);
    }
    
    public Object getStaticField(String name, dhrlang.error.SourceLocation location) {
        if (staticFields.containsKey(name)) {
            return staticFields.get(name);
        }
        if (superclass != null) {
            return superclass.getStaticField(name, location);
        }
        throw ErrorFactory.accessError("Undefined static field '" + name + "'.", location);
    }
    
    public void setStaticField(String name, Object value) {
        staticFields.put(name, value); // Allow setting even if not previously defined
    }
    
    public boolean hasStaticField(String name) {
        return staticFields.containsKey(name) || 
               (superclass != null && superclass.hasStaticField(name));
    }

    @Override
    public int arity() {
        Function initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        if (isAbstract) {
            dhrlang.error.SourceLocation location = interpreter.getCurrentCallLocation();
            throw ErrorFactory.validationError(
                "Cannot instantiate abstract class '" + name + "'. " +
                "Abstract classes contain abstract methods and cannot be instantiated directly. " +
                "Create a concrete subclass that implements all abstract methods, then instantiate the subclass instead.",
                location
            );
        }
        
        Instance instance = new Instance(this);

        Function initializer = findMethod("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }

        return instance;
    }

    @Override
    public String toString() {
        return "<class " + name + ">";
    }
    
    // Interface support methods
    public boolean implementsInterface(String interfaceName) {
        return implementedInterfaces.contains(interfaceName) ||
               (superclass != null && superclass.implementsInterface(interfaceName));
    }
    
    public void addImplementedInterface(String interfaceName) {
        implementedInterfaces.add(interfaceName);
    }
    
    public Set<String> getDirectlyImplementedInterfaces() {
        return new HashSet<>(implementedInterfaces);
    }
    
    public Set<String> getAllImplementedInterfaces() {
        Set<String> allInterfaces = new HashSet<>(implementedInterfaces);
        if (superclass != null) {
            allInterfaces.addAll(superclass.getAllImplementedInterfaces());
        }
        return allInterfaces;
    }
}