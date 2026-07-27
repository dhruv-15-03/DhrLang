package dhrlang.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * SC-701 — v3.0 Parameterized Annotation Model for AI Agents &amp; Data Pipelines.
 *
 * <p>Current DhrLang annotations ({@code @contract}, {@code @storage}, …) are
 * bare tokens without parameters.  The v3.0 annotation system extends this to
 * support <em>parameterised</em> annotations such as:</p>
 *
 * <pre>{@code
 *   @agent
 *   @model("gpt-4")
 *   @tools(SearchTool, ReadTool)
 *   @retry(attempts: 3)
 *   @pipeline
 *   @schedule("0 0 * * *")
 *   @source("postgres://db/orders")
 *   @sink("snowflake://analytics")
 * }</pre>
 *
 * <p>This class provides:</p>
 * <ul>
 *   <li>{@link V3Annotation} — the annotation enum</li>
 *   <li>{@link AnnotationParam} — a single key/value parameter</li>
 *   <li>{@link AnnotationDecl} — a concrete annotation instance with params</li>
 *   <li>{@link AnnotationTarget} — where an annotation may appear</li>
 *   <li>Validation helpers (required params, conflicts, targets)</li>
 * </ul>
 */
public final class AgentAnnotations {

    private AgentAnnotations() { }

    // ── Annotation Target ──────────────────────────────────────────────

    /** Where an annotation is allowed to appear. */
    public enum AnnotationTarget {
        CLASS, METHOD, FIELD
    }

    // ── Param Value Kind ───────────────────────────────────────────────

    /** The kind of value an annotation parameter holds. */
    public enum ParamKind {
        STRING, INT, BOOLEAN, IDENTIFIER, LIST
    }

    // ── V3 Annotation Enum ─────────────────────────────────────────────

    /**
     * All v3.0 annotation types.
     *
     * <p>Each value carries metadata that drives compile-time validation:
     * syntax string, allowed targets, required/optional parameter names,
     * and whether the annotation supports a single "value" shorthand
     * ({@code @model("gpt-4")} ≡ {@code @model(value: "gpt-4")}).</p>
     */
    public enum V3Annotation {
        // ── Agent annotations ──
        AGENT("@agent", targets(AnnotationTarget.CLASS), false),
        MODEL("@model", targets(AnnotationTarget.CLASS), true,
                required("value"), optional("temperature", "maxTokens")),
        TOOLS("@tools", targets(AnnotationTarget.CLASS, AnnotationTarget.METHOD), true,
                required("value")),
        RETRY("@retry", targets(AnnotationTarget.METHOD), true,
                optional("attempts", "backoffMs", "retryOn")),

        // ── Pipeline annotations ──
        PIPELINE("@pipeline", targets(AnnotationTarget.CLASS), false),
        SCHEDULE("@schedule", targets(AnnotationTarget.CLASS), true,
                required("value")),
        SOURCE("@source", targets(AnnotationTarget.FIELD), true,
                required("value"), optional("format", "schema")),
        SINK("@sink", targets(AnnotationTarget.FIELD), true,
                required("value"), optional("format", "batchSize"));

        private final String syntax;
        private final Set<AnnotationTarget> allowedTargets;
        private final boolean hasParams;
        private final List<String> requiredParams;
        private final List<String> optionalParams;

        V3Annotation(String syntax, Set<AnnotationTarget> targets,
                     boolean hasParams, String[]... paramSets) {
            this.syntax = syntax;
            this.allowedTargets = Collections.unmodifiableSet(targets);
            this.hasParams = hasParams;

            List<String> req = new ArrayList<>();
            List<String> opt = new ArrayList<>();
            for (String[] set : paramSets) {
                if (set.length > 0 && "__req__".equals(set[0])) {
                    for (int i = 1; i < set.length; i++) req.add(set[i]);
                } else if (set.length > 0 && "__opt__".equals(set[0])) {
                    for (int i = 1; i < set.length; i++) opt.add(set[i]);
                }
            }
            this.requiredParams = Collections.unmodifiableList(req);
            this.optionalParams = Collections.unmodifiableList(opt);
        }

