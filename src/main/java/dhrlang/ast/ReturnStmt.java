package dhrlang.ast;

/**
 * Represents a return statement, optionally with a return value.
 */
public class ReturnStmt extends Statement {
    private final Expression value; // nullable

    public ReturnStmt(Expression value) {
        this.value = value;
    }

    public Expression getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "ReturnStmt{" +
                "value=" + value +
                '}';
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitReturnStmt(this);
    }
}