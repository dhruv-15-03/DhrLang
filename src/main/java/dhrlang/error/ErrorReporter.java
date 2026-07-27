package dhrlang.error;

import java.util.List;
import java.util.ArrayList;


public class ErrorReporter {
    private final List<DhrError> errors = new ArrayList<>();
    private final List<DhrError> warnings = new ArrayList<>();
    private String sourceCode;
    private String filename;
    // suppression: map target line -> set of suppressed codes (applies to that line)
    private final java.util.Map<Integer, java.util.Set<ErrorCode>> lineSuppressions = new java.util.HashMap<>();
    private final java.util.Set<ErrorCode> fileSuppressions = new java.util.HashSet<>();
    // fast de-dup keys for errors & warnings (type|line|col|code|message)
    private final java.util.Set<String> errorKeys = new java.util.HashSet<>();
    private final java.util.Set<String> warningKeys = new java.util.HashSet<>();

    public ErrorReporter() {
    this("unknown", "");
    }

    public ErrorReporter(String filename, String sourceCode) {
        this.filename = filename;
        this.sourceCode = sourceCode;
    parseSuppressDirectives();
    }

    public void setSource(String filename, String sourceCode) {
        this.filename = filename;
        this.sourceCode = sourceCode;
    parseSuppressDirectives();
    }

    public void error(SourceLocation location, String message) {
    addError(new DhrError(ErrorType.ERROR, location, message, null, null));
    }
    
    public void error(SourceLocation location, String message, String hint) {
    addError(new DhrError(ErrorType.ERROR, location, message, hint, null));
    }

    public void error(SourceLocation location, String message, String hint, ErrorCode code) {
    addError(new DhrError(ErrorType.ERROR, location, message, hint, code));
    }

    public void error(int line, String message) {
        error(new SourceLocation(filename, line, 0), message);
    }
    
    public void error(int line, String message, String hint) {
        error(new SourceLocation(filename, line, 0), message, hint);
    }

    public void warning(SourceLocation location, String message) {
    if(isSuppressed(location, null)) return;
    addWarning(new DhrError(ErrorType.WARNING, location, message, null, null));
    }
    
    public void warning(SourceLocation location, String message, String hint) {
    if(isSuppressed(location, null)) return;
    addWarning(new DhrError(ErrorType.WARNING, location, message, hint, null));
    }

