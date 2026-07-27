package dhrlang.production;

import dhrlang.ast.*;
import dhrlang.lexer.TokenType;

import java.util.*;

/**
 * Provides language server–style support for the DhrLang VSCode extension.
 *
 * <p>This class offers:
 * <ul>
 *   <li>Completion suggestions (annotations, keywords, types, snippets)</li>
 *   <li>Hover information (type docs, annotation semantics)</li>
 *   <li>Diagnostic messages (errors, warnings)</li>
 *   <li>Signature help for built-in functions</li>
 * </ul>
 *
 * <p>The methods return plain data objects that can be serialised to LSP-
 * compatible JSON by the VSCode extension TypeScript adapter.</p>
 *
 * <p><b>User story:</b> SC-506 — As a developer, I want complete VSCode integration.</p>
 */
public final class VscodeLanguageSupport {

    private VscodeLanguageSupport() {}

    // ── CompletionItem ───────────────────────────────────────────────────

    /**
     * Completion item kind.
     */
    public enum CompletionKind {
        KEYWORD,
        ANNOTATION,
        TYPE,
        SNIPPET,
        FUNCTION,
        VARIABLE
    }

    /**
     * A single completion item.
     */
    public static final class CompletionItem {
        private final String label;
        private final CompletionKind kind;
        private final String detail;
        private final String insertText;
        private final String documentation;

        public CompletionItem(String label, CompletionKind kind, String detail,
                              String insertText, String documentation) {
            this.label = label;
            this.kind = kind;
            this.detail = detail;
            this.insertText = insertText;
            this.documentation = documentation;
        }

        public String getLabel() { return label; }
        public CompletionKind getKind() { return kind; }
        public String getDetail() { return detail; }
        public String getInsertText() { return insertText; }
        public String getDocumentation() { return documentation; }
    }

    // ── HoverInfo ────────────────────────────────────────────────────────

    /**
     * Hover information for a symbol.
     */
    public static final class HoverInfo {
        private final String symbol;
        private final String type;
        private final String documentation;
        private final String example;

        public HoverInfo(String symbol, String type, String documentation, String example) {
            this.symbol = symbol;
            this.type = type;
            this.documentation = documentation;
            this.example = example;
        }

        public String getSymbol() { return symbol; }
        public String getType() { return type; }
        public String getDocumentation() { return documentation; }
        public String getExample() { return example; }

