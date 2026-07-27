package dhrlang.interpreter;

public class ContinueException extends RuntimeException {
    private final String label;
    public ContinueException() { this.label = null; }
    public ContinueException(String label) { this.label = label; }
    public String getLabel() { return label; }
}