    public void warning(SourceLocation location, String message, String hint, ErrorCode code) {
    if(isSuppressed(location, code)) return;
    addWarning(new DhrError(ErrorType.WARNING, location, message, hint, code));
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

    private boolean colorEnabled = true;

    public void setColorEnabled(boolean enabled){
        this.colorEnabled = enabled;
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

    // Removed unused printAll() and printSummary() for leaner API.

    private void printError(DhrError error) {
        // Format the error with professional styling
        String formattedError = formatError(error);
        System.err.println(formattedError);
        
        // Show source context for better debugging
        if (sourceCode != null && !sourceCode.isEmpty()) {
            String context = getSourceContext(error.getLocation());
            if (!context.isEmpty()) {
                System.err.println(context);
            }
        }
        
        // Show helpful hints
        if (error.hasHint()) {
            if(colorEnabled)
                System.err.println("\u001B[93m💡 Hint: " + error.getHint() + "\u001B[0m");
            else
                System.err.println("Hint: " + error.getHint());
        }
        System.err.println();
    }

    private String formatError(DhrError error) {
        StringBuilder sb = new StringBuilder();
        
        // Professional error formatting with clear categorization
        if (error.getType() == ErrorType.ERROR) {
            if(colorEnabled) sb.append("\u001B[91m❌ Error:\u001B[0m "); else sb.append("Error: ");
        } else { // WARNING
            if(colorEnabled) sb.append("\u001B[93m⚠️  Warning:\u001B[0m "); else sb.append("Warning: ");
        }
        
        // Location information
        if (error.getLocation() != null) {
            if(colorEnabled) sb.append("\u001B[36m").append(error.getLocation().toString()).append("\u001B[0m"); else sb.append(error.getLocation().toString());
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
        
        // Handle EOF errors by showing context from the last available lines
        if (lineNum < 1) {
            return "";
        }
        
        // If error is beyond last line, show context from the end of file
        if (lineNum > lines.length) {
            lineNum = lines.length;
        }

        StringBuilder context = new StringBuilder();
    if(colorEnabled) context.append("\u001B[2m");
        
        int startLine = Math.max(1, lineNum - 2);
        int endLine = Math.min(lines.length, lineNum + 2);
        
        int maxLineNumWidth = String.valueOf(endLine).length();
        
        for (int i = startLine; i <= endLine; i++) {
            String line = lines[i - 1];
            String lineNumStr = String.format("%" + maxLineNumWidth + "d", i);
            
            if (i == lineNum) {
                if(colorEnabled){
                    context.append("\u001B[0m");
                    context.append("\u001B[91m→ ").append(lineNumStr).append(" │ \u001B[0m");
                    context.append("\u001B[1m").append(line).append("\u001B[0m\n");
                } else {
                    context.append("→ ").append(lineNumStr).append(" │ ").append(line).append("\n");
                }
                
                if (location.getColumn() > 0) {
                    if(colorEnabled) context.append("\u001B[91m");
                    for (int j = 0; j < maxLineNumWidth + 3; j++) context.append(" ");
                    context.append("│ ");
                    for (int j = 0; j < location.getColumn() - 1; j++) {
                        context.append(" ");
                    }
                    context.append("^");
                    if(colorEnabled) context.append("\u001B[0m");
                    context.append("\n");
                }
                if(colorEnabled) context.append("\u001B[2m");
            } else {
                context.append("  ").append(lineNumStr).append(" │ ").append(line).append("\n");
            }
        }
        if(colorEnabled) context.append("\u001B[0m"); 
        return context.toString();
    }

    public void clear() {
        errors.clear();
        warnings.clear();
    lineSuppressions.clear();
    fileSuppressions.clear();
    errorKeys.clear();
    warningKeys.clear();
    }

    // JSON diagnostics (simple manual build; avoids external deps)
    public String toJson(){
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"errors\":");
        appendArray(sb, errors);
        sb.append(',');
        sb.append("\"warnings\":");
        appendArray(sb, warnings);
        sb.append('}');
        return sb.toString();
    }
    private void appendArray(StringBuilder sb, List<DhrError> list){
        sb.append('[');
        for(int i=0;i<list.size();i++){
            if(i>0) sb.append(',');
            sb.append(errorToJson(list.get(i))); }
        sb.append(']');
    }
    private String errorToJson(DhrError e){
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        if(e.getLocation()!=null){
            sb.append("\"file\":\"").append(escape(e.getLocation().getFilename())).append('\"').append(',');
            sb.append("\"line\":").append(e.getLocation().getLine()).append(',');
            sb.append("\"column\":").append(e.getLocation().getColumn()).append(',');
        }
        sb.append("\"type\":\"").append(e.getType().name()).append('\"').append(',');
        if(e.getCode()!=null) sb.append("\"code\":\"").append(e.getCode().name()).append('\"').append(',');
        sb.append("\"message\":\"").append(escape(e.getMessage())).append('\"');
        if(e.getHint()!=null){ sb.append(',').append("\"hint\":\"").append(escape(e.getHint())).append('\"'); }
        // embed a short snippet (current line only)
        if(sourceCode!=null && e.getLocation()!=null){
            String[] lines = sourceCode.split("\n"); int ln = e.getLocation().getLine();
            if(ln>=1 && ln<=lines.length){ sb.append(',').append("\"sourceLine\":\"").append(escape(lines[ln-1])).append('\"'); }
        }
        sb.append('}');
        return sb.toString();
    }
    private String escape(String s){
        if(s==null) return ""; // null-safe
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");
    }

    public List<DhrError> getErrors() {
        return new ArrayList<>(errors);
    }

    public List<DhrError> getWarnings() {
        return new ArrayList<>(warnings);
    }

    private void parseSuppressDirectives(){
        lineSuppressions.clear();
        fileSuppressions.clear();
        if(sourceCode==null) return;
        String[] lines = sourceCode.split("\n");
        for(int i=0;i<lines.length;i++){
            String line = lines[i];
            int commentIdx = line.indexOf("//");
            if(commentIdx>=0){
                String comment = line.substring(commentIdx+2);
                int sup = comment.indexOf("@suppress");
                if(sup>=0){
                    String rest = comment.substring(sup+9).trim();
                    if(rest.startsWith(":")) rest = rest.substring(1).trim();
                    rest = rest.replaceAll("[, ]+"," ").trim();
                    if(rest.isEmpty()) continue;
                    String[] codes = rest.split(" ");
                    java.util.Set<ErrorCode> set = new java.util.HashSet<>();
                    for(String c: codes){
                        String up = c.trim().toUpperCase();
                        if(up.equals("ALL") || up.equals("ALL_WARNINGS")){ fileSuppressions.addAll(java.util.Arrays.asList(ErrorCode.values())); continue; }
                        try { set.add(ErrorCode.valueOf(up)); } catch(IllegalArgumentException ignored) {}
                    }
                    if(!set.isEmpty()){
                        // Find next non-empty, non-comment line
                        int target = i+1; // start searching after this line (0-based)
                        while(target < lines.length){
                            String nl = lines[target].trim();
                            if(!nl.isEmpty() && !nl.startsWith("//")) break;
                            target++;
                        }
                        int targetLine = Math.min(target+1, lines.length); // convert to 1-based
                        lineSuppressions.computeIfAbsent(targetLine, k-> new java.util.HashSet<>()).addAll(set);
                    }
                }
            }
        }
    }

    private boolean isSuppressed(SourceLocation loc, ErrorCode code){
        if(loc==null) return false;
        if(code!=null && fileSuppressions.contains(code)) return true;
        java.util.Set<ErrorCode> set = lineSuppressions.get(loc.getLine());
        if(set==null) return false;
        if(code==null) return !set.isEmpty();
        return set.contains(code);
    }

    // -------- de-dup helpers --------
    private String keyFor(DhrError e){
        SourceLocation l = e.getLocation();
        int line = l==null? -1 : l.getLine();
        int col = l==null? -1 : l.getColumn();
        String code = e.getCode()==null? "" : e.getCode().name();
        return e.getType()+"|"+line+"|"+col+"|"+code+"|"+e.getMessage();
    }
    private void addError(DhrError e){
        String k = keyFor(e);
        if(errorKeys.add(k)){
            errors.add(e);
        }
    }
    private void addWarning(DhrError e){
        String k = keyFor(e);
        if(warningKeys.add(k)){
            warnings.add(e);
        }
    }
}
