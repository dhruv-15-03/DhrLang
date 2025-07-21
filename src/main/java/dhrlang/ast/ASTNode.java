package dhrlang.ast;

/**
 * Base interface for all AST nodes.
 */
public interface ASTNode {
    <R> R accept(ASTVisitor<R> visitor);
}
