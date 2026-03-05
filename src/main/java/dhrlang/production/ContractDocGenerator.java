package dhrlang.production;

import dhrlang.ast.*;
import dhrlang.evm.AbiGenerator;
import dhrlang.validation.StorageLayouter;
import dhrlang.validation.StorageLayouter.ContractLayout;
import dhrlang.validation.StorageLayouter.SlotInfo;

import java.util.*;

/**
 * Generates comprehensive Markdown documentation from DhrLang contract source.
 *
 * <p>Extracts annotations, function signatures, events, storage layout,
 * and generates human-readable documentation for each contract.</p>
 *
 * <p><b>User story:</b> SC-502 — As a developer, I want comprehensive documentation.</p>
 *
 * @see AbiGenerator
 * @see StorageLayouter
 */
public final class ContractDocGenerator {

    // ── Output formats ───────────────────────────────────────────────────

    /**
     * Output format for generated documentation.
     */
    public enum DocFormat {
        MARKDOWN,
        HTML,
        PLAIN_TEXT
    }

    // ── FunctionDoc ──────────────────────────────────────────────────────

    /**
     * Documentation for a single function.
     */
    public static final class FunctionDoc {
        private final String name;
        private final String returnType;
        private final List<ParamDoc> parameters;
        private final Set<String> annotations;
        private final String stateMutability;
        private final String description;

        public FunctionDoc(String name, String returnType, List<ParamDoc> parameters,
                           Set<String> annotations, String stateMutability, String description) {
            this.name = name;
            this.returnType = returnType;
            this.parameters = Collections.unmodifiableList(parameters);
            this.annotations = Collections.unmodifiableSet(annotations);
            this.stateMutability = stateMutability;
            this.description = description;
        }

        public String getName() { return name; }
        public String getReturnType() { return returnType; }
        public List<ParamDoc> getParameters() { return parameters; }
        public Set<String> getAnnotations() { return annotations; }
        public String getStateMutability() { return stateMutability; }
        public String getDescription() { return description; }
    }

    /**
     * Documentation for a function parameter.
     */
    public static final class ParamDoc {
        private final String name;
        private final String type;

        public ParamDoc(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() { return name; }
        public String getType() { return type; }
    }

    // ── StorageDoc ───────────────────────────────────────────────────────

    /**
     * Documentation for a storage variable.
     */
    public static final class StorageDoc {
        private final String name;
        private final String type;
        private final int slotIndex;
        private final Set<String> annotations;

        public StorageDoc(String name, String type, int slotIndex, Set<String> annotations) {
            this.name = name;
            this.type = type;
            this.slotIndex = slotIndex;
            this.annotations = Collections.unmodifiableSet(annotations);
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public int getSlotIndex() { return slotIndex; }
        public Set<String> getAnnotations() { return annotations; }
    }

    // ── ContractDoc ──────────────────────────────────────────────────────

    /**
     * Complete documentation for a single contract.
     */
    public static final class ContractDoc {
        private final String name;
        private final String superclass;
        private final Set<String> annotations;
        private final List<FunctionDoc> functions;
        private final List<StorageDoc> storageVariables;
        private final List<String> events;
        private final List<String> interfaces;

        public ContractDoc(String name, String superclass, Set<String> annotations,
                           List<FunctionDoc> functions, List<StorageDoc> storageVariables,
                           List<String> events, List<String> interfaces) {
            this.name = name;
            this.superclass = superclass;
            this.annotations = Collections.unmodifiableSet(annotations);
            this.functions = Collections.unmodifiableList(functions);
            this.storageVariables = Collections.unmodifiableList(storageVariables);
            this.events = Collections.unmodifiableList(events);
            this.interfaces = Collections.unmodifiableList(interfaces);
        }

        public String getName() { return name; }
        public String getSuperclass() { return superclass; }
        public Set<String> getAnnotations() { return annotations; }
        public List<FunctionDoc> getFunctions() { return functions; }
        public List<StorageDoc> getStorageVariables() { return storageVariables; }
        public List<String> getEvents() { return events; }
        public List<String> getInterfaces() { return interfaces; }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private String projectTitle = "DhrLang Smart Contracts";
    private DocFormat format = DocFormat.MARKDOWN;

