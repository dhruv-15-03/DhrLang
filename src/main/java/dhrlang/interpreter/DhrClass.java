package dhrlang.interpreter;

import dhrlang.error.ErrorFactory;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class DhrClass implements Callable {
    final String name;
    final DhrClass superclass;
    private final Map<String, Function> methods;
    private final Map<String, Function> staticMethods;
    private final Map<String, Object> staticFields;
    private final boolean isAbstract;

    public DhrClass(String name, DhrClass superclass, Map<String, Function> methods) {
        this(name, superclass, methods, new HashMap<>(), new HashMap<>(), false);
    }
    
    public DhrClass(String name, DhrClass superclass, Map<String, Function> methods, 
                    Map<String, Function> staticMethods, Map<String, Object> staticFields) {
        this(name, superclass, methods, staticMethods, staticFields, false);
    }
    
    public DhrClass(String name, DhrClass superclass, Map<String, Function> methods, 
                    Map<String, Function> staticMethods, Map<String, Object> staticFields, boolean isAbstract) {
        this.name = name;
        this.superclass = superclass;
        this.methods = methods;
        this.staticMethods = staticMethods != null ? staticMethods : new HashMap<>();
        this.staticFields = staticFields != null ? staticFields : new HashMap<>();
        this.isAbstract = isAbstract;
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
            throw ErrorFactory.validationError("Cannot instantiate abstract class '" + name + "'.", location);
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
}