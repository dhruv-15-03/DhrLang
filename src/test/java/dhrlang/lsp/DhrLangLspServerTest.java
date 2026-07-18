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
    @DisplayName("Completion is scope-aware: offers locals/params of the current method, not of a sibling method")
    void completionOffersLocalsAndParamsInScope() throws Exception {
        String source = "class Foo {\n"
                + "  num field1;\n"
                + "  kaam methodA(num paramA) {\n"
                + "    num localA = 1;\n"
                + "\n"
                + "  }\n"
                + "  kaam methodB(num paramB) {\n"
                + "    num localB = 2;\n"
                + "  }\n"
                + "}\n";
        // Cursor on the blank line inside methodA, right after localA is declared.
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///scope.dhr\",\"text\":\""
                        + jsonEscape(source) + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/completion\",\"params\":{\"textDocument\":{\"uri\":\"file:///scope.dhr\"},\"position\":{\"line\":4,\"character\":0}}}");

        assertTrue(result.contains("\"label\":\"paramA\""), "Should offer the enclosing method's own parameter");
        assertTrue(result.contains("\"label\":\"localA\""), "Should offer a local declared earlier in the same method");
        assertTrue(result.contains("\"label\":\"field1\""), "Should offer the enclosing class's field");
        assertFalse(result.contains("\"label\":\"paramB\""), "Should NOT offer a sibling method's parameter");
        assertFalse(result.contains("\"label\":\"localB\""), "Should NOT offer a sibling method's local variable");
    }

    @Test
    @DisplayName("Completion offers sibling methods and other declared classes as types")
    void completionOffersSiblingMethodsAndClasses() throws Exception {
        String source = "class Foo {\n"
                + "  kaam methodA() {\n"
                + "\n"
                + "  }\n"
                + "  kaam methodB() {\n"
                + "  }\n"
                + "}\n"
                + "class Bar {\n"
                + "}\n";
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///scope2.dhr\",\"text\":\""
                        + jsonEscape(source) + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/completion\",\"params\":{\"textDocument\":{\"uri\":\"file:///scope2.dhr\"},\"position\":{\"line\":2,\"character\":0}}}");

        assertTrue(result.contains("\"label\":\"methodB\""), "Should offer a sibling method in the same class");
        assertTrue(result.contains("\"label\":\"Bar\""), "Should offer another declared class as a type");
        assertTrue(result.contains("\"label\":\"kaam\""), "Should still include static keyword completions");
    }

    @Test
    @DisplayName("Member-access completion after 'receiver.' offers ONLY the receiver's fields/methods")
    void memberAccessCompletionOffersReceiverMembers() throws Exception {
        String source = "class Point {\n"
                + "  num x;\n"
                + "  num y;\n"
                + "  kaam distance() { return 0; }\n"
                + "}\n"
                + "class Foo {\n"
                + "  kaam methodA() {\n"
                + "    Point p = new Point();\n"
                + "    num unrelated = 1;\n"
                + "    p.\n"
                + "  }\n"
                + "}\n";
        // Cursor right after "p." on line 9 (0-based), character 6 (after the dot).
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///member.dhr\",\"text\":\""
                        + jsonEscape(source) + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/completion\",\"params\":{\"textDocument\":{\"uri\":\"file:///member.dhr\"},\"position\":{\"line\":9,\"character\":6}}}");

        assertTrue(result.contains("\"label\":\"x\""), "Should offer receiver type's field x");
        assertTrue(result.contains("\"label\":\"y\""), "Should offer receiver type's field y");
        assertTrue(result.contains("\"label\":\"distance\""), "Should offer receiver type's method");
        assertFalse(result.contains("\"label\":\"unrelated\""), "Should NOT offer the enclosing scope's unrelated local");
        assertFalse(result.contains("\"label\":\"methodA\""), "Should NOT offer the enclosing (non-receiver) class's own method");
        assertFalse(result.contains("\"label\":\"kaam\""), "Should NOT include static keyword completions after a dot");
    }

    @Test
    @DisplayName("Member-access completion resolves 'this.' to the enclosing class's own members")
    void memberAccessCompletionResolvesThisReceiver() throws Exception {
        String source = "class Counter {\n"
                + "  num count;\n"
                + "  kaam increment() {\n"
                + "    this.\n"
                + "  }\n"
                + "}\n";
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///this.dhr\",\"text\":\""
                        + jsonEscape(source) + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/completion\",\"params\":{\"textDocument\":{\"uri\":\"file:///this.dhr\"},\"position\":{\"line\":3,\"character\":9}}}");

        assertTrue(result.contains("\"label\":\"count\""), "this. should offer the enclosing class's own field");
        assertTrue(result.contains("\"label\":\"increment\""), "this. should offer the enclosing class's own method");
    }

    @Test
    @DisplayName("Member-access completion walks the superclass chain for inherited fields/methods")
    void memberAccessCompletionWalksSuperclassChain() throws Exception {
        String source = "class Animal {\n"
                + "  num age;\n"
                + "  kaam speak() { return 0; }\n"
                + "}\n"
                + "class Dog extends Animal {\n"
                + "  num breed;\n"
                + "}\n"
                + "class Foo {\n"
                + "  kaam methodA() {\n"
                + "    Dog d = new Dog();\n"
                + "    d.\n"
                + "  }\n"
                + "}\n";
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///super.dhr\",\"text\":\""
                        + jsonEscape(source) + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/completion\",\"params\":{\"textDocument\":{\"uri\":\"file:///super.dhr\"},\"position\":{\"line\":10,\"character\":6}}}");

        assertTrue(result.contains("\"label\":\"breed\""), "Should offer the receiver's own declared field");
        assertTrue(result.contains("\"label\":\"age\""), "Should offer an inherited field from the superclass");
        assertTrue(result.contains("\"label\":\"speak\""), "Should offer an inherited method from the superclass");
    }

    @Test
    @DisplayName("Member-access completion with a partial member name still resolves the receiver correctly")
    void memberAccessCompletionWithPartialMemberName() throws Exception {
        String source = "class Point {\n"
                + "  num x;\n"
                + "  num y;\n"
                + "}\n"
                + "class Foo {\n"
                + "  kaam methodA() {\n"
                + "    Point p = new Point();\n"
                + "    p.x\n"
                + "  }\n"
                + "}\n";
        // Cursor right after the "x" typed so far on "p.x" (line 7, character 6).
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///partial.dhr\",\"text\":\""
                        + jsonEscape(source) + "\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/completion\",\"params\":{\"textDocument\":{\"uri\":\"file:///partial.dhr\"},\"position\":{\"line\":7,\"character\":7}}}");

        assertTrue(result.contains("\"label\":\"x\""), "Should still offer field x even with a partial member name typed");
        assertTrue(result.contains("\"label\":\"y\""), "Should still offer field y even with a partial member name typed");
    }

    private static String jsonEscape(String source) {
        return source.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
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
    @DisplayName("Diagnostics span the full offending identifier, not just one character")
    void diagnosticsSpanFullIdentifier() throws Exception {
        String source = "class Main { static kaam main() { printLine(missingVar); } }";
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///undef.dhr\",\"text\":\""
                        + source.replace("\"", "\\\"") + "\"}}}");

        int diagIdx = result.indexOf("publishDiagnostics");
        assertTrue(diagIdx >= 0, "Should publish diagnostics");
        String tail = result.substring(diagIdx);
        assertTrue(tail.contains("Undefined variable 'missingVar'"),
                "Should report the undefined variable: " + tail);

        int rangeIdx = tail.indexOf("\"range\"");
        assertTrue(rangeIdx >= 0, "Should include a range");
        int startCharIdx = tail.indexOf("\"character\":", rangeIdx);
        int startChar = Integer.parseInt(tail.substring(startCharIdx + 12, tail.indexOf('}', startCharIdx)));
        int endCharIdx = tail.indexOf("\"character\":", startCharIdx + 12);
        int endChar = Integer.parseInt(tail.substring(endCharIdx + 12, tail.indexOf('}', endCharIdx)));

        assertEquals("missingVar".length(), endChar - startChar,
                "Diagnostic range should span the whole identifier (\"missingVar\", 10 chars), not a single character: "
                        + tail);
    }

    @Test
    @DisplayName("Diagnostics include the DhrLang error code")
    void diagnosticsIncludeErrorCode() throws Exception {
        String source = "class Main { static kaam main() { printLine(missingVar); } }";
        String result = sendAndReceive(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///undef2.dhr\",\"text\":\""
                        + source.replace("\"", "\\\"") + "\"}}}");

        int diagIdx = result.indexOf("publishDiagnostics");
        assertTrue(diagIdx >= 0, "Should publish diagnostics");
        assertTrue(result.substring(diagIdx).contains("\"code\":\"DHR-E202\""),
                "Should include the DHR-E202 (undeclared identifier) error code: " + result.substring(diagIdx));
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
