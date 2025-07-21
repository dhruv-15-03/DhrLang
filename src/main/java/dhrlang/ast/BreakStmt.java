package dhrlang.ast;

public class BreakStmt extends Statement {

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitBreakStmt(this);
    }
}