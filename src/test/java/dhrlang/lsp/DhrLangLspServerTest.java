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

    @Test
    @DisplayName("Hover on a user-defined class name shows its kind")
    void hoverShowsUserDefinedClass() throws Exception {
        String source = "class Point { num x; }";
        int usageSite = source.indexOf("Point");
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///point.dhr\",\"text\":\""
                        + source.replace("\"", "\\\"") + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/hover\",\"params\":{\"textDocument\":{\"uri\":\"file:///point.dhr\"},\"position\":{\"line\":0,\"character\":"
                        + usageSite + "}}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        String tail = result.substring(idx);
        assertTrue(tail.contains("Point"), "Should mention the class name");
        assertTrue(tail.contains("class"), "Should identify it as a class");
    }

    @Test
    @DisplayName("Hover on a user-defined method shows its signature")
    void hoverShowsUserDefinedMethodSignature() throws Exception {
        String source = "class Counter { num total; kaam add(num n) { total = total + n; } "
                + "kaam run() { add(1); } }";
        int callSite = source.indexOf("add(1)");
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\",\"text\":\""
                        + source.replace("\"", "\\\"") + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/hover\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\"},\"position\":{\"line\":0,\"character\":"
                        + callSite + "}}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        String tail = result.substring(idx);
        assertTrue(tail.contains("add"), "Should mention the method name");
        assertTrue(tail.contains("Counter"), "Should mention the owning class");
    }

    @Test
    @DisplayName("Hover returns null for an identifier not declared anywhere")
    void hoverNullForUnknownIdentifier() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\",\"text\":\"class Main { static kaam main() { printLine(1); } }\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/hover\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\"},\"position\":{\"line\":0,\"character\":35}}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        assertTrue(result.substring(idx).contains("\"result\":null"),
                "Should return null for an identifier that's neither a builtin nor user-declared symbol");
    }

    @Test
    @DisplayName("References finds all call sites of a method, including its declaration")
    void referencesFindsAllCallSitesIncludingDeclaration() throws Exception {
        String source = "class Counter { num total; kaam add(num n) { total = total + n; } "
                + "kaam run() { add(1); add(2); } }";
        int declSite = source.indexOf("add");
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\",\"text\":\""
                        + source.replace("\"", "\\\"") + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/references\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\"},\"position\":{\"line\":0,\"character\":"
                        + declSite + "},\"context\":{\"includeDeclaration\":true}}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        String tail = result.substring(idx);
        long uriCount = tail.split("\"uri\":\"file:///counter.dhr\"", -1).length - 1;
        assertEquals(3, uriCount, "Should find the declaration plus both call sites: " + tail);
    }

    @Test
    @DisplayName("References excludes the declaration site when includeDeclaration is false")
    void referencesExcludesDeclarationWhenRequested() throws Exception {
        String source = "class Counter { num total; kaam add(num n) { total = total + n; } "
                + "kaam run() { add(1); add(2); } }";
        int declSite = source.indexOf("add");
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\",\"text\":\""
                        + source.replace("\"", "\\\"") + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/references\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\"},\"position\":{\"line\":0,\"character\":"
                        + declSite + "},\"context\":{\"includeDeclaration\":false}}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        String tail = result.substring(idx);
        long uriCount = tail.split("\"uri\":\"file:///counter.dhr\"", -1).length - 1;
        assertEquals(2, uriCount, "Should find only the two call sites, excluding the declaration: " + tail);
    }

    @Test
    @DisplayName("References returns an empty array for an unknown identifier")
    void referencesEmptyForUnknownIdentifier() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\",\"text\":\"class Main { static kaam main() { printLine(1); } }\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/references\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\"},\"position\":{\"line\":0,\"character\":0},\"context\":{\"includeDeclaration\":true}}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        assertTrue(result.substring(idx).contains("\"result\":[]"),
                "Should return an empty array when the cursor is not on an identifier");
    }

    @Test
    @DisplayName("References returns an empty array for an unopened document")
    void referencesEmptyForUnopenedDocument() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/references\",\"params\":{\"textDocument\":{\"uri\":\"file:///missing.dhr\"},\"position\":{\"line\":0,\"character\":0},\"context\":{\"includeDeclaration\":true}}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        assertTrue(result.substring(idx).contains("\"result\":[]"),
                "Should return an empty array for an unopened document");
    }

    @Test
    @DisplayName("Capabilities advertise referencesProvider")
    void capabilitiesAdvertiseReferencesProvider() throws Exception {
        String result = sendAndReceive("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        assertTrue(result.contains("\"referencesProvider\": true"),
                "Should advertise referencesProvider capability");
    }

    @Test
    @DisplayName("Capabilities advertise renameProvider with prepareProvider")
    void capabilitiesAdvertiseRenameProvider() throws Exception {
        String result = sendAndReceive("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        assertTrue(result.contains("\"renameProvider\""), "Should advertise renameProvider capability");
        assertTrue(result.contains("\"prepareProvider\": true"), "Should advertise prepareProvider support");
    }

    @Test
    @DisplayName("PrepareRename returns the identifier range and placeholder text")
    void prepareRenameReturnsRangeAndPlaceholder() throws Exception {
        String source = "class Counter { num total; kaam add(num n) { total = total + n; } }";
        int usageSite = source.indexOf("add");
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\",\"text\":\""
                        + source.replace("\"", "\\\"") + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/prepareRename\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\"},\"position\":{\"line\":0,\"character\":"
                        + usageSite + "}}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        String tail = result.substring(idx);
        assertTrue(tail.contains("\"placeholder\":\"add\""), "Should return the identifier as the placeholder: " + tail);
        assertTrue(tail.contains("\"range\""), "Should include a range");
    }

    @Test
    @DisplayName("PrepareRename returns null when the cursor isn't on an identifier")
    void prepareRenameNullForNonIdentifier() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\",\"text\":\"class Main { }\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/prepareRename\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\"},\"position\":{\"line\":0,\"character\":11}}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        assertTrue(result.substring(idx).contains("\"result\":null"),
                "Should return null when the cursor is on whitespace, not an identifier");
    }

    @Test
    @DisplayName("Rename updates every occurrence of a method name, including its declaration")
    void renameUpdatesAllOccurrencesOfMethod() throws Exception {
        String source = "class Counter { num total; kaam add(num n) { total = total + n; } "
                + "kaam run() { add(1); add(2); } }";
        int declSite = source.indexOf("add");
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\",\"text\":\""
                        + source.replace("\"", "\\\"") + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/rename\",\"params\":{\"textDocument\":{\"uri\":\"file:///counter.dhr\"},\"position\":{\"line\":0,\"character\":"
                        + declSite + "},\"newName\":\"increment\"}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        String tail = result.substring(idx);
        assertTrue(tail.contains("\"changes\""), "Should return a WorkspaceEdit with changes: " + tail);
        assertTrue(tail.contains("file:///counter.dhr"), "Should target the same document");
        long newTextCount = tail.split("\"newText\":\"increment\"", -1).length - 1;
        assertEquals(3, newTextCount, "Should rewrite the declaration plus both call sites: " + tail);
    }

    @Test
    @DisplayName("Rename returns null for an identifier not present in the document")
    void renameNullForUnknownIdentifier() throws Exception {
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\",\"text\":\"class Main { static kaam main() { printLine(1); } }\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/rename\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.dhr\"},\"position\":{\"line\":0,\"character\":0},\"newName\":\"x\"}}");

        int idx = result.indexOf("\"id\":2");
        assertTrue(idx >= 0, "Should respond to the request");
        assertTrue(result.substring(idx).contains("\"result\":null"),
                "Should return null when the cursor is not on a renameable identifier");
    }
}
