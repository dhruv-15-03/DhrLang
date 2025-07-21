package dhrlang.ast;

import dhrlang.lexer.Token;
import java.util.Objects;


public class ThrowStmt extends Statement {
    private final Expression value;
    private final Token throwToken; // For location information

    public ThrowStmt(Expression value, Token throwToken) {
        this.value = value;
        this.throwToken = throwToken;
    }
    
    public ThrowStmt(Expression value) {
        this(value, null);
    }

    public Expression getValue() {
        return value;
    }
    
    public Token getThrowToken() {
        return throwToken;
    }

    @Override
    public String toString() {
        return "ThrowStmt{" +
                "value=" + value +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThrowStmt throwStmt = (ThrowStmt) o;
        return Objects.equals(value, throwStmt.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitThrowStmt(this);
    }
}
