package dhrlang.ast;

import dhrlang.lexer.Token;

public  class SetExpr extends Expression {
    private final Expression object;
    private final Token name;
    private final Expression value;

    public SetExpr(Expression object, Token name, Expression value) {
        this.object = object;
        this.name = name;
        this.value = value;
        if (name != null) {
            this.setSourceLocation(name.getLocation());
        }
    }

    public Expression getObject() {
        return object;
    }

    public Token getName() {
        return name;
    }

    public Expression getValue() {
        return value;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitSetExpr(this);
    }
}