package dhrlang.ast;

import java.util.List;

public class ArrayExpr extends Expression {
    private final List<Expression> elements;

    public ArrayExpr(List<Expression> elements) {
        this.elements = elements;
    }

    public List<Expression> getElements() {
        return elements;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitArrayExpr(this);
    }
}