package dhrlang.ast;

import dhrlang.lexer.Token;

/**
 * Represents a variable reference expression.
 */
public class VariableExpr extends Expression {
    private final Token name;

    public VariableExpr(Token name) {
        this.name = name;
        if (name != null) {
            this.setSourceLocation(name.getLocation());
        }
    }

    public Token getName() {
        return name;
    }

    @Override
    public String toString() {
        return "VariableExpr{" +
                "name=" + name +
                '}';
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitVariableExpr(this);
    }
}