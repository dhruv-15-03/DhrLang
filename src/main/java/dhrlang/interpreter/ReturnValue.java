package dhrlang.interpreter;

public class ReturnValue extends RuntimeException {
    private final Object value;

    public ReturnValue(Object value) {
        super(null, null, false, false); // disable stack trace
        this.value = value;
    }

    public Object getValue() {
        return value;
    }
}
