package dhrlang.ast;

import dhrlang.lexer.Token;
import java.util.List;

/**
 * Represents a lambda expression.
 * Syntax: (params) => expression
 *         (params) => { statements }
 */
public class LambdaExpr extends Expression {
    private final List<Token> parameters;
    private final List<String> paramTypes; // nullable type annotations
    private final Statement body;          // Block or single expression wrapped as ReturnStmt

    public LambdaExpr(List<Token> parameters, List<String> paramTypes, Statement body) {
        this.parameters = parameters;
        this.paramTypes = paramTypes;
        this.body = body;
    }

    public List<Token> getParameters() { return parameters; }
    public List<String> getParamTypes() { return paramTypes; }
    public Statement getBody() { return body; }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitLambdaExpr(this);
    }
}
