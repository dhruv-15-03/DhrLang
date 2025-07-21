package dhrlang.ast;

import java.util.List;

public class NewExpr extends Expression {
    private final String className;
    private final List<Expression> arguments;
    public NewExpr(String className,  List<Expression> arguments1) {
        this.className = className;
        this.arguments = arguments1;
    }

    public String getClassName() {
        return className;
    }
    public List<Expression> getArguments() {
        return arguments;
    }
    @Override
    public String toString() {
        return "VariableExpr{" +
                "name=" + className +
                '}';
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitNewExpr(this);
    }
}