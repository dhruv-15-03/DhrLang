package dhrlang.ast;

public class IndexAssignExpr extends Expression {
    private final Expression object;
    private final Expression index;
    private final Expression value;

    public IndexAssignExpr(Expression object, Expression index, Expression value) {
        this.object = object;
        this.index = index;
        this.value = value;
    }

    public Expression getObject() {
        return object;
    }

    public Expression getIndex() {
        return index;
    }

    public Expression getValue() {
        return value;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitIndexAssignExpr(this);
    }
}