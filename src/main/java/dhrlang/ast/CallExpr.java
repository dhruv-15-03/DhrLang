package dhrlang.ast;

import java.util.List;
import java.util.Objects;

/**
 * Represents a function call expression, e.g. foo(1, 2, bar).
 */
public class CallExpr extends Expression {

    private final Expression callee;           // The function being called
    private final List<Expression> arguments;  // Arguments passed to the call

    public CallExpr(Expression callee, List<Expression> arguments) {
        this.callee = Objects.requireNonNull(callee, "Callee expression cannot be null");
        this.arguments = Objects.requireNonNull(arguments, "Arguments list cannot be null");
    }

    public Expression getCallee() {
        return callee;
    }

    public List<Expression> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "CallExpr{" +
                "callee=" + callee +
                ", arguments=" + arguments +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallExpr)) return false;
        CallExpr callExpr = (CallExpr) o;
        return callee.equals(callExpr.callee) &&
                arguments.equals(callExpr.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callee, arguments);
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitCallExpr(this);
    }
}