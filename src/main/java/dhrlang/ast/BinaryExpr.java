package dhrlang.ast;

import dhrlang.lexer.Token;

/**
 * Represents a binary operation expression: left op right
 */
public class BinaryExpr extends Expression {
    private final Expression left;
    private final Token operator; // token representing operator like PLUS, MINUS, etc.
    private final Expression right;

    public BinaryExpr(Expression left, Token operator, Expression right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
        if (operator != null) {
            this.setSourceLocation(operator.getLocation());
        }
    }

    public Expression getLeft() {
        return left;
    }

    public Token getOperator() {
        return operator;
    }

    public Expression getRight() {
        return right;
    }

    @Override
    public String toString() {
        return "BinaryExpr{" +
                "left=" + left +
                ", operator=" + operator +
                ", right=" + right +
                '}';
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitBinaryExpr(this);
    }
}