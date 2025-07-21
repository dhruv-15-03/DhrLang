package dhrlang.ast;

import dhrlang.lexer.Token;

public class GetExpr extends Expression {
    private final Expression object;
    private final Token name;

    public GetExpr(Expression object, Token name) {
        this.object = object;
        this.name = name;
    }

    public Expression getObject() {
        return object;
    }

    public Token getName() {
        return name;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitGetExpr(this);
    }
}