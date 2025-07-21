package dhrlang.ast;

public class IndexExpr extends Expression {
    private final Expression object;
    private final Expression index;

    public IndexExpr(Expression object, Expression index) {
        this.object = object;
        this.index = index;
    }

    public Expression getObject() {
        return object;
    }

    public Expression getIndex() {
        return index;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitIndexExpr(this);
    }
}