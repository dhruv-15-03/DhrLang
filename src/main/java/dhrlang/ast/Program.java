package dhrlang.ast;

import java.util.List;
import java.util.ArrayList;


public class Program {
    private final List<ClassDecl> classes;
    private final List<InterfaceDecl> interfaces;
    private final List<ImportStmt> imports;
    private final List<EnumDecl> enums;

    public Program(List<ClassDecl> classes) {
        this(classes, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
    
    public Program(List<ClassDecl> classes, List<InterfaceDecl> interfaces) {
        this(classes, interfaces, new ArrayList<>(), new ArrayList<>());
    }

    public Program(List<ClassDecl> classes, List<InterfaceDecl> interfaces,
                   List<ImportStmt> imports, List<EnumDecl> enums) {
        this.classes = classes;
        this.interfaces = interfaces != null ? interfaces : new ArrayList<>();
        this.imports = imports != null ? imports : new ArrayList<>();
        this.enums = enums != null ? enums : new ArrayList<>();
    }

    public List<ClassDecl> getClasses() {
        return classes;
    }
    
    public List<InterfaceDecl> getInterfaces() {
        return interfaces;
    }

    public List<ImportStmt> getImports() { return imports; }
    public List<EnumDecl> getEnums() { return enums; }

    @Override
    public String toString() {
        return "Program{" +
                "imports=" + imports.size() +
                ", classes=" + classes +
                ", interfaces=" + interfaces +
                ", enums=" + enums.size() +
                '}';
    }
}
