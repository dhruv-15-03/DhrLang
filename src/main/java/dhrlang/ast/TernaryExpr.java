package dhrlang.ast;

public class TernaryExpr extends Expression {
    private final Expression condition;
    private final Expression thenBranch;
    private final Expression elseBranch;

    public TernaryExpr(Expression condition, Expression thenBranch, Expression elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    public Expression getCondition() { return condition; }
    public Expression getThenBranch() { return thenBranch; }
    public Expression getElseBranch() { return elseBranch; }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitTernaryExpr(this);
    }
}