    // ── Configuration ────────────────────────────────────────────────────

    public ContractDocGenerator setProjectTitle(String title) {
        this.projectTitle = title;
        return this;
    }

    public ContractDocGenerator setFormat(DocFormat format) {
        this.format = format;
        return this;
    }

    public String getProjectTitle() { return projectTitle; }
    public DocFormat getFormat() { return format; }

    // ── Documentation Generation ─────────────────────────────────────────

    /**
     * Generate documentation for a complete program.
     *
     * @param program the DhrLang program
     * @return list of {@link ContractDoc} objects
     */
    public List<ContractDoc> generateDocs(Program program) {
        List<ContractDoc> docs = new ArrayList<>();
        for (ClassDecl cls : program.getClasses()) {
            docs.add(documentContract(cls));
        }
        return docs;
    }

    /**
     * Generate documentation for a single contract class.
     */
    public ContractDoc documentContract(ClassDecl classDecl) {
        // Build class annotations
        Set<String> classAnnotations = new LinkedHashSet<>();
        for (ContractAnnotation ann : classDecl.getContractAnnotations()) {
            classAnnotations.add(ann.getSyntax());
        }

        // Build function docs
        List<FunctionDoc> functionDocs = new ArrayList<>();
        List<String> events = new ArrayList<>();
        for (FunctionDecl fn : classDecl.getFunctions()) {
            if (fn.hasContractAnnotation(ContractAnnotation.EVENT)) {
                events.add(fn.getName());
            }
            functionDocs.add(documentFunction(fn));
        }

        // Build storage variable docs
        List<StorageDoc> storageDocs = new ArrayList<>();
        int slotIndex = 0;
        for (VarDecl v : classDecl.getVariables()) {
            Set<String> varAnnotations = new LinkedHashSet<>();
            for (ContractAnnotation ann : v.getContractAnnotations()) {
                varAnnotations.add(ann.getSyntax());
            }
            int slot = v.isStorage() ? slotIndex++ : -1;
            storageDocs.add(new StorageDoc(v.getName(), v.getType(), slot, varAnnotations));
        }

        // Interfaces
        List<String> interfaces = new ArrayList<>();
        if (classDecl.getInterfaces() != null) {
            for (VariableExpr iface : classDecl.getInterfaces()) {
                interfaces.add(iface.getName().getLexeme());
            }
        }

        String superclass = classDecl.getSuperclass() != null
                ? classDecl.getSuperclass().getName().getLexeme() : null;

        return new ContractDoc(
                classDecl.getName(), superclass, classAnnotations,
                functionDocs, storageDocs, events, interfaces
        );
    }

    /**
     * Document a single function.
     */
    public FunctionDoc documentFunction(FunctionDecl fn) {
        Set<String> annotations = new LinkedHashSet<>();
        for (ContractAnnotation ann : fn.getContractAnnotations()) {
            annotations.add(ann.getSyntax());
        }

        List<ParamDoc> params = new ArrayList<>();
        for (VarDecl p : fn.getParameters()) {
            params.add(new ParamDoc(p.getName(), p.getType()));
        }

        String mutability = fn.isPure() ? "pure"
                : fn.isView() ? "view"
                : fn.isPayable() ? "payable"
                : "nonpayable";

        String description = generateFunctionDescription(fn);

        return new FunctionDoc(fn.getName(), fn.getReturnType(), params,
                annotations, mutability, description);
    }

    // ── Rendering ────────────────────────────────────────────────────────

    /**
     * Render the full documentation as a string in the configured format.
     */
    public String render(List<ContractDoc> docs) {
        switch (format) {
            case MARKDOWN: return renderMarkdown(docs);
            case HTML: return renderHtml(docs);
            case PLAIN_TEXT: return renderPlainText(docs);
            default: return renderMarkdown(docs);
        }
    }

