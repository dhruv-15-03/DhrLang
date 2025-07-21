package dhrlang.ast;

import dhrlang.error.SourceLocation;

/**
 * Base interface for all AST nodes.
 */
public interface ASTNode {
    <R> R accept(ASTVisitor<R> visitor);
    
    /**
     * Get the source location of this AST node
     */
    default SourceLocation getSourceLocation() {
        return null; // Default implementation for backward compatibility
    }
}
