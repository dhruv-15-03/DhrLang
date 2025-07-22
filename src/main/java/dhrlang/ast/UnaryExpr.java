package dhrlang.ast;

import dhrlang.lexer.Token;

/**
 * Represents a unary operation expression: op right
 */
public class UnaryExpr extends Expression {
    private final Token operator; // token representing operator like MINUS, NOT, etc.
    private final Expression right;

    public UnaryExpr(Token operator, Expression right) {
        this.operator = operator;
        this.right = right;
        if (operator != null) {
            this.setSourceLocation(operator.getLocation());
        }
    }

    public Token getOperator() {
        return operator;
    }

    public Expression getRight() {
        return right;
    }

    @Override
    public String toString() {
        return "UnaryExpr{" +
                "operator=" + operator +
                ", right=" + right +
                '}';
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitUnaryExpr(this);
    }
}