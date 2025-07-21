package dhrlang.ast;

/**
 * Represents a print statement: print(expression);
 */
public class PrintStmt extends Statement {
    private final Expression expression;

    public PrintStmt(Expression expression) {
        this.expression = expression;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "PrintStmt{" +
                "expression=" + expression +
                '}';
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitPrintStmt(this);
    }
}
