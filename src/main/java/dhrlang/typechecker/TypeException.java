package dhrlang.typechecker;

public class TypeException extends RuntimeException {
    public TypeException(String message) {
        super("Type Error: " + message);
    }
}
