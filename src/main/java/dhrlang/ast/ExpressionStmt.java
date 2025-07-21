package dhrlang.ast;

/**
 * Represents a statement that is just an expression followed by a semicolon.
 */
public class ExpressionStmt extends Statement {
    private final Expression expression;

    public ExpressionStmt(Expression expression) {
        this.expression = expression;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "ExpressionStmt{" +
                "expression=" + expression +
                '}';
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitExpressionStmt(this);
    }
}