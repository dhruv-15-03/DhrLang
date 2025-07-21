package dhrlang.ast;

public class ContinueStmt extends Statement{

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitContinueStmt(this);
    }
}