        public String getSyntax()                        { return syntax; }
        public Set<AnnotationTarget> getAllowedTargets()  { return allowedTargets; }
        public boolean hasParams()                        { return hasParams; }
        public List<String> getRequiredParams()           { return requiredParams; }
        public List<String> getOptionalParams()           { return optionalParams; }

        /** True if the annotation is an agent-domain annotation. */
        public boolean isAgentAnnotation() {
            return this == AGENT || this == MODEL || this == TOOLS || this == RETRY;
        }

        /** True if the annotation is a pipeline-domain annotation. */
        public boolean isPipelineAnnotation() {
            return this == PIPELINE || this == SCHEDULE || this == SOURCE || this == SINK;
        }

        /** Resolve by syntax string, e.g. {@code "@agent"} → {@link #AGENT}. */
        public static V3Annotation fromSyntax(String syntax) {
            for (V3Annotation a : values()) {
                if (a.syntax.equals(syntax)) return a;
            }
            throw new IllegalArgumentException("Unknown v3 annotation: " + syntax);
        }

        /** All agent-domain annotations. */
        public static List<V3Annotation> agentAnnotations() {
            List<V3Annotation> list = new ArrayList<>();
            for (V3Annotation a : values()) if (a.isAgentAnnotation()) list.add(a);
            return list;
        }

        /** All pipeline-domain annotations. */
        public static List<V3Annotation> pipelineAnnotations() {
            List<V3Annotation> list = new ArrayList<>();
            for (V3Annotation a : values()) if (a.isPipelineAnnotation()) list.add(a);
            return list;
        }

        // ── Helper factories for param-set varargs ──

        private static Set<AnnotationTarget> targets(AnnotationTarget... ts) {
            EnumSet<AnnotationTarget> set = EnumSet.noneOf(AnnotationTarget.class);
            for (AnnotationTarget t : ts) set.add(t);
            return set;
        }
    }

    // varargs helper (package-private so enum constructor can reference)
    static String[] required(String... names) {
        String[] arr = new String[names.length + 1];
        arr[0] = "__req__";
        System.arraycopy(names, 0, arr, 1, names.length);
        return arr;
    }

    static String[] optional(String... names) {
        String[] arr = new String[names.length + 1];
        arr[0] = "__opt__";
        System.arraycopy(names, 0, arr, 1, names.length);
        return arr;
    }

    // ── Annotation Parameter ───────────────────────────────────────────

    /**
     * A single key/value parameter inside an annotation.
     *
     * <pre>{@code
     *   @model(value: "gpt-4", temperature: 0.7)
     *         ^^^^^^^^^^^^^^   ^^^^^^^^^^^^^^^^
     *         AnnotationParam  AnnotationParam
     * }</pre>
     */
    public static final class AnnotationParam {
        private final String name;
        private final Object value;
        private final ParamKind kind;

        public AnnotationParam(String name, String value) {
            this.name = Objects.requireNonNull(name);
            this.value = Objects.requireNonNull(value);
            this.kind = ParamKind.STRING;
        }

        public AnnotationParam(String name, int value) {
            this.name = Objects.requireNonNull(name);
            this.value = value;
            this.kind = ParamKind.INT;
        }

        public AnnotationParam(String name, boolean value) {
            this.name = Objects.requireNonNull(name);
            this.value = value;
            this.kind = ParamKind.BOOLEAN;
        }

        /** Constructor for identifier references (tool names, class names). */
        public static AnnotationParam identifier(String name, String id) {
            return new AnnotationParam(name, id, ParamKind.IDENTIFIER);
        }

        /** Constructor for list parameters (multiple tool names). */
        public static AnnotationParam list(String name, List<String> items) {
            return new AnnotationParam(name, Collections.unmodifiableList(new ArrayList<>(items)),
                    ParamKind.LIST);
        }

        private AnnotationParam(String name, Object value, ParamKind kind) {
            this.name = Objects.requireNonNull(name);
            this.value = Objects.requireNonNull(value);
            this.kind = kind;
        }

        public String getName()     { return name; }
        public Object getValue()    { return value; }
        public ParamKind getKind()  { return kind; }

