package dhrlang.ast;

import dhrlang.lexer.Token;

public class ThisExpr extends Expression {
    public final Token keyword;

    public ThisExpr(Token keyword) {
        this.keyword = keyword;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitThisExpr(this);
    }
}