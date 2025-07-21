package dhrlang.ast;

import dhrlang.lexer.Token;

public class PrefixIncrementExpr extends Expression {
    private final Token operator; // INCREMENT or DECREMENT token
    private final Expression target;

    public PrefixIncrementExpr(Token operator, Expression target) {
        this.operator = operator;
        this.target = target;
    }

    public Token getOperator() {
        return operator;
    }

    public Expression getTarget() {
        return target;
    }

    public boolean isIncrement() {
        return operator.getType() == dhrlang.lexer.TokenType.INCREMENT;
    }

    @Override
    public String toString() {
        return (isIncrement() ? "++" : "--") + target;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitPrefixIncrementExpr(this);
    }
}