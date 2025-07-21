package dhrlang.ast;

import dhrlang.lexer.Token;

public class StaticAccessExpr extends Expression {
    public final VariableExpr className;
    public final Token memberName;

    public StaticAccessExpr(VariableExpr className, Token memberName) {
        this.className = className;
        this.memberName = memberName;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitStaticAccessExpr(this);
    }
}
