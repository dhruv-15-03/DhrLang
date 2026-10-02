package dhrlang.ast;

import java.util.List;

/**
 * Represents a match expression (pattern matching).
 * Syntax: match (expr) {
 *           case Pattern1 => result1
 *           case Pattern2 => result2
 *           default => fallback
 *         }
 */
public class MatchExpr extends Expression {
    private final Expression subject;
    private final List<MatchArm> arms;

    public MatchExpr(Expression subject, List<MatchArm> arms) {
        this.subject = subject;
        this.arms = arms;
    }

    public Expression getSubject() { return subject; }
    public List<MatchArm> getArms() { return arms; }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitMatchExpr(this);
    }

    /**
     * A single arm in a match expression.
     */
    public static class MatchArm {
        private final Expression pattern;  // null for default arm
        private final Expression body;
        private final boolean isDefault;

        public MatchArm(Expression pattern, Expression body, boolean isDefault) {
            this.pattern = pattern;
            this.body = body;
            this.isDefault = isDefault;
        }

        public Expression getPattern() { return pattern; }
        public Expression getBody() { return body; }
        public boolean isDefault() { return isDefault; }
    }
}