    /**
     * Render the full documentation as Markdown.
     */
    public String renderMarkdown(List<ContractDoc> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(projectTitle).append("\n\n");
        sb.append("## Table of Contents\n\n");
        for (ContractDoc doc : docs) {
            sb.append("- [").append(doc.getName()).append("](#")
                    .append(doc.getName().toLowerCase(Locale.ROOT)).append(")\n");
        }
        sb.append('\n');

        for (ContractDoc doc : docs) {
            sb.append("---\n\n");
            sb.append("## ").append(doc.getName()).append('\n');

            if (!doc.getAnnotations().isEmpty()) {
                sb.append("\n**Annotations:** ");
                sb.append(String.join(", ", doc.getAnnotations()));
                sb.append("\n\n");
            }

            if (doc.getSuperclass() != null) {
                sb.append("**Inherits:** ").append(doc.getSuperclass()).append("\n\n");
            }

            if (!doc.getInterfaces().isEmpty()) {
                sb.append("**Implements:** ")
                        .append(String.join(", ", doc.getInterfaces()))
                        .append("\n\n");
            }

            // Storage Variables
            if (!doc.getStorageVariables().isEmpty()) {
                sb.append("### Storage Variables\n\n");
                sb.append("| Name | Type | Slot | Annotations |\n");
                sb.append("|------|------|------|-------------|\n");
                for (StorageDoc sd : doc.getStorageVariables()) {
                    sb.append("| ").append(sd.getName())
                            .append(" | ").append(sd.getType())
                            .append(" | ").append(sd.getSlotIndex() >= 0 ? sd.getSlotIndex() : "-")
                            .append(" | ").append(String.join(", ", sd.getAnnotations()))
                            .append(" |\n");
                }
                sb.append('\n');
            }

            // Functions
            if (!doc.getFunctions().isEmpty()) {
                sb.append("### Functions\n\n");
                for (FunctionDoc fd : doc.getFunctions()) {
                    sb.append("#### `").append(fd.getName()).append("`\n\n");
                    if (!fd.getAnnotations().isEmpty()) {
                        sb.append("**Modifiers:** ")
                                .append(String.join(", ", fd.getAnnotations())).append("\n\n");
                    }
                    sb.append("**State mutability:** ").append(fd.getStateMutability()).append("\n\n");

                    if (!fd.getParameters().isEmpty()) {
                        sb.append("**Parameters:**\n\n");
                        sb.append("| Name | Type |\n");
                        sb.append("|------|------|\n");
                        for (ParamDoc pd : fd.getParameters()) {
                            sb.append("| ").append(pd.getName())
                                    .append(" | ").append(pd.getType()).append(" |\n");
                        }
                        sb.append('\n');
                    }

                    if (fd.getReturnType() != null && !fd.getReturnType().equals("void")) {
                        sb.append("**Returns:** `").append(fd.getReturnType()).append("`\n\n");
                    }

                    if (fd.getDescription() != null && !fd.getDescription().isEmpty()) {
                        sb.append(fd.getDescription()).append("\n\n");
                    }
                }
            }

            // Events
            if (!doc.getEvents().isEmpty()) {
                sb.append("### Events\n\n");
                for (String event : doc.getEvents()) {
                    sb.append("- `").append(event).append("`\n");
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Render the full documentation as plain text.
     */
    public String renderPlainText(List<ContractDoc> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append(projectTitle).append("\n");
        sb.append("=".repeat(projectTitle.length())).append("\n\n");

        for (ContractDoc doc : docs) {
            sb.append("Contract: ").append(doc.getName()).append('\n');
            sb.append("-".repeat(("Contract: " + doc.getName()).length())).append('\n');

            if (doc.getSuperclass() != null) {
                sb.append("  Inherits: ").append(doc.getSuperclass()).append('\n');
            }

            for (StorageDoc sd : doc.getStorageVariables()) {
                sb.append("  Storage: ").append(sd.getType()).append(' ')
                        .append(sd.getName());
                if (sd.getSlotIndex() >= 0) sb.append(" (slot ").append(sd.getSlotIndex()).append(')');
                sb.append('\n');
            }

            for (FunctionDoc fd : doc.getFunctions()) {
                sb.append("  Function: ").append(fd.getName()).append('(');
                for (int i = 0; i < fd.getParameters().size(); i++) {
                    if (i > 0) sb.append(", ");
                    ParamDoc pd = fd.getParameters().get(i);
                    sb.append(pd.getType()).append(' ').append(pd.getName());
                }
                sb.append(')');
                if (fd.getReturnType() != null && !fd.getReturnType().equals("void")) {
                    sb.append(" -> ").append(fd.getReturnType());
                }
                sb.append(" [").append(fd.getStateMutability()).append(']');
                sb.append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Render the full documentation as HTML.
     */
    public String renderHtml(List<ContractDoc> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><title>")
                .append(escHtml(projectTitle))
                .append("</title><style>")
                .append("body{font-family:sans-serif;max-width:900px;margin:auto;padding:20px}")
                .append("table{border-collapse:collapse;width:100%}")
                .append("th,td{border:1px solid #ddd;padding:8px;text-align:left}")
                .append("th{background:#f4f4f4}")
                .append("h2{border-bottom:2px solid #333}")
                .append("code{background:#f5f5f5;padding:2px 6px;border-radius:3px}")
                .append("</style></head><body>\n");
        sb.append("<h1>").append(escHtml(projectTitle)).append("</h1>\n");

        for (ContractDoc doc : docs) {
            sb.append("<h2 id=\"").append(doc.getName().toLowerCase(Locale.ROOT))
                    .append("\">").append(escHtml(doc.getName())).append("</h2>\n");

            if (!doc.getAnnotations().isEmpty()) {
                sb.append("<p><strong>Annotations:</strong> ")
                        .append(escHtml(String.join(", ", doc.getAnnotations())))
                        .append("</p>\n");
            }

            if (!doc.getStorageVariables().isEmpty()) {
                sb.append("<h3>Storage Variables</h3>\n<table><tr><th>Name</th><th>Type</th><th>Slot</th></tr>\n");
                for (StorageDoc sd : doc.getStorageVariables()) {
                    sb.append("<tr><td>").append(escHtml(sd.getName()))
                            .append("</td><td>").append(escHtml(sd.getType()))
                            .append("</td><td>").append(sd.getSlotIndex() >= 0 ? sd.getSlotIndex() : "-")
                            .append("</td></tr>\n");
                }
                sb.append("</table>\n");
            }

            if (!doc.getFunctions().isEmpty()) {
                sb.append("<h3>Functions</h3>\n");
                for (FunctionDoc fd : doc.getFunctions()) {
                    sb.append("<h4><code>").append(escHtml(fd.getName())).append("</code></h4>\n");
                    sb.append("<p>State mutability: <code>").append(fd.getStateMutability())
                            .append("</code></p>\n");
                    if (!fd.getParameters().isEmpty()) {
                        sb.append("<table><tr><th>Param</th><th>Type</th></tr>\n");
                        for (ParamDoc pd : fd.getParameters()) {
                            sb.append("<tr><td>").append(escHtml(pd.getName()))
                                    .append("</td><td>").append(escHtml(pd.getType()))
                                    .append("</td></tr>\n");
                        }
                        sb.append("</table>\n");
                    }
                }
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Auto-generate a brief description for a function based on its annotations.
     */
    static String generateFunctionDescription(FunctionDecl fn) {
        if (fn.isContractConstructor()) {
            return "Initializes the contract state.";
        }
        if (fn.hasContractAnnotation(ContractAnnotation.EVENT)) {
            return "Emits the " + fn.getName() + " event.";
        }
        if (fn.isView()) {
            return "Read-only function that does not modify state.";
        }
        if (fn.isPure()) {
            return "Pure function with no state access.";
        }
        if (fn.isPayable()) {
            return "Payable function that can receive ETH.";
        }
        return "";
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
