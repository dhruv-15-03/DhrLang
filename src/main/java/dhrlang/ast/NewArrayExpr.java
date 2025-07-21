package dhrlang.ast;


public class NewArrayExpr extends Expression {
    private final String elementType;
    private final Expression size;

    public NewArrayExpr(String elementType, Expression size) {
        this.elementType = elementType;
        this.size = size;
    }

    public String getElementType() {
        return elementType;
    }

    public Expression getSize() {
        return size;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitNewArrayExpr(this);
    }

    @Override
    public String toString() {
        return "NewArrayExpr{" +
                "elementType='" + elementType + '\'' +
                ", size=" + size +
                '}';
    }
}
