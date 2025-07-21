package dhrlang.error;

import java.util.List;
import java.util.ArrayList;


public class ErrorReporter {
    private final List<DhrError> errors = new ArrayList<>();
    private final List<DhrError> warnings = new ArrayList<>();
    private String sourceCode;
    private String filename;

    public ErrorReporter() {
        this("unknown", "");
    }

    public ErrorReporter(String filename, String sourceCode) {
        this.filename = filename;
        this.sourceCode = sourceCode;
    }

    public void setSource(String filename, String sourceCode) {
        this.filename = filename;
        this.sourceCode = sourceCode;
    }

    public void error(SourceLocation location, String message) {
        errors.add(new DhrError(ErrorType.ERROR, location, message));
    }
    
    public void error(SourceLocation location, String message, String hint) {
        errors.add(new DhrError(ErrorType.ERROR, location, message, hint));
    }

    public void error(int line, String message) {
        error(new SourceLocation(filename, line, 0), message);
    }
    
    public void error(int line, String message, String hint) {
        error(new SourceLocation(filename, line, 0), message, hint);
    }

    public void warning(SourceLocation location, String message) {
        warnings.add(new DhrError(ErrorType.WARNING, location, message));
    }
    
    public void warning(SourceLocation location, String message, String hint) {
        warnings.add(new DhrError(ErrorType.WARNING, location, message, hint));
    }

    public void warning(int line, String message) {
        warning(new SourceLocation(filename, line, 0), message);
    }
    
    public void warning(int line, String message, String hint) {
        warning(new SourceLocation(filename, line, 0), message, hint);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public int getErrorCount() {
        return errors.size();
    }

    public int getWarningCount() {
        return warnings.size();
    }

    public void printAllErrors() {
        for (DhrError error : errors) {
            printError(error);
        }
    }

    public void printAllWarnings() {
        for (DhrError warning : warnings) {
            printError(warning);
        }
    }

    public void printAll() {
        printAllErrors();
        printAllWarnings();
    }
    
    public void printSummary() {
        if (hasErrors() || hasWarnings()) {
            System.err.println();
            System.err.println("\u001B[94m╔═══════════════════════════════════════╗\u001B[0m");
            System.err.println("\u001B[94m║            COMPILATION SUMMARY       ║\u001B[0m");
            System.err.println("\u001B[94m╚═══════════════════════════════════════╝\u001B[0m");
            
            if (hasErrors()) {
                System.err.println("\u001B[91m❌ Errors: " + getErrorCount() + "\u001B[0m");
            }
            if (hasWarnings()) {
                System.err.println("\u001B[93m⚠️  Warnings: " + getWarningCount() + "\u001B[0m");
            }
            System.err.println();
        }
    }

    private void printError(DhrError error) {
        // Format the error with professional styling
        String formattedError = formatError(error);
        
        if (error.getType() == ErrorType.ERROR) {
            System.err.println(formattedError);
        } else if (error.getType() == ErrorType.WARNING) {
            System.err.println(formattedError);
        } else {
            System.err.println(formattedError);
        }
        
        // Show source context for better debugging
        if (sourceCode != null && !sourceCode.isEmpty()) {
            String context = getSourceContext(error.getLocation());
            if (!context.isEmpty()) {
                System.err.println(context);
            }
        }
        
        // Show helpful hints
        if (error.hasHint()) {
            System.err.println("\u001B[93m💡 Hint: " + error.getHint() + "\u001B[0m");
        }
        System.err.println();
    }

    private String formatError(DhrError error) {
        StringBuilder sb = new StringBuilder();
        
        // Professional error formatting with clear categorization
        if (error.getType() == ErrorType.ERROR) {
            sb.append("\u001B[91m❌ Error:\u001B[0m ");
        } else if (error.getType() == ErrorType.WARNING) {
            sb.append("\u001B[93m⚠️  Warning:\u001B[0m ");
        } else {
            sb.append("\u001B[96mℹ️  Info:\u001B[0m ");
        }
        
        // Location information
        if (error.getLocation() != null) {
            sb.append("\u001B[36m").append(error.getLocation().toString()).append("\u001B[0m");
            sb.append(" - ");
        }
        
        // Error message (keeping authentic DhrLang type names)
        sb.append(error.getMessage());
        
        return sb.toString();
    }

    private String getSourceContext(SourceLocation location) {
        if (location == null || sourceCode == null || sourceCode.isEmpty()) {
            return "";
        }

        String[] lines = sourceCode.split("\n");
        int lineNum = location.getLine();
        
        if (lineNum < 1 || lineNum > lines.length) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\u001B[2m"); // Dim text
        
        int startLine = Math.max(1, lineNum - 2);
        int endLine = Math.min(lines.length, lineNum + 2);
        
        int maxLineNumWidth = String.valueOf(endLine).length();
        
        for (int i = startLine; i <= endLine; i++) {
            String line = lines[i - 1];
            String lineNumStr = String.format("%" + maxLineNumWidth + "d", i);
            
            if (i == lineNum) {
                context.append("\u001B[0m"); 
                context.append("\u001B[91m→ ").append(lineNumStr).append(" │ \u001B[0m");
                context.append("\u001B[1m").append(line).append("\u001B[0m\n");
                
                if (location.getColumn() > 0) {
                    context.append("\u001B[91m");
                    for (int j = 0; j < maxLineNumWidth + 3; j++) context.append(" ");
                    context.append("│ ");
                    for (int j = 0; j < location.getColumn() - 1; j++) {
                        context.append(" ");
                    }
                    context.append("^\u001B[0m\n");
                }
                context.append("\u001B[2m");
            } else {
                context.append("  ").append(lineNumStr).append(" │ ").append(line).append("\n");
            }
        }
        
        context.append("\u001B[0m"); 
        return context.toString();
    }

    public void clear() {
        errors.clear();
        warnings.clear();
    }

    public List<DhrError> getErrors() {
        return new ArrayList<>(errors);
    }

    public List<DhrError> getWarnings() {
        return new ArrayList<>(warnings);
    }
}
