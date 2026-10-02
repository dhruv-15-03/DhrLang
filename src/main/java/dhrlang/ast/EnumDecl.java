package dhrlang.ast;

import dhrlang.lexer.Token;
import java.util.List;

/**
 * Represents an enum declaration.
 * Syntax: enum Color { RED, GREEN, BLUE }
 *         enum Status { PENDING("waiting"), ACTIVE("running") }
 */
public class EnumDecl extends Statement {
    private final Token name;
    private final List<EnumConstant> constants;

    public EnumDecl(Token name, List<EnumConstant> constants) {
        this.name = name;
        this.constants = constants;
    }

    public Token getName() { return name; }
    public String getNameStr() { return name.getLexeme(); }
    public List<EnumConstant> getConstants() { return constants; }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitEnumDecl(this);
    }

    /**
     * A single enum constant, optionally with arguments.
     */
    public static class EnumConstant {
        private final Token name;
        private final List<Expression> arguments; // nullable

        public EnumConstant(Token name, List<Expression> arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public Token getName() { return name; }
        public String getNameStr() { return name.getLexeme(); }
        public List<Expression> getArguments() { return arguments; }
    }
}
