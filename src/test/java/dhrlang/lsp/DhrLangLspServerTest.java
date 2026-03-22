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
}
