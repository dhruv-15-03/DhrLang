package dhrlang.ast;

public class ContinueStmt extends Statement {
    private final String label; // nullable — null means unlabeled

    public ContinueStmt() { this.label = null; }
    public ContinueStmt(String label) { this.label = label; }
    public String getLabel() { return label; }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitContinueStmt(this);
    }
}