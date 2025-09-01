package dhrlang.ast;

import java.util.Objects;
import dhrlang.error.SourceLocation;

public class CatchClause implements ASTNode {
    private final String exceptionType;
    private final String parameter;
    private final Block body;
    private SourceLocation location; // start location of 'catch' keyword / clause

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

    public void setSourceLocation(SourceLocation loc){ this.location = loc; }
    @Override
    public dhrlang.error.SourceLocation getSourceLocation(){ return location; }

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
