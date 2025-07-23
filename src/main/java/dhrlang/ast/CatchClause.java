package dhrlang.ast;

import java.util.Objects;


public class CatchClause implements ASTNode {
    private final String exceptionType;
    private final String parameter;
    private final Block body;

    // Constructor with exception type support
    public CatchClause(String exceptionType, String parameter, Block body) {
        this.exceptionType = exceptionType != null ? exceptionType : "any";
        this.parameter = parameter;
        this.body = body;
    }

    // Backward compatibility constructor
    public CatchClause(String parameter, Block body) {
        this("any", parameter, body);
    }

    public String getExceptionType() {
        return exceptionType;
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
                "exceptionType='" + exceptionType + '\'' +
                ", parameter='" + parameter + '\'' +
                ", body=" + body +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CatchClause that = (CatchClause) o;
        return Objects.equals(exceptionType, that.exceptionType) && 
               Objects.equals(parameter, that.parameter) && 
               Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exceptionType, parameter, body);
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitCatchClause(this);
    }
}
