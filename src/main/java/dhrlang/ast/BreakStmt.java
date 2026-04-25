package dhrlang.ast;

public class BreakStmt extends Statement {
    private final String label; // nullable — null means unlabeled

    public BreakStmt() { this.label = null; }
    public BreakStmt(String label) { this.label = label; }
    public String getLabel() { return label; }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitBreakStmt(this);
    }
}