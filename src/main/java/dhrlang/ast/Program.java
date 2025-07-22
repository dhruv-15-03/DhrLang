package dhrlang.ast;

import java.util.List;
import java.util.ArrayList;


public class Program {
    private final List<ClassDecl> classes;
    private final List<InterfaceDecl> interfaces;

    public Program(List<ClassDecl> classes) {
        this(classes, new ArrayList<>());
    }
    
    public Program(List<ClassDecl> classes, List<InterfaceDecl> interfaces) {
        this.classes = classes;
        this.interfaces = interfaces != null ? interfaces : new ArrayList<>();
    }

    public List<ClassDecl> getClasses() {
        return classes;
    }
    
    public List<InterfaceDecl> getInterfaces() {
        return interfaces;
    }

    @Override
    public String toString() {
        return "Program{" +
                "classes=" + classes +
                ", interfaces=" + interfaces +
                '}';
    }
}
