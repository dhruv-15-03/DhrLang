package dhrlang.error;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


public class ErrorAnalytics {
    private final Map<ErrorType, Integer> errorCounts = new EnumMap<>(ErrorType.class);
    private final Map<RuntimeErrorCategory, Integer> runtimeErrorCounts = new EnumMap<>(RuntimeErrorCategory.class);
    private final List<DhrError> errorHistory = new ArrayList<>();
    private final LocalDateTime compilationStart;
    
    public ErrorAnalytics() {
        this.compilationStart = LocalDateTime.now();
    }
    
    public void recordError(DhrError error) {
        errorHistory.add(error);
        errorCounts.merge(error.getType(), 1, Integer::sum);
    }
    
    public void recordRuntimeError(RuntimeErrorCategory category) {
        runtimeErrorCounts.merge(category, 1, Integer::sum);
    }
    
    public void printDetailedReport() {
        if (errorHistory.isEmpty()) {
            System.out.println(ErrorDisplayConfig.color(ErrorDisplayConfig.GREEN, 
                "✓ No errors found! Clean compilation."));
            return;
        }
        
        System.err.println();
        System.err.println(ErrorDisplayConfig.color(ErrorDisplayConfig.BLUE, 
            "╔═══════════════════════════════════════════════════════════════╗"));
        System.err.println(ErrorDisplayConfig.color(ErrorDisplayConfig.BLUE, 
            "║                      ERROR ANALYSIS REPORT                   ║"));
        System.err.println(ErrorDisplayConfig.color(ErrorDisplayConfig.BLUE, 
            "╚═══════════════════════════════════════════════════════════════╝"));
        
        // Summary statistics
        System.err.println();
        System.err.println(ErrorDisplayConfig.color(ErrorDisplayConfig.YELLOW, "📊 Summary:"));
        for (Map.Entry<ErrorType, Integer> entry : errorCounts.entrySet()) {
            String emoji = entry.getKey() == ErrorType.ERROR ? "❌" : 
                          entry.getKey() == ErrorType.WARNING ? "⚠️" : "ℹ️";
            System.err.println("   " + emoji + " " + entry.getKey() + ": " + entry.getValue());
        }
        
        // Error distribution by line
        Map<Integer, List<DhrError>> errorsByLine = errorHistory.stream()
            .filter(e -> e.getLocation() != null)
            .collect(Collectors.groupingBy(e -> e.getLocation().getLine()));
        
        if (!errorsByLine.isEmpty()) {
            System.err.println();
            System.err.println(ErrorDisplayConfig.color(ErrorDisplayConfig.YELLOW, "📍 Hot Spots:"));
            errorsByLine.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(5)
                .forEach(entry -> {
                    System.err.println("   Line " + entry.getKey() + ": " + 
                        entry.getValue().size() + " issue(s)");
                });
        }
        
        LocalDateTime now = LocalDateTime.now();
        System.err.println();
        System.err.println(ErrorDisplayConfig.color(ErrorDisplayConfig.CYAN, "⏱️  Compilation Time: ") + 
            java.time.Duration.between(compilationStart, now).toMillis() + "ms");
        
        System.err.println(ErrorDisplayConfig.color(ErrorDisplayConfig.CYAN, "📅 Timestamp: ") + 
            now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
    
    public boolean hasErrors() {
        return errorCounts.getOrDefault(ErrorType.ERROR, 0) > 0;
    }
    
    public boolean hasWarnings() {
        return errorCounts.getOrDefault(ErrorType.WARNING, 0) > 0;
    }
    
    public int getTotalErrors() {
        return errorCounts.getOrDefault(ErrorType.ERROR, 0);
    }
    
    public int getTotalWarnings() {
        return errorCounts.getOrDefault(ErrorType.WARNING, 0);
    }
}
