package dhrlang.ast;

import java.util.List;

/**
 * Represents the root of a DhrLang program — a list of classes.
 */
public class Program {
    private final List<ClassDecl> classes;

    public Program(List<ClassDecl> classes) {
        this.classes = classes;
    }

    public List<ClassDecl> getClasses() {
        return classes;
    }

    @Override
    public String toString() {
        return "Program{" +
                "classes=" + classes +
                '}';
    }
}
