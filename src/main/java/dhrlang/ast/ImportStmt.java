package dhrlang.ast;

import java.util.List;

/**
 * Represents an import statement.
 * Syntax: import "path/to/file.dhr"
 *         import { ClassName } from "path/to/file.dhr"
 */
public class ImportStmt extends Statement {
    private final String path;
    private final List<String> names; // null = import all, non-null = selective

    public ImportStmt(String path) {
        this.path = path;
        this.names = null;
    }

    public ImportStmt(String path, List<String> names) {
        this.path = path;
        this.names = names;
    }

    public String getPath() { return path; }
    public List<String> getNames() { return names; }
    public boolean isSelectiveImport() { return names != null; }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitImportStmt(this);
    }
}
