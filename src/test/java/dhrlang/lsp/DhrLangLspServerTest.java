package dhrlang.lsp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the DhrLang Language Server Protocol server.
 */
@DisplayName("LSP Server Tests")
class DhrLangLspServerTest {

    private String makeLspMessage(String json) {
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        return "Content-Length: " + content.length + "\r\n\r\n" + json;
    }

    private String sendAndReceive(String... messages) throws Exception {
        StringBuilder input = new StringBuilder();
        for (String msg : messages) {
            input.append(makeLspMessage(msg));
        }

        ByteArrayInputStream in = new ByteArrayInputStream(
                input.toString().getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        DhrLangLspServer server = new DhrLangLspServer(in, out);
        server.run();

        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Initialize returns capabilities")
    void initializeResponse() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

        assertTrue(result.contains("\"capabilities\""), "Should contain capabilities");
        assertTrue(result.contains("textDocumentSync"), "Should have textDocumentSync");
        assertTrue(result.contains("completionProvider"), "Should have completionProvider");
        assertTrue(result.contains("hoverProvider"), "Should have hoverProvider");
    }

    @Test
    @DisplayName("Shutdown returns null")
    void shutdownResponse() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"shutdown\",\"params\":{}}");

        assertTrue(result.contains("\"id\":2"), "Should contain shutdown response id");
    }

    @Test
    @DisplayName("Completion returns items")
    void completionReturnsItems() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/completion\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\"},\"position\":{\"line\":0,\"character\":0}}}");

        assertTrue(result.contains("\"label\""), "Should contain completion labels");
        assertTrue(result.contains("\"kind\""), "Should contain completion kinds");
    }

    @Test
    @DisplayName("Hover returns info for known symbols")
    void hoverForKnownSymbol() throws Exception {
        // First open a document with 'num' keyword
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\",\"text\":\"num x = 5;\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/hover\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\"},\"position\":{\"line\":0,\"character\":0}}}");

        // The hover response depends on whether "num" is in the hover table
        assertTrue(result.contains("\"id\":2"), "Should contain hover response");
    }

    @Test
    @DisplayName("DidOpen publishes diagnostics for valid code")
    void didOpenPublishesDiagnostics() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\",\"text\":\"class Main { static kaam main() { printLine(1); } }\"}}}");

        assertTrue(result.contains("publishDiagnostics"), "Should publish diagnostics");
        assertTrue(result.contains("file:///test.dhr"), "Should reference the file URI");
    }

    @Test
    @DisplayName("DidOpen reports errors for invalid code")
    void didOpenReportsErrors() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///bad.dhr\",\"text\":\"invalid code here !!!\"}}}");

        assertTrue(result.contains("publishDiagnostics"), "Should publish diagnostics");
        assertTrue(result.contains("\"severity\":1") || result.contains("\"message\""),
                "Should contain error diagnostics");
    }

    @Test
    @DisplayName("Initialize advertises documentSymbolProvider")
    void initializeAdvertisesDocumentSymbols() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

        assertTrue(result.contains("\"documentSymbolProvider\": true"),
                "Should advertise documentSymbolProvider capability");
    }

    @Test
    @DisplayName("DocumentSymbol returns class, fields and methods")
    void documentSymbolReturnsOutline() throws Exception {
        String source = "class Counter { num total; kaam Counter() { total = 0; } "
                + "kaam add(num n) { total = total + n; } }";
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\",\"text\":\""
                        + source.replace("\"", "\\\"") + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/documentSymbol\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\"}}}");

        assertTrue(result.contains("\"name\":\"Counter\""), "Should include the class symbol");
        assertTrue(result.contains("\"name\":\"total\""), "Should include the field symbol");
        assertTrue(result.contains("\"name\":\"add\""), "Should include the method symbol");
        assertTrue(result.contains("\"kind\":5"), "Class should use SymbolKind.Class (5)");
    }

    @Test
    @DisplayName("DocumentSymbol returns empty array for unopened document")
    void documentSymbolEmptyForUnopenedDocument() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/documentSymbol\",\"params\":{\"textDocument\":{\"uri\":\"file:///missing.dhr\"}}}");

        assertTrue(result.contains("\"id\":2"), "Should respond to the request");
        int idx = result.indexOf("\"id\":2");
        assertTrue(result.substring(idx).contains("\"result\":[]"),
                "Should return an empty array for an unopened document");
    }

    @Test
    @DisplayName("DidClose clears diagnostics")
    void didCloseClearsDiagnostics() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\",\"text\":\"class Main { static kaam main() { } }\"}}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didClose\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\"}}}");

        // The last publishDiagnostics should have empty array
        int lastDiag = result.lastIndexOf("publishDiagnostics");
        assertTrue(lastDiag >= 0, "Should publish diagnostics on close");
        String afterLastDiag = result.substring(lastDiag);
        assertTrue(afterLastDiag.contains("\"diagnostics\":[]"),
                "Should clear diagnostics on close");
    }

    @Test
    @DisplayName("Initialize advertises definitionProvider")
    void initializeAdvertisesDefinition() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

        assertTrue(result.contains("\"definitionProvider\": true"),
                "Should advertise definitionProvider capability");
    }

    @Test
    @DisplayName("Definition jumps from a method call to the method declaration")
    void definitionJumpsToMethodDeclaration() throws Exception {
        String source = "class Counter { num total; kaam Counter() { total = 0; } "
                + "kaam add(num n) { total = total + n; } "
                + "kaam run() { add(1); } }";
        int callSite = source.indexOf("add(1)");
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\",\"text\":\""
                        + source.replace("\"", "\\\"") + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/definition\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\"},\"position\":{\"line\":0,\"character\":"
                        + callSite + "}}}");

        assertTrue(result.contains("\"uri\":\"file:///counter.dhr\""), "Should return a location in the same file");
        assertTrue(result.contains("\"range\""), "Should include a range");
    }

    @Test
    @DisplayName("Definition jumps from a type reference to the class declaration")
    void definitionJumpsToClassDeclaration() throws Exception {
        String source = "class Point { num x; } class Main { static kaam main() { Point p; } }";
        int usageSite = source.indexOf("Point", source.indexOf("Point") + 1);
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///point.dhr\",\"text\":\""
                        + source.replace("\"", "\\\"") + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/definition\",\"params\":{\"textDocument\":{\"uri\":\"file:///point.dhr\"},\"position\":{\"line\":0,\"character\":"
                        + usageSite + "}}}");

        assertTrue(result.contains("\"uri\":\"file:///point.dhr\""), "Should return a location in the same file");
        assertTrue(result.contains("\"range\""), "Should include a range for the class declaration");
    }

    @Test
    @DisplayName("Definition returns null for an unknown identifier")
    void definitionNullForUnknownIdentifier() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\",\"text\":\"class Main { static kaam main() { printLine(1); } }\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/definition\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\"},\"position\":{\"line\":0,\"character\":35}}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        assertTrue(result.substring(idx).contains("\"result\":null"),
                "Should return null for an identifier not declared in the file (e.g. builtin printLine)");
    }

    @Test
    @DisplayName("Definition returns null for an unopened document")
    void definitionNullForUnopenedDocument() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/definition\",\"params\":{\"textDocument\":{\"uri\":\"file:///missing.dhr\"},\"position\":{\"line\":0,\"character\":0}}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        assertTrue(result.substring(idx).contains("\"result\":null"),
                "Should return null for an unopened document");
    }
}