        public String getStringValue() {
            if (kind != ParamKind.STRING && kind != ParamKind.IDENTIFIER)
                throw new IllegalStateException("Param '" + name + "' is not a string (kind=" + kind + ")");
            return (String) value;
        }

        public int getIntValue() {
            if (kind != ParamKind.INT)
                throw new IllegalStateException("Param '" + name + "' is not an int (kind=" + kind + ")");
            return (int) value;
        }

        public boolean getBooleanValue() {
            if (kind != ParamKind.BOOLEAN)
                throw new IllegalStateException("Param '" + name + "' is not a boolean (kind=" + kind + ")");
            return (boolean) value;
        }

        @SuppressWarnings("unchecked")
        public List<String> getListValue() {
            if (kind != ParamKind.LIST)
                throw new IllegalStateException("Param '" + name + "' is not a list (kind=" + kind + ")");
            return (List<String>) value;
        }

        @Override
        public String toString() {
            return name + ": " + value;
        }
    }

    // ── Annotation Declaration ─────────────────────────────────────────

    /**
     * A concrete, parsed annotation instance with its parameter values.
     *
     * <pre>{@code
     *   @model("gpt-4")         → AnnotationDecl(MODEL, {value: "gpt-4"})
     *   @retry(attempts: 3)     → AnnotationDecl(RETRY, {attempts: 3})
     *   @agent                  → AnnotationDecl(AGENT, {})
     * }</pre>
     */
    public static final class AnnotationDecl {
        private final V3Annotation type;
        private final Map<String, AnnotationParam> params;

        public AnnotationDecl(V3Annotation type) {
            this(type, Collections.emptyList());
        }

        public AnnotationDecl(V3Annotation type, List<AnnotationParam> params) {
            this.type = Objects.requireNonNull(type);
            Map<String, AnnotationParam> map = new LinkedHashMap<>();
            for (AnnotationParam p : params) {
                map.put(p.getName(), p);
            }
            this.params = Collections.unmodifiableMap(map);
        }

        public V3Annotation getType()                     { return type; }
        public Map<String, AnnotationParam> getParams()   { return params; }
        public boolean hasParam(String name)               { return params.containsKey(name); }

        public AnnotationParam getParam(String name) {
            AnnotationParam p = params.get(name);
            if (p == null) throw new IllegalArgumentException(
                    "No param '" + name + "' in " + type.getSyntax());
            return p;
        }

        public String getStringParam(String name) {
            return getParam(name).getStringValue();
        }

        public int getIntParam(String name) {
            return getParam(name).getIntValue();
        }

        public int getIntParamOrDefault(String name, int defaultValue) {
            AnnotationParam p = params.get(name);
            return (p != null) ? p.getIntValue() : defaultValue;
        }

        public String getStringParamOrDefault(String name, String defaultValue) {
            AnnotationParam p = params.get(name);
            return (p != null) ? p.getStringValue() : defaultValue;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(type.getSyntax());
            if (!params.isEmpty()) {
                sb.append('(');
                int i = 0;
                for (AnnotationParam p : params.values()) {
                    if (i++ > 0) sb.append(", ");
                    sb.append(p);
                }
                sb.append(')');
            }
            return sb.toString();
        }
    }

    // ── Validation ─────────────────────────────────────────────────────

    /**
     * A validation problem found during annotation checking.
     */
    public static final class ValidationProblem {
        public enum Level { ERROR, WARNING }

        private final Level level;
        private final String code;
        private final String message;

        public ValidationProblem(Level level, String code, String message) {
            this.level = level;
            this.code = code;
            this.message = message;
        }

        public Level getLevel()   { return level; }
        public String getCode()   { return code; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return "[" + level + " " + code + "] " + message;
        }
    }

    /**
     * Validate an annotation declaration against its target and constraints.
     *
     * @param decl   the annotation instance
     * @param target where it appears
     * @return list of validation problems (empty = valid)
     */
    public static List<ValidationProblem> validate(AnnotationDecl decl, AnnotationTarget target) {
        List<ValidationProblem> problems = new ArrayList<>();
        V3Annotation type = decl.getType();

        // Target check
        if (!type.getAllowedTargets().contains(target)) {
            problems.add(new ValidationProblem(
                    ValidationProblem.Level.ERROR, "ANN-001",
                    type.getSyntax() + " cannot be applied to " + target));
        }

        // Required params check
        for (String req : type.getRequiredParams()) {
            if (!decl.hasParam(req)) {
                problems.add(new ValidationProblem(
                        ValidationProblem.Level.ERROR, "ANN-002",
                        type.getSyntax() + " requires parameter '" + req + "'"));
            }
        }

        // Unknown params check
        Set<String> known = new java.util.HashSet<>(type.getRequiredParams());
        known.addAll(type.getOptionalParams());
        for (String key : decl.getParams().keySet()) {
            if (!known.contains(key)) {
                problems.add(new ValidationProblem(
                        ValidationProblem.Level.WARNING, "ANN-003",
                        "Unknown parameter '" + key + "' on " + type.getSyntax()));
            }
        }

        // Params on param-less annotation
        if (!type.hasParams() && !decl.getParams().isEmpty()) {
            problems.add(new ValidationProblem(
                    ValidationProblem.Level.ERROR, "ANN-004",
                    type.getSyntax() + " does not accept parameters"));
        }

        return problems;
    }

    /**
     * Validate a set of annotations for mutual compatibility on a single target.
     *
     * <ul>
     *   <li>Agent and pipeline annotations must not be mixed on the same class.</li>
     *   <li>{@code @model} and {@code @tools} require {@code @agent} on the class.</li>
     *   <li>{@code @schedule}, {@code @source}, {@code @sink} require {@code @pipeline}.</li>
     * </ul>
     */
    public static List<ValidationProblem> validateSet(
            List<AnnotationDecl> annotations, AnnotationTarget target) {
        List<ValidationProblem> problems = new ArrayList<>();

        boolean hasAgent = false, hasPipeline = false;
        boolean hasModel = false, hasTools = false;
        boolean hasSchedule = false, hasSource = false, hasSink = false;

        for (AnnotationDecl a : annotations) {
            problems.addAll(validate(a, target));
            switch (a.getType()) {
                case AGENT:    hasAgent = true; break;
                case PIPELINE: hasPipeline = true; break;
                case MODEL:    hasModel = true; break;
                case TOOLS:    hasTools = true; break;
                case SCHEDULE: hasSchedule = true; break;
                case SOURCE:   hasSource = true; break;
                case SINK:     hasSink = true; break;
                case RETRY:    break; // always valid on methods
            }
        }

        // Conflict: cannot be both agent and pipeline
        if (hasAgent && hasPipeline) {
            problems.add(new ValidationProblem(
                    ValidationProblem.Level.ERROR, "ANN-010",
                    "@agent and @pipeline cannot both appear on the same class"));
        }

        // @model/@tools require @agent context (on CLASS target)
        if (target == AnnotationTarget.CLASS) {
            if (hasModel && !hasAgent) {
                problems.add(new ValidationProblem(
                        ValidationProblem.Level.ERROR, "ANN-011",
                        "@model requires @agent on the class"));
            }
            if (hasTools && !hasAgent) {
                problems.add(new ValidationProblem(
                        ValidationProblem.Level.ERROR, "ANN-012",
                        "@tools on a class requires @agent"));
            }
            if ((hasSchedule || hasSource || hasSink) && !hasPipeline) {
                problems.add(new ValidationProblem(
                        ValidationProblem.Level.ERROR, "ANN-013",
                        "@schedule/@source/@sink require @pipeline on the class"));
            }
        }

        return problems;
    }

    /**
     * Convenience: build an AnnotationDecl with a single string "value" param.
     *
     * <pre>{@code
     *   shorthand(V3Annotation.MODEL, "gpt-4")
     *   // → @model(value: "gpt-4")
     * }</pre>
     */
    public static AnnotationDecl shorthand(V3Annotation type, String value) {
        return new AnnotationDecl(type,
                List.of(new AnnotationParam("value", value)));
    }

    /**
     * Convenience: build a bare annotation with no params.
     */
    public static AnnotationDecl bare(V3Annotation type) {
        return new AnnotationDecl(type);
    }
}
