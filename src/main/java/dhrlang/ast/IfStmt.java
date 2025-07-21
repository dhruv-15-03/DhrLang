package dhrlang.ast;

import java.util.Objects;

public class IfStmt extends Statement {
    private final Expression condition;
    private final Statement thenBranch;
    private final Statement elseBranch; // can be null

    public IfStmt(Expression condition, Statement thenBranch, Statement elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    public Expression getCondition() {
        return condition;
    }

    public Statement getThenBranch() {
        return thenBranch;
    }

    public Statement getElseBranch() {
        return elseBranch;
    }

    @Override
    public String toString() {
        return "IfStmt{" +
                "condition=" + condition +
                ", thenBranch=" + thenBranch +
                ", elseBranch=" + elseBranch +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IfStmt)) return false;
        IfStmt ifStmt = (IfStmt) o;
        return Objects.equals(condition, ifStmt.condition) &&
                Objects.equals(thenBranch, ifStmt.thenBranch) &&
                Objects.equals(elseBranch, ifStmt.elseBranch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, thenBranch, elseBranch);
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitIfStmt(this);
    }
}