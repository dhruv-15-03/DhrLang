package dhrlang.ast;

import dhrlang.lexer.Token;

public class PostfixIncrementExpr extends Expression {
    private final Expression target;
    private final Token operator; // INCREMENT or DECREMENT token

    public PostfixIncrementExpr(Expression target, Token operator) {
        this.target = target;
        this.operator = operator;
    }

    public Expression getTarget() {
        return target;
    }

    public Token getOperator() {
        return operator;
    }

    public boolean isIncrement() {
        return operator.getType() == dhrlang.lexer.TokenType.INCREMENT;
    }

    @Override
    public String toString() {
        return target + (isIncrement() ? "++" : "--");
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitPostfixIncrementExpr(this);
    }
}