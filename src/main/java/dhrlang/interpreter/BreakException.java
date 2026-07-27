package dhrlang.interpreter;

public class BreakException extends RuntimeException {
    private final String label;
    public BreakException() { this.label = null; }
    public BreakException(String label) { this.label = label; }
    public String getLabel() { return label; }
}
