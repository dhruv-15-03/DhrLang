package dhrlang.ast;

import dhrlang.lexer.Token;

public class StaticAssignExpr extends Expression {
    public final VariableExpr className;
    public final Token memberName;
    public final Expression value;

    public StaticAssignExpr(VariableExpr className, Token memberName, Expression value) {
        this.className = className;
        this.memberName = memberName;
        this.value = value;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitStaticAssignExpr(this);
    }
}
