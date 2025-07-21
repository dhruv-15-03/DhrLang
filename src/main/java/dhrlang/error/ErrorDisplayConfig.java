package dhrlang.error;

/**
 * Configuration for error display formatting and behavior
 */
public class ErrorDisplayConfig {
    private static boolean colorEnabled = true;
    private static boolean showStackTrace = false;
    private static boolean showHints = true;
    private static int contextLines = 2;
    
    public static boolean isColorEnabled() {
        return colorEnabled;
    }
    
    public static void setColorEnabled(boolean enabled) {
        colorEnabled = enabled;
    }
    
    public static boolean isShowStackTrace() {
        return showStackTrace;
    }
    
    public static void setShowStackTrace(boolean show) {
        showStackTrace = show;
    }
    
    public static boolean isShowHints() {
        return showHints;
    }
    
    public static void setShowHints(boolean show) {
        showHints = show;
    }
    
    public static int getContextLines() {
        return contextLines;
    }
    
    public static void setContextLines(int lines) {
        contextLines = Math.max(0, Math.min(10, lines));
    }
    
    // Color codes
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[91m";
    public static final String GREEN = "\u001B[92m";
    public static final String YELLOW = "\u001B[93m";
    public static final String BLUE = "\u001B[94m";
    public static final String CYAN = "\u001B[96m";
    public static final String DIM = "\u001B[2m";
    public static final String BOLD = "\u001B[1m";
    
    public static String color(String colorCode, String text) {
        return colorEnabled ? colorCode + text + RESET : text;
    }
}
