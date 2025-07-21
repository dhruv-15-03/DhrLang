package dhrlang.ast;

import java.util.List;
import java.util.Objects;

/**
 * AST node representing a try-catch-finally statement.
 * Syntax: try { ... } catch (variable) { ... } finally { ... }
 */
public class TryStmt extends Statement {
    private final Block tryBlock;
    private final List<CatchClause> catchClauses;
    private final Block finallyBlock; // can be null

    public TryStmt(Block tryBlock, List<CatchClause> catchClauses, Block finallyBlock) {
        this.tryBlock = tryBlock;
        this.catchClauses = catchClauses;
        this.finallyBlock = finallyBlock;
    }

    public Block getTryBlock() {
        return tryBlock;
    }

    public List<CatchClause> getCatchClauses() {
        return catchClauses;
    }

    public Block getFinallyBlock() {
        return finallyBlock;
    }

    @Override
    public String toString() {
        return "TryStmt{" +
                "tryBlock=" + tryBlock +
                ", catchClauses=" + catchClauses +
                ", finallyBlock=" + finallyBlock +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TryStmt tryStmt = (TryStmt) o;
        return Objects.equals(tryBlock, tryStmt.tryBlock) &&
                Objects.equals(catchClauses, tryStmt.catchClauses) &&
                Objects.equals(finallyBlock, tryStmt.finallyBlock);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tryBlock, catchClauses, finallyBlock);
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitTryStmt(this);
    }
}
