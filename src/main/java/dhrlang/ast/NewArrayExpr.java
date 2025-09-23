package dhrlang.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NewArrayExpr extends Expression {
    private final String elementType;
    // Support multiple dimensions; keep single-d size for backward compatibility
    private final Expression size; // legacy first-dimension size
    private final List<Expression> sizes; // all dimensions (non-empty)

    // Legacy constructor: single dimension
    public NewArrayExpr(String elementType, Expression size) {
        this.elementType = elementType;
        this.size = size;
        List<Expression> list = new ArrayList<>();
        list.add(size);
        this.sizes = Collections.unmodifiableList(list);
    }

    public NewArrayExpr(String elementType, List<Expression> sizes) {
        this.elementType = elementType;
        this.size = sizes!=null && !sizes.isEmpty() ? sizes.get(0) : null;
        this.sizes = sizes==null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(sizes));
    }

    public String getElementType() {
        return elementType;
    }

    public Expression getSize() {
        return size;
    }

    public List<Expression> getSizes() {
        return sizes;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitNewArrayExpr(this);
    }

    @Override
    public String toString() {
        return "NewArrayExpr{" +
                "elementType='" + elementType + '\'' +
                ", sizes=" + sizes +
                '}';
    }
}
