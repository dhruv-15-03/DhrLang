package dhrlang.lsp;

import dhrlang.ast.ClassDecl;
import dhrlang.ast.FunctionDecl;
import dhrlang.ast.InterfaceDecl;
import dhrlang.ast.Program;
import dhrlang.ast.VarDecl;
import dhrlang.error.ErrorReporter;
import dhrlang.error.SourceLocation;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.production.VscodeLanguageSupport;
import dhrlang.typechecker.TypeChecker;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minimal stdio-based Language Server Protocol (LSP) server for DhrLang.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>initialize / initialized / shutdown</li>
 *   <li>textDocument/didOpen, didChange, didSave, didClose</li>
 *   <li>textDocument/completion</li>
 *   <li>textDocument/hover</li>
 *   <li>textDocument/documentSymbol (outline / breadcrumbs)</li>
 *   <li>textDocument/publishDiagnostics (push notifications)</li>
 * </ul>
 *
 * <p>Launch via: {@code java -jar DhrLang.jar --lsp}</p>
 */
public final class DhrLangLspServer {

    private final InputStream in;
    private final OutputStream out;
    private final Map<String, String> openDocuments = new HashMap<>();
    private boolean running = true;
    private boolean initialized = false;

    public DhrLangLspServer(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    /** Start the server main loop (blocking). */
    public void run() throws IOException {
        BufferedInputStream bis = new BufferedInputStream(in);
        while (running) {
            String message = readMessage(bis);
            if (message == null) break;
            handleMessage(message);
        }
    }

    // ── LSP message I/O ─────────────────────────────────────────────────

    private String readMessage(InputStream is) throws IOException {
        // Read headers
        int contentLength = -1;
        StringBuilder headerLine = new StringBuilder();
        while (true) {
            int b = is.read();
            if (b == -1) return null;
            headerLine.append((char) b);
            String h = headerLine.toString();
            if (h.endsWith("\r\n\r\n")) {
                // Parse Content-Length
                for (String line : h.split("\r\n")) {
                    if (line.startsWith("Content-Length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }
                break;
            }
        }
        if (contentLength <= 0) return null;
        byte[] body = new byte[contentLength];
        int read = 0;
        while (read < contentLength) {
            int n = is.read(body, read, contentLength - read);
            if (n == -1) return null;
            read += n;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private void sendMessage(String json) throws IOException {
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + content.length + "\r\n\r\n";
        synchronized (out) {
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.flush();
        }
    }

    private void sendResponse(Object id, String resultJson) throws IOException {
        String idStr = id instanceof String ? "\"" + escapeJson((String) id) + "\"" : id.toString();
        sendMessage("{\"jsonrpc\":\"2.0\",\"id\":" + idStr + ",\"result\":" + resultJson + "}");
    }

    private void sendNotification(String method, String paramsJson) throws IOException {
        sendMessage("{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + paramsJson + "}");
    }

    // ── Message dispatch ─────────────────────────────────────────────────

    private void handleMessage(String json) throws IOException {
        String method = extractString(json, "method");
        Object id = extractId(json);

        if (method == null) return;

        switch (method) {
            case "initialize" -> handleInitialize(id);
            case "initialized" -> { /* no-op */ }
            case "shutdown" -> handleShutdown(id);
            case "exit" -> { running = false; }
            case "textDocument/didOpen" -> handleDidOpen(json);
            case "textDocument/didChange" -> handleDidChange(json);
            case "textDocument/didSave" -> handleDidSave(json);
            case "textDocument/didClose" -> handleDidClose(json);
            case "textDocument/completion" -> handleCompletion(id, json);
            case "textDocument/hover" -> handleHover(id, json);
            case "textDocument/documentSymbol" -> handleDocumentSymbol(id, json);
            default -> {
                // Unknown method — send null response if it has an id
                if (id != null) {
                    sendResponse(id, "null");
                }
            }
        }
    }

    // ── Handler implementations ─────────────────────────────────────────

    private void handleInitialize(Object id) throws IOException {
        initialized = true;
        String capabilities = """
            {
                "capabilities": {
                    "textDocumentSync": 1,
                    "completionProvider": {
                        "triggerCharacters": [".", "@"]
                    },
                        "hoverProvider": true,
                        "documentSymbolProvider": true
                    }
                }""";
        sendResponse(id, capabilities);
    }

    private void handleShutdown(Object id) throws IOException {
        sendResponse(id, "null");
    }

    private void handleDidOpen(String json) throws IOException {
        String uri = extractNestedString(json, "textDocument", "uri");
        String text = extractNestedString(json, "textDocument", "text");
        if (uri != null && text != null) {
            openDocuments.put(uri, text);
            publishDiagnostics(uri, text);
        }
    }

    private void handleDidChange(String json) throws IOException {
        String uri = extractNestedString(json, "textDocument", "uri");
        // Full sync mode — extract first content change
        String text = extractContentChange(json);
        if (uri != null && text != null) {
            openDocuments.put(uri, text);
            publishDiagnostics(uri, text);
        }
    }

    private void handleDidSave(String json) throws IOException {
        String uri = extractNestedString(json, "textDocument", "uri");
        if (uri != null && openDocuments.containsKey(uri)) {
            publishDiagnostics(uri, openDocuments.get(uri));
        }
    }

    private void handleDidClose(String json) throws IOException {
        String uri = extractNestedString(json, "textDocument", "uri");
        if (uri != null) {
            openDocuments.remove(uri);
            // Clear diagnostics
            sendNotification("textDocument/publishDiagnostics",
                    "{\"uri\":\"" + escapeJson(uri) + "\",\"diagnostics\":[]}");
        }
    }

    private void handleCompletion(Object id, String json) throws IOException {
        List<VscodeLanguageSupport.CompletionItem> items = new java.util.ArrayList<>(VscodeLanguageSupport.getAnnotationCompletions());
        items.addAll(VscodeLanguageSupport.getKeywordCompletions());
        items.addAll(VscodeLanguageSupport.getTypeCompletions());

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            VscodeLanguageSupport.CompletionItem item = items.get(i);
            int kind = switch (item.getKind()) {
                case KEYWORD -> 14;
                case ANNOTATION -> 5;
                case TYPE -> 6;
                case SNIPPET -> 15;
                case FUNCTION -> 3;
                case VARIABLE -> 6;
            };
            sb.append("{\"label\":\"").append(escapeJson(item.getLabel())).append("\"");
            sb.append(",\"kind\":").append(kind);
            if (item.getDetail() != null) {
                sb.append(",\"detail\":\"").append(escapeJson(item.getDetail())).append("\"");
            }
            if (item.getInsertText() != null) {
                sb.append(",\"insertText\":\"").append(escapeJson(item.getInsertText())).append("\"");
            }
            if (item.getDocumentation() != null) {
                sb.append(",\"documentation\":\"").append(escapeJson(item.getDocumentation())).append("\"");
            }
            sb.append('}');
        }
        sb.append(']');
        sendResponse(id, sb.toString());
    }

    private void handleHover(Object id, String json) throws IOException {
        String uri = extractNestedString(json, "textDocument", "uri");
        int line = extractNestedInt(json, "position", "line");
        int character = extractNestedInt(json, "position", "character");

        String doc = openDocuments.get(uri);
        if (doc == null) {
            sendResponse(id, "null");
            return;
        }

        // Find word at position
        String word = getWordAt(doc, line, character);
        if (word == null || word.isEmpty()) {
            sendResponse(id, "null");
            return;
        }

        VscodeLanguageSupport.HoverInfo hover = VscodeLanguageSupport.getHover(word);
        if (hover == null) {
            sendResponse(id, "null");
            return;
        }

        String md = hover.toMarkdown();
        sendResponse(id, "{\"contents\":{\"kind\":\"markdown\",\"value\":\"" + escapeJson(md) + "\"}}");
    }

    // ── Document symbols (outline / breadcrumbs) ─────────────────────────

    /** LSP {@code SymbolKind} values used below. */
    private static final int SYMBOL_KIND_CLASS = 5;
    private static final int SYMBOL_KIND_METHOD = 6;
    private static final int SYMBOL_KIND_FIELD = 8;
    private static final int SYMBOL_KIND_CONSTRUCTOR = 9;
    private static final int SYMBOL_KIND_INTERFACE = 11;

    private void handleDocumentSymbol(Object id, String json) throws IOException {
        String uri = extractNestedString(json, "textDocument", "uri");
        String source = uri != null ? openDocuments.get(uri) : null;
        if (source == null) {
            sendResponse(id, "[]");
            return;
        }

        Program program;
        try {
            ErrorReporter reporter = new ErrorReporter("lsp", source);
            Lexer lexer = new Lexer(source, reporter);
            List<Token> tokens = lexer.scanTokens();
            Parser parser = new Parser(tokens, reporter);
            program = parser.parse();
        } catch (Exception e) {
            program = null;
        }

        if (program == null) {
            sendResponse(id, "[]");
            return;
        }

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ClassDecl classDecl : program.getClasses()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(classSymbolJson(classDecl));
        }
        for (InterfaceDecl interfaceDecl : program.getInterfaces()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(interfaceSymbolJson(interfaceDecl));
        }
        sb.append(']');
        sendResponse(id, sb.toString());
    }

    private String classSymbolJson(ClassDecl classDecl) {
        StringBuilder children = new StringBuilder("[");
        boolean first = true;
        for (VarDecl field : classDecl.getVariables()) {
            if (!first) children.append(',');
            first = false;
            children.append(symbolJson(field.getName(), SYMBOL_KIND_FIELD, field.getSourceLocation(), null));
        }
        for (FunctionDecl method : classDecl.getFunctions()) {
            if (!first) children.append(',');
            first = false;
            int kind = method.getName().equals(classDecl.getName()) ? SYMBOL_KIND_CONSTRUCTOR : SYMBOL_KIND_METHOD;
            children.append(symbolJson(method.getName(), kind, method.getSourceLocation(), signatureDetail(method)));
        }
        children.append(']');
        return symbolJsonWithChildren(classDecl.getName(), SYMBOL_KIND_CLASS, classDecl.getSourceLocation(), null, children.toString());
    }

    private String interfaceSymbolJson(InterfaceDecl interfaceDecl) {
        StringBuilder children = new StringBuilder("[");
        boolean first = true;
        for (FunctionDecl method : interfaceDecl.getMethods()) {
            if (!first) children.append(',');
            first = false;
            children.append(symbolJson(method.getName(), SYMBOL_KIND_METHOD, method.getSourceLocation(), signatureDetail(method)));
        }
        children.append(']');
        return symbolJsonWithChildren(interfaceDecl.getName(), SYMBOL_KIND_INTERFACE, interfaceDecl.getSourceLocation(), null, children.toString());
    }

    private String signatureDetail(FunctionDecl method) {
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (i > 0) params.append(", ");
            params.append(method.getParameters().get(i).getType());
        }
        return method.getReturnType() + "(" + params + ")";
    }

    private String symbolJson(String name, int kind, SourceLocation loc, String detail) {
        return symbolJsonWithChildren(name, kind, loc, detail, "[]");
    }

    private String symbolJsonWithChildren(String name, int kind, SourceLocation loc, String detail, String childrenJson) {
        int line = loc != null ? Math.max(0, loc.getLine() - 1) : 0;
        int startChar = loc != null ? Math.max(0, loc.getColumn() - 1) : 0;
        int endChar = startChar + Math.max(1, name.length());
        String range = "{\"start\":{\"line\":" + line + ",\"character\":" + startChar
                + "},\"end\":{\"line\":" + line + ",\"character\":" + endChar + "}}";
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"name\":\"").append(escapeJson(name)).append('"');
        if (detail != null) {
            sb.append(",\"detail\":\"").append(escapeJson(detail)).append('"');
        }
        sb.append(",\"kind\":").append(kind);
        sb.append(",\"range\":").append(range);
        sb.append(",\"selectionRange\":").append(range);
        sb.append(",\"children\":").append(childrenJson);
        sb.append('}');
        return sb.toString();
    }

    // ── Diagnostics ─────────────────────────────────────────────────────

    private void publishDiagnostics(String uri, String source) throws IOException {
        // Collect errors from lexer + parser + type checker
        ErrorReporter reporter = new ErrorReporter("lsp", source);

        try {
            Lexer lexer = new Lexer(source, reporter);
            List<Token> tokens = lexer.scanTokens();
            Parser parser = new Parser(tokens, reporter);
            Program program = parser.parse();
            if (program != null && !reporter.hasErrors()) {
                TypeChecker tc = new TypeChecker(reporter);
                tc.check(program);
            }
        } catch (Exception e) {
            // Errors are already captured in the reporter
        }

        StringBuilder sb = new StringBuilder("[");
        List<dhrlang.error.DhrError> allIssues = new ArrayList<>();
        allIssues.addAll(reporter.getErrors());
        allIssues.addAll(reporter.getWarnings());

        for (int i = 0; i < allIssues.size(); i++) {
            if (i > 0) sb.append(',');
            dhrlang.error.DhrError err = allIssues.get(i);
            SourceLocation loc = err.getLocation();
            int sl = loc != null ? Math.max(0, loc.getLine() - 1) : 0;
            int sc = loc != null ? Math.max(0, loc.getColumn() - 1) : 0;
            int severity = err.getType() == dhrlang.error.ErrorType.ERROR ? 1 : 2;
            sb.append("{\"range\":{\"start\":{\"line\":").append(sl)
              .append(",\"character\":").append(sc)
              .append("},\"end\":{\"line\":").append(sl)
              .append(",\"character\":").append(sc + 1)
              .append("}},\"severity\":").append(severity)
              .append(",\"source\":\"dhrlang\"")
              .append(",\"message\":\"").append(escapeJson(err.getMessage())).append("\"}");
        }
        sb.append(']');

        sendNotification("textDocument/publishDiagnostics",
                "{\"uri\":\"" + escapeJson(uri) + "\",\"diagnostics\":" + sb + "}");
    }

    // ── JSON helpers (minimal, no external deps) ─────────────────────────

    private static String extractString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        idx = json.indexOf(':', idx + pattern.length());
        if (idx < 0) return null;
        idx = json.indexOf('"', idx + 1);
        if (idx < 0) return null;
        int end = findEndOfString(json, idx);
        return unescapeJson(json.substring(idx + 1, end));
    }

    private static Object extractId(String json) {
        int idx = json.indexOf("\"id\"");
        if (idx < 0) return null;
        idx = json.indexOf(':', idx + 4);
        if (idx < 0) return null;
        // Skip whitespace
        idx++;
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
        if (idx >= json.length()) return null;
        char c = json.charAt(idx);
        if (c == '"') {
            int end = findEndOfString(json, idx);
            return json.substring(idx + 1, end);
        }
        // Number
        int start = idx;
        while (idx < json.length() && (Character.isDigit(json.charAt(idx)) || json.charAt(idx) == '-'))
            idx++;
        try {
            return Integer.parseInt(json.substring(start, idx));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String extractNestedString(String json, String outerKey, String innerKey) {
        String pattern = "\"" + outerKey + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int braceStart = json.indexOf('{', idx + pattern.length());
        if (braceStart < 0) return null;
        // Find nested key in the remaining JSON
        return extractString(json.substring(braceStart), innerKey);
    }

    private static int extractNestedInt(String json, String outerKey, String innerKey) {
        String pattern = "\"" + outerKey + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return 0;
        int braceStart = json.indexOf('{', idx + pattern.length());
        if (braceStart < 0) return 0;
        String sub = json.substring(braceStart);
        String inner = "\"" + innerKey + "\"";
        int iIdx = sub.indexOf(inner);
        if (iIdx < 0) return 0;
        iIdx = sub.indexOf(':', iIdx + inner.length());
        if (iIdx < 0) return 0;
        iIdx++;
        while (iIdx < sub.length() && Character.isWhitespace(sub.charAt(iIdx))) iIdx++;
        int start = iIdx;
        while (iIdx < sub.length() && (Character.isDigit(sub.charAt(iIdx)) || sub.charAt(iIdx) == '-'))
            iIdx++;
        try {
            return Integer.parseInt(sub.substring(start, iIdx));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String extractContentChange(String json) {
        // Find "contentChanges" array, then extract the first "text" field
        int idx = json.indexOf("\"contentChanges\"");
        if (idx < 0) return null;
        int arrStart = json.indexOf('[', idx);
        if (arrStart < 0) return null;
        return extractString(json.substring(arrStart), "text");
    }

    private static int findEndOfString(String json, int openQuote) {
        int i = openQuote + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') { i += 2; continue; }
            if (c == '"') return i;
            i++;
        }
        return json.length();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String getWordAt(String doc, int line, int character) {
        String[] lines = doc.split("\n", -1);
        if (line < 0 || line >= lines.length) return null;
        String l = lines[line];
        if (character < 0 || character >= l.length()) return null;
        int start = character;
        while (start > 0 && isWordChar(l.charAt(start - 1))) start--;
        int end = character;
        while (end < l.length() && isWordChar(l.charAt(end))) end++;
        return l.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '@' || c == '.';
    }

    /** Entry point for LSP mode. */
    public static void startLsp() throws IOException {
        DhrLangLspServer server = new DhrLangLspServer(System.in, System.out);
        server.run();
    }
}
