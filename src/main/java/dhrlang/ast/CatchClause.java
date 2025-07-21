package dhrlang.ast;

import java.util.Objects;


public class CatchClause implements ASTNode {
    private final String parameter;
    private final Block body;

    public CatchClause(String parameter, Block body) {
        this.parameter = parameter;
        this.body = body;
    }

    public String getParameter() {
        return parameter;
    }

    public Block getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "CatchClause{" +
                "parameter='" + parameter + '\'' +
                ", body=" + body +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CatchClause that = (CatchClause) o;
        return Objects.equals(parameter, that.parameter) && Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameter, body);
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitCatchClause(this);
    }
}
