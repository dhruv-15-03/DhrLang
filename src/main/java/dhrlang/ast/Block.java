package dhrlang.ast;

import java.util.List;

/**
 * Represents a block of statements: { ... }
 */
public class Block extends Statement{
    private final List<Statement> statements;

    public Block(List<Statement> statements) {
        this.statements = statements;
    }

    public List<Statement> getStatements() {
        return statements;
    }

    @Override
    public String toString() {
        return "Block{" +
                "statements=" + statements +
                '}';
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitBlock(this);
    }
}
