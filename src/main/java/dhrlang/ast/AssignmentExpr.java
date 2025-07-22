package dhrlang.ast;

import dhrlang.lexer.Token;

/**
 * Represents an assignment expression: identifier = value
 */
public class AssignmentExpr extends Expression {
    private final Token name;
    private final Expression value;

    public AssignmentExpr(Token name, Expression value) {
        this.name = name;
        this.value = value;
        if (name != null) {
            this.setSourceLocation(name.getLocation());
        }
    }

    public Token getName() {
        return name;
    }

    public Expression getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "AssignmentExpr{" +
                "name=" + name +
                ", value=" + value +
                '}';
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitAssignmentExpr(this);
    }
}