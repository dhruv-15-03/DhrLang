package dhrlang.ast;

import dhrlang.lexer.Token;

public class SuperExpr extends Expression {
    public final Token keyword;
    public final Token method;

    public SuperExpr(Token keyword, Token method) {
        this.keyword = keyword;
        this.method = method;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitSuperExpr(this);
    }
}