        /**
         * Render as Markdown for the VSCode hover panel.
         */
        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("**").append(symbol).append("**");
            if (type != null) sb.append(" — `").append(type).append('`');
            sb.append("\n\n");
            if (documentation != null) sb.append(documentation).append("\n\n");
            if (example != null) sb.append("```dhrlang\n").append(example).append("\n```\n");
            return sb.toString();
        }
    }

    // ── Diagnostic ───────────────────────────────────────────────────────

    /**
     * Diagnostic severity.
     */
    public enum DiagnosticSeverity {
        ERROR,
        WARNING,
        INFORMATION,
        HINT
    }

    /**
     * A single diagnostic message.
     */
    public static final class Diagnostic {
        private final int startLine;
        private final int startColumn;
        private final int endLine;
        private final int endColumn;
        private final DiagnosticSeverity severity;
        private final String message;
        private final String code;
        private final String source;

        public Diagnostic(int startLine, int startColumn, int endLine, int endColumn,
                          DiagnosticSeverity severity, String message, String code) {
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
            this.severity = severity;
            this.message = message;
            this.code = code;
            this.source = "dhrlang";
        }

        public int getStartLine() { return startLine; }
        public int getStartColumn() { return startColumn; }
        public int getEndLine() { return endLine; }
        public int getEndColumn() { return endColumn; }
        public DiagnosticSeverity getSeverity() { return severity; }
        public String getMessage() { return message; }
        public String getCode() { return code; }
        public String getSource() { return source; }
    }

    // ── SignatureInfo ────────────────────────────────────────────────────

    /**
     * Signature help for a function.
     */
    public static final class SignatureInfo {
        private final String label;
        private final String documentation;
        private final List<ParameterInfo> parameters;

        public SignatureInfo(String label, String documentation, List<ParameterInfo> parameters) {
            this.label = label;
            this.documentation = documentation;
            this.parameters = Collections.unmodifiableList(parameters);
        }

        public String getLabel() { return label; }
        public String getDocumentation() { return documentation; }
        public List<ParameterInfo> getParameters() { return parameters; }
    }

    /**
     * Information about a single parameter in a signature.
     */
    public static final class ParameterInfo {
        private final String label;
        private final String documentation;

        public ParameterInfo(String label, String documentation) {
            this.label = label;
            this.documentation = documentation;
        }

        public String getLabel() { return label; }
        public String getDocumentation() { return documentation; }
    }

    // ── Completion Providers ─────────────────────────────────────────────

    private static final List<CompletionItem> ANNOTATION_COMPLETIONS = buildAnnotationCompletions();
    private static final List<CompletionItem> KEYWORD_COMPLETIONS = buildKeywordCompletions();
    private static final List<CompletionItem> TYPE_COMPLETIONS = buildTypeCompletions();
    private static final List<CompletionItem> SNIPPET_COMPLETIONS = buildSnippetCompletions();

    /**
     * Get all completions applicable after the '@' trigger character.
     */
    public static List<CompletionItem> getAnnotationCompletions() {
        return Collections.unmodifiableList(ANNOTATION_COMPLETIONS);
    }

    /**
     * Get completions for DhrLang keywords.
     */
    public static List<CompletionItem> getKeywordCompletions() {
        return Collections.unmodifiableList(KEYWORD_COMPLETIONS);
    }

    /**
     * Get completions for DhrLang types (including blockchain types).
     */
    public static List<CompletionItem> getTypeCompletions() {
        return Collections.unmodifiableList(TYPE_COMPLETIONS);
    }

    /**
     * Get snippet completions (multi-line templates).
     */
    public static List<CompletionItem> getSnippetCompletions() {
        return Collections.unmodifiableList(SNIPPET_COMPLETIONS);
    }

    /**
     * Get all completions matching a prefix.
     */
    public static List<CompletionItem> getCompletions(String prefix) {
        List<CompletionItem> result = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);

        List<List<CompletionItem>> allLists = List.of(
                ANNOTATION_COMPLETIONS, KEYWORD_COMPLETIONS,
                TYPE_COMPLETIONS, SNIPPET_COMPLETIONS
        );
        for (List<CompletionItem> list : allLists) {
            for (CompletionItem item : list) {
                if (item.getLabel().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    // ── Hover Provider ───────────────────────────────────────────────────

    private static final Map<String, HoverInfo> HOVER_TABLE = buildHoverTable();

    /**
     * Get hover information for a symbol.
     *
     * @param symbol the word under the cursor
     * @return hover info, or null if not found
     */
    public static HoverInfo getHover(String symbol) {
        if (symbol == null) return null;
        // Try exact match
        HoverInfo info = HOVER_TABLE.get(symbol);
        if (info != null) return info;
        // Try with @ prefix for annotations
        return HOVER_TABLE.get("@" + symbol);
    }

    /**
     * Get all known hover symbols.
     */
    public static Set<String> allHoverSymbols() {
        return Collections.unmodifiableSet(HOVER_TABLE.keySet());
    }

    // ── Signature Help ───────────────────────────────────────────────────

    private static final Map<String, SignatureInfo> SIGNATURES = buildSignatures();

    /**
     * Get signature help for a function name.
     */
    public static SignatureInfo getSignatureHelp(String functionName) {
        return SIGNATURES.get(functionName);
    }

    /**
     * Get all known function signature names.
     */
    public static Set<String> allSignatureNames() {
        return Collections.unmodifiableSet(SIGNATURES.keySet());
    }

    // ── Diagnostics from Program AST ─────────────────────────────────────

    /**
     * Generate diagnostics for a program.
     */
    public static List<Diagnostic> generateDiagnostics(Program program) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (ClassDecl cls : program.getClasses()) {
            // Check for contracts without constructor
            if (cls.isContract()) {
                boolean hasConstructor = cls.getFunctions().stream()
                        .anyMatch(FunctionDecl::isContractConstructor);
                if (!hasConstructor) {
                    diagnostics.add(new Diagnostic(0, 0, 0, 0,
                            DiagnosticSeverity.WARNING,
                            "Contract '" + cls.getName() + "' has no @constructor",
                            "DHR-W001"));
                }
            }

            // Check functions
            for (FunctionDecl fn : cls.getFunctions()) {
                // View + Pure conflict
                if (fn.isView() && fn.isPure()) {
                    diagnostics.add(new Diagnostic(0, 0, 0, 0,
                            DiagnosticSeverity.ERROR,
                            "Function '" + fn.getName() + "' cannot be both @view and @pure",
                            "DHR-E510"));
                }
                // Empty function body warning
                if (fn.getBody() != null && fn.getBody().getStatements().isEmpty()
                        && !fn.hasContractAnnotation(ContractAnnotation.EVENT)) {
                    diagnostics.add(new Diagnostic(0, 0, 0, 0,
                            DiagnosticSeverity.INFORMATION,
                            "Function '" + fn.getName() + "' has an empty body",
                            "DHR-I001"));
                }
            }
        }

        return diagnostics;
    }

    // ── Builder Methods ──────────────────────────────────────────────────

    private static List<CompletionItem> buildAnnotationCompletions() {
        List<CompletionItem> items = new ArrayList<>();
        items.add(new CompletionItem("@contract", CompletionKind.ANNOTATION,
                "Smart contract class", "@contract",
                "Marks a class as a smart contract deployable to EVM."));
        items.add(new CompletionItem("@storage", CompletionKind.ANNOTATION,
                "Persistent storage field", "@storage",
                "Marks a field as on-chain persistent storage (32-byte slot)."));
        items.add(new CompletionItem("@view", CompletionKind.ANNOTATION,
                "Read-only function", "@view",
                "Function reads state but does not modify it. Free gas for external calls."));
        items.add(new CompletionItem("@pure", CompletionKind.ANNOTATION,
                "Pure function", "@pure",
                "Function has no state access. Only uses parameters and local variables."));
        items.add(new CompletionItem("@payable", CompletionKind.ANNOTATION,
                "Payable function", "@payable",
                "Function can receive ETH (msg.value > 0)."));
        items.add(new CompletionItem("@nonreentrant", CompletionKind.ANNOTATION,
                "Reentrancy guard", "@nonreentrant",
                "Adds reentrancy protection. Prevents reentrant calls."));
        items.add(new CompletionItem("@constructor", CompletionKind.ANNOTATION,
                "Contract constructor", "@constructor",
                "Called once during deployment. Initialize storage variables here."));
        items.add(new CompletionItem("@event", CompletionKind.ANNOTATION,
                "Event emitter", "@event",
                "Declares an event logged on-chain for external consumption."));
        items.add(new CompletionItem("@immutable", CompletionKind.ANNOTATION,
                "Immutable field", "@immutable",
                "Field set once in constructor, cannot be changed afterwards."));
        items.add(new CompletionItem("@invariant", CompletionKind.ANNOTATION,
                "Formal invariant", "@invariant",
                "Declares a property that must always hold (formal verification)."));
        items.add(new CompletionItem("@test", CompletionKind.ANNOTATION,
                "Test method", "@test",
                "Marks a method as a contract test discovered by the test runner."));
        items.add(new CompletionItem("@beforeEach", CompletionKind.ANNOTATION,
                "Test setup hook", "@beforeEach",
                "Run before each @test method in the contract."));
        items.add(new CompletionItem("@afterEach", CompletionKind.ANNOTATION,
                "Test teardown hook", "@afterEach",
                "Run after each @test method in the contract."));
        return items;
    }

    private static List<CompletionItem> buildKeywordCompletions() {
        List<CompletionItem> items = new ArrayList<>();
        items.add(new CompletionItem("kaam", CompletionKind.KEYWORD,
                "Function declaration", "kaam",
                "Declares a function (Hindi: \"work\")."));
        items.add(new CompletionItem("agar", CompletionKind.KEYWORD,
                "If statement", "agar",
                "Conditional statement (Hindi: \"if\")."));
        items.add(new CompletionItem("warna", CompletionKind.KEYWORD,
                "Else clause", "warna",
                "Else branch of a conditional (Hindi: \"otherwise\")."));
        items.add(new CompletionItem("jabtak", CompletionKind.KEYWORD,
                "While loop", "jabtak",
                "Loop while condition is true (Hindi: \"until\")."));
        items.add(new CompletionItem("class", CompletionKind.KEYWORD,
                "Class declaration", "class",
                "Declares a new class."));
        items.add(new CompletionItem("return", CompletionKind.KEYWORD,
                "Return statement", "return",
                "Returns a value from a function."));
        items.add(new CompletionItem("sahi", CompletionKind.KEYWORD,
                "Boolean true", "sahi",
                "Boolean literal true (Hindi: \"correct\")."));
        items.add(new CompletionItem("galat", CompletionKind.KEYWORD,
                "Boolean false", "galat",
                "Boolean literal false (Hindi: \"wrong\")."));
        items.add(new CompletionItem("naya", CompletionKind.KEYWORD,
                "New instance", "naya",
                "Create a new class instance (Hindi: \"new\")."));
        items.add(new CompletionItem("try", CompletionKind.KEYWORD,
                "Try block", "try",
                "Begin an exception-handling block."));
        items.add(new CompletionItem("catch", CompletionKind.KEYWORD,
                "Catch block", "catch",
                "Handle exceptions from a try block."));
        items.add(new CompletionItem("require", CompletionKind.KEYWORD,
                "Require assertion", "require",
                "Assert a condition; revert if false."));
        return items;
    }

    private static List<CompletionItem> buildTypeCompletions() {
        List<CompletionItem> items = new ArrayList<>();
        items.add(new CompletionItem("num", CompletionKind.TYPE,
                "Integer type", "num", "DhrLang integer type."));
        items.add(new CompletionItem("duo", CompletionKind.TYPE,
                "Double/float type", "duo", "DhrLang floating-point type."));
        items.add(new CompletionItem("kya", CompletionKind.TYPE,
                "String type", "kya", "DhrLang string type (Hindi: \"what\")."));
        items.add(new CompletionItem("sab", CompletionKind.TYPE,
                "Boolean type", "sab", "DhrLang boolean type (Hindi: \"all\")."));
        items.add(new CompletionItem("ek", CompletionKind.TYPE,
                "Single/unit type", "ek", "Small integer / char-like type."));
        items.add(new CompletionItem("Address", CompletionKind.TYPE,
                "Ethereum address (20 bytes)", "Address",
                "160-bit Ethereum account or contract address."));
        items.add(new CompletionItem("uint256", CompletionKind.TYPE,
                "Unsigned 256-bit integer", "uint256",
                "Most common type for balances and amounts."));
        items.add(new CompletionItem("int256", CompletionKind.TYPE,
                "Signed 256-bit integer", "int256",
                "Signed integer for when negative values are needed."));
        items.add(new CompletionItem("bytes32", CompletionKind.TYPE,
                "Fixed 32-byte array", "bytes32",
                "Used for hashes, identifiers, and fixed data."));
        items.add(new CompletionItem("mapping", CompletionKind.TYPE,
                "Storage mapping", "mapping",
                "Key-value storage mapping. Syntax: mapping(Key → Value)."));
        return items;
    }

    private static List<CompletionItem> buildSnippetCompletions() {
        List<CompletionItem> items = new ArrayList<>();
        items.add(new CompletionItem("contract", CompletionKind.SNIPPET,
                "New contract template",
                "@contract\nclass ${1:MyContract} {\n    @storage Address owner;\n\n" +
                "    @constructor\n    kaam init() {\n        owner = msg.sender;\n    }\n}\n",
                "Create a new smart contract with owner and constructor."));
        items.add(new CompletionItem("erc20", CompletionKind.SNIPPET,
                "ERC20 token template",
                "@contract\nclass ${1:Token} {\n    @storage uint256 totalSupply;\n" +
                "    @storage mapping(Address → uint256) balances;\n\n" +
                "    @constructor\n    kaam init(uint256 _supply) {\n" +
                "        totalSupply = _supply;\n        balances[msg.sender] = _supply;\n    }\n\n" +
                "    @view\n    kaam balanceOf(Address a) -> uint256 { return balances[a]; }\n\n" +
                "    @nonreentrant\n    kaam transfer(Address to, uint256 amt) {\n" +
                "        require(balances[msg.sender] >= amt, \"Insufficient\");\n" +
                "        balances[msg.sender] -= amt;\n        balances[to] += amt;\n    }\n}\n",
                "Create a basic ERC20 token contract."));
        items.add(new CompletionItem("function", CompletionKind.SNIPPET,
                "New function",
                "kaam ${1:myFunction}(${2:params}) {\n    ${3:// body}\n}\n",
                "Create a new function declaration."));
        items.add(new CompletionItem("test", CompletionKind.SNIPPET,
                "Test method",
                "@test\nkaam ${1:testSomething}() {\n    assertEqual(${2:expected}, ${3:actual});\n}\n",
                "Create a contract test method."));
        return items;
    }

    private static Map<String, HoverInfo> buildHoverTable() {
        Map<String, HoverInfo> table = new LinkedHashMap<>();

        // Annotations
        for (ContractAnnotation ann : ContractAnnotation.values()) {
            String example = annotationExample(ann);
            table.put(ann.getSyntax(), new HoverInfo(
                    ann.getSyntax(), "annotation",
                    annotationDoc(ann), example));
        }

        // Keywords
        table.put("kaam", new HoverInfo("kaam", "keyword",
                "Function declaration keyword (Hindi: \"work\").",
                "kaam greet() { likho(\"Namaste!\"); }"));
        table.put("agar", new HoverInfo("agar", "keyword",
                "Conditional (if) keyword (Hindi: \"if\").",
                "agar (x > 0) { likho(x); }"));
        table.put("warna", new HoverInfo("warna", "keyword",
                "Else keyword (Hindi: \"otherwise\").",
                "agar (x > 0) { likho(\"pos\"); } warna { likho(\"neg\"); }"));
        table.put("jabtak", new HoverInfo("jabtak", "keyword",
                "While loop keyword (Hindi: \"until\").",
                "jabtak (i < 10) { i = i + 1; }"));

        // Types
        table.put("Address", new HoverInfo("Address", "type",
                "20-byte Ethereum address.", "@storage Address owner;"));
        table.put("uint256", new HoverInfo("uint256", "type",
                "Unsigned 256-bit integer. Range: 0 to 2^256 - 1.",
                "@storage uint256 balance;"));
        table.put("int256", new HoverInfo("int256", "type",
                "Signed 256-bit integer. Range: -2^255 to 2^255 - 1.",
                "@storage int256 delta;"));
        table.put("bytes32", new HoverInfo("bytes32", "type",
                "Fixed-size 32-byte array, commonly used for hashes.",
                "bytes32 hash = keccak256(data);"));
        table.put("mapping", new HoverInfo("mapping", "type",
                "On-chain key-value mapping. Uses one storage slot base.",
                "@storage mapping(Address → uint256) balances;"));

        return table;
    }

    private static String annotationDoc(ContractAnnotation ann) {
        switch (ann) {
            case CONTRACT: return "Marks a class as a deployable smart contract.";
            case STORAGE: return "Persistent on-chain storage field (32-byte slot).";
            case VIEW: return "Read-only function. Does not modify state. Free gas for external calls.";
            case PURE: return "Pure computation. No state reading or writing.";
            case PAYABLE: return "Can receive ETH (msg.value > 0).";
            case NONREENTRANT: return "Adds reentrancy guard to prevent re-entrant calls.";
            case CONSTRUCTOR: return "Contract constructor. Called once at deployment.";
            case EVENT: return "Declares an event logged on-chain.";
            case IMMUTABLE: return "Field set once in constructor, then read-only.";
            case INVARIANT: return "Formal property that must always hold.";
            case TEST: return "Test method discovered by the contract test runner.";
            case BEFORE_EACH: return "Setup hook run before each @test method.";
            case AFTER_EACH: return "Teardown hook run after each @test method.";
            default: return "";
        }
    }

    private static String annotationExample(ContractAnnotation ann) {
        switch (ann) {
            case CONTRACT: return "@contract\nclass Token { ... }";
            case STORAGE: return "@storage uint256 totalSupply;";
            case VIEW: return "@view\nkaam getBalance() -> uint256 { return balance; }";
            case PURE: return "@pure\nkaam add(num a, num b) -> num { return a + b; }";
            case PAYABLE: return "@payable\nkaam deposit() { ... }";
            case NONREENTRANT: return "@nonreentrant\nkaam withdraw(uint256 amt) { ... }";
            case CONSTRUCTOR: return "@constructor\nkaam init() { owner = msg.sender; }";
            case EVENT: return "@event\nkaam Transfer(Address from, Address to, uint256 amount) {}";
            case IMMUTABLE: return "@immutable kya name;";
            case INVARIANT: return "@invariant\nkaam checkSupply() { assert(totalSupply >= 0); }";
            case TEST: return "@test\nkaam testTransfer() { assertEqual(100, balance); }";
            case BEFORE_EACH: return "@beforeEach\nkaam setup() { deploy(); }";
            case AFTER_EACH: return "@afterEach\nkaam cleanup() { reset(); }";
            default: return "";
        }
    }

    private static Map<String, SignatureInfo> buildSignatures() {
        Map<String, SignatureInfo> sigs = new LinkedHashMap<>();
        sigs.put("assertEqual", new SignatureInfo(
                "assertEqual(expected, actual)", "Assert two values are equal.",
                List.of(new ParameterInfo("expected", "The expected value"),
                        new ParameterInfo("actual", "The actual value"))));
        sigs.put("assertNotEqual", new SignatureInfo(
                "assertNotEqual(unexpected, actual)", "Assert two values differ.",
                List.of(new ParameterInfo("unexpected", "Value that should not match"),
                        new ParameterInfo("actual", "The actual value"))));
        sigs.put("assertTrue", new SignatureInfo(
                "assertTrue(condition)", "Assert condition is truthy.",
                List.of(new ParameterInfo("condition", "Should be truthy"))));
        sigs.put("assertFalse", new SignatureInfo(
                "assertFalse(condition)", "Assert condition is falsy.",
                List.of(new ParameterInfo("condition", "Should be falsy"))));
        sigs.put("require", new SignatureInfo(
                "require(condition, message)", "Revert if condition is false.",
                List.of(new ParameterInfo("condition", "Boolean guard"),
                        new ParameterInfo("message", "Revert message"))));
        return sigs;
    }
}
