package dhrlang.ast;

/**
 * Represents a literal value expression: number, string, char, boolean
 */
public class LiteralExpr extends Expression {
    private final Object value;

    public LiteralExpr(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "LiteralExpr{" +
                "value=" + value +
                '}';
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitLiteralExpr(this);
    }
}