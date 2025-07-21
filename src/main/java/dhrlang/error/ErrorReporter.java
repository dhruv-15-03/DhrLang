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

    public void error(int line, String message) {
        error(new SourceLocation(filename, line, 0), message);
    }

    public void warning(SourceLocation location, String message) {
        warnings.add(new DhrError(ErrorType.WARNING, location, message));
    }

    public void warning(int line, String message) {
        warning(new SourceLocation(filename, line, 0), message);
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

    private void printError(DhrError error) {
        System.err.println(formatError(error));
        if (sourceCode != null && !sourceCode.isEmpty()) {
            String context = getSourceContext(error.getLocation());
            if (!context.isEmpty()) {
                System.err.println(context);
            }
        }
        System.err.println();
    }

    private String formatError(DhrError error) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(error.getType().toString().toLowerCase())
          .append(": ");
        
        if (error.getLocation() != null) {
            sb.append("[").append(error.getLocation().toString()).append("] ");
        }
        
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
        
        int startLine = Math.max(1, lineNum - 1);
        int endLine = Math.min(lines.length, lineNum + 1);
        
        for (int i = startLine; i <= endLine; i++) {
            String line = lines[i - 1];
            
            String lineNumStr = String.format("%4d", i);
            
            if (i == lineNum) {
                context.append(" -> ").append(lineNumStr).append(" | ").append(line).append("\n");
                
                if (location.getColumn() > 0) {
                    context.append("      | ");
                    for (int j = 0; j < location.getColumn() - 1; j++) {
                        context.append(" ");
                    }
                    context.append("^\n");
                }
            } else {
                context.append("    ").append(lineNumStr).append(" | ").append(line).append("\n");
            }
        }
        
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
