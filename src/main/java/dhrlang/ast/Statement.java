package dhrlang.ast;

import dhrlang.error.SourceLocation;

public abstract class Statement implements ASTNode {
    protected SourceLocation sourceLocation;
    
    public void setSourceLocation(SourceLocation location) {
        this.sourceLocation = location;
    }
    
    @Override
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }
}
