package dhrlang.ast;

import java.util.Objects;

public class WhileStmt extends Statement {
    private final Expression condition;
    private final Statement body;

    public WhileStmt(Expression condition, Statement body) {
        this.condition = condition;
        this.body = body;
    }

    public Expression getCondition() {
        return condition;
    }

    public Statement getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "WhileStmt{" +
                "condition=" + condition +
                ", body=" + body +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WhileStmt)) return false;
        WhileStmt that = (WhileStmt) o;
        return Objects.equals(condition, that.condition) &&
                Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, body);
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitWhileStmt(this);
    }
}