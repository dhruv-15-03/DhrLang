package dhrlang.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SC-704 — Pipeline Configuration Model.
 *
 * <p>Defines the configuration layer for the DhrLang v3.0 Data Pipeline DSL.
 * A pipeline connects {@link SourceConfig data sources} to
 * {@link SinkConfig data sinks} through a series of transformation stages,
 * optionally on a {@link ScheduleConfig cron schedule}.</p>
 *
 * <pre>{@code
 *   @pipeline
 *   @schedule("0 0 * * *")
 *   class SalesAnalytics {
 *       @source("postgres://db/orders") orders;
 *       @sink("snowflake://analytics") output;
 *       kaam process() { ... }
 *   }
 * }</pre>
 */
public final class PipelineConfig {

    // ── Source Config ───────────────────────────────────────────────────

    /** Supported source connector types. */
    public enum SourceType {
        POSTGRES("postgres"), MYSQL("mysql"), MONGODB("mongodb"),
        KAFKA("kafka"), S3("s3"), HTTP("http"), FILE("file"),
        CUSTOM("custom");

        private final String protocol;
        SourceType(String protocol) { this.protocol = protocol; }
        public String getProtocol() { return protocol; }

        public static SourceType fromUri(String uri) {
            String scheme = uri.contains("://") ? uri.substring(0, uri.indexOf("://")) : "";
            for (SourceType t : values()) {
                if (t.protocol.equalsIgnoreCase(scheme)) return t;
            }
            return CUSTOM;
        }
    }

    /**
     * Configuration for a data source.
     */
    public static final class SourceConfig {
        private final String name;
        private final String uri;
        private final SourceType type;
        private final String format;
        private final String schema;
        private final Map<String, String> options;

        private SourceConfig(Builder b) {
            this.name = b.name;
            this.uri = b.uri;
            this.type = SourceType.fromUri(b.uri);
            this.format = b.format;
            this.schema = b.schema;
            this.options = Collections.unmodifiableMap(new LinkedHashMap<>(b.options));
        }

        public String getName()   { return name; }
        public String getUri()    { return uri; }
        public SourceType getType() { return type; }
        public String getFormat() { return format; }
        public String getSchema() { return schema; }
        public Map<String, String> getOptions() { return options; }

        public static Builder builder(String name, String uri) { return new Builder(name, uri); }

        public static final class Builder {
            private final String name;
            private final String uri;
            private String format = "json";
            private String schema;
            private final Map<String, String> options = new LinkedHashMap<>();

            private Builder(String name, String uri) {
                this.name = Objects.requireNonNull(name);
                this.uri = Objects.requireNonNull(uri);
            }
            public Builder format(String f)              { this.format = f; return this; }
            public Builder schema(String s)              { this.schema = s; return this; }
            public Builder option(String k, String v)    { options.put(k, v); return this; }
            public SourceConfig build()                  { return new SourceConfig(this); }
        }

        @Override public String toString() {
            return "Source[" + name + " → " + type.getProtocol() + " " + uri + "]";
        }
    }

    // ── Sink Config ────────────────────────────────────────────────────

    /** Supported sink connector types. */
    public enum SinkType {
        POSTGRES("postgres"), MYSQL("mysql"), MONGODB("mongodb"),
        KAFKA("kafka"), S3("s3"), SNOWFLAKE("snowflake"),
        BIGQUERY("bigquery"), FILE("file"), CONSOLE("console"),
        CUSTOM("custom");

        private final String protocol;
        SinkType(String protocol) { this.protocol = protocol; }
        public String getProtocol() { return protocol; }

        public static SinkType fromUri(String uri) {
            String scheme = uri.contains("://") ? uri.substring(0, uri.indexOf("://")) : "";
            for (SinkType t : values()) {
                if (t.protocol.equalsIgnoreCase(scheme)) return t;
            }
            return CUSTOM;
        }
    }

    /** Configuration for a data sink. */
    public static final class SinkConfig {
        private final String name;
        private final String uri;
        private final SinkType type;
        private final String format;
        private final int batchSize;
        private final Map<String, String> options;

        private SinkConfig(Builder b) {
            this.name = b.name;
            this.uri = b.uri;
            this.type = SinkType.fromUri(b.uri);
            this.format = b.format;
            this.batchSize = b.batchSize;
            this.options = Collections.unmodifiableMap(new LinkedHashMap<>(b.options));
        }

        public String getName()   { return name; }
        public String getUri()    { return uri; }
        public SinkType getType() { return type; }
        public String getFormat() { return format; }
        public int getBatchSize() { return batchSize; }
        public Map<String, String> getOptions() { return options; }

        public static Builder builder(String name, String uri) { return new Builder(name, uri); }

        public static final class Builder {
            private final String name;
            private final String uri;
            private String format = "json";
            private int batchSize = 1000;
            private final Map<String, String> options = new LinkedHashMap<>();

            private Builder(String name, String uri) {
                this.name = Objects.requireNonNull(name);
                this.uri = Objects.requireNonNull(uri);
            }
            public Builder format(String f)              { this.format = f; return this; }
            public Builder batchSize(int n)              { this.batchSize = n; return this; }
            public Builder option(String k, String v)    { options.put(k, v); return this; }
            public SinkConfig build()                    { return new SinkConfig(this); }
        }

        @Override public String toString() {
            return "Sink[" + name + " → " + type.getProtocol() + " " + uri + "]";
        }
    }

    // ── Schedule Config ────────────────────────────────────────────────

    /**
     * Cron-based schedule configuration.
     *
     * <p>Supports standard 5-field cron expressions: {@code minute hour dom month dow}.</p>
     */
    public static final class ScheduleConfig {
        private static final Pattern CRON_PATTERN =
                Pattern.compile("^([0-9*,/\\-]+)\\s+([0-9*,/\\-]+)\\s+"
                        + "([0-9*,/\\-]+)\\s+([0-9*,/\\-]+)\\s+([0-9*,/\\-]+)$");

        private final String cronExpression;
        private final String minute;
        private final String hour;
        private final String dayOfMonth;
        private final String month;
        private final String dayOfWeek;
        private final String timezone;

        public ScheduleConfig(String cronExpression) {
            this(cronExpression, "UTC");
        }

        public ScheduleConfig(String cronExpression, String timezone) {
            this.cronExpression = Objects.requireNonNull(cronExpression).trim();
            this.timezone = timezone;

            Matcher m = CRON_PATTERN.matcher(this.cronExpression);
            if (!m.matches()) {
                throw new IllegalArgumentException(
                        "Invalid cron expression: '" + cronExpression
                                + "'. Expected 5 fields: minute hour dom month dow");
            }
            this.minute = m.group(1);
            this.hour = m.group(2);
            this.dayOfMonth = m.group(3);
            this.month = m.group(4);
            this.dayOfWeek = m.group(5);
        }

        public String getCronExpression() { return cronExpression; }
        public String getMinute()         { return minute; }
        public String getHour()           { return hour; }
        public String getDayOfMonth()     { return dayOfMonth; }
        public String getMonth()          { return month; }
        public String getDayOfWeek()      { return dayOfWeek; }
        public String getTimezone()       { return timezone; }

        /** Human-readable description of the schedule. */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            if ("0".equals(minute) && "0".equals(hour)
                    && "*".equals(dayOfMonth) && "*".equals(month) && "*".equals(dayOfWeek)) {
                return "Daily at midnight (" + timezone + ")";
            }
            if ("*".equals(minute) && "*".equals(hour)) {
                return "Every minute (" + timezone + ")";
            }
            sb.append("Cron: ").append(cronExpression).append(" (").append(timezone).append(')');
            return sb.toString();
        }

        /** Predefined: every day at midnight. */
        public static ScheduleConfig daily() {
            return new ScheduleConfig("0 0 * * *");
        }

        /** Predefined: every hour at minute 0. */
        public static ScheduleConfig hourly() {
            return new ScheduleConfig("0 * * * *");
        }

        /** Predefined: every 5 minutes. */
        public static ScheduleConfig everyFiveMinutes() {
            return new ScheduleConfig("*/5 * * * *");
        }

        /** Predefined: Monday–Friday at 9am. */
        public static ScheduleConfig weekdaysAt9() {
            return new ScheduleConfig("0 9 * * 1-5");
        }

        @Override public String toString() {
            return "Schedule[" + cronExpression + " " + timezone + "]";
        }
    }

    // ── Stage Config ───────────────────────────────────────────────────

    /** The kind of a pipeline stage. */
    public enum StageKind {
        TRANSFORM, FILTER, AGGREGATE, JOIN, SORT, DISTINCT, LIMIT, CUSTOM
    }

    /**
     * Configuration for a single pipeline stage (one transformation step).
     */
    public static final class StageConfig {
        private final String name;
        private final StageKind kind;
        private final String expression;  // the DhrLang expression / lambda body
        private final Map<String, String> params;

        public StageConfig(String name, StageKind kind, String expression) {
            this(name, kind, expression, Collections.emptyMap());
        }

        public StageConfig(String name, StageKind kind, String expression,
                           Map<String, String> params) {
            this.name = Objects.requireNonNull(name);
            this.kind = Objects.requireNonNull(kind);
            this.expression = expression;
            this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }

        public String getName()       { return name; }
        public StageKind getKind()    { return kind; }
        public String getExpression() { return expression; }
        public Map<String, String> getParams() { return params; }

        @Override public String toString() {
            return kind + "(" + name + ")";
        }
    }

    // ── Pipeline Config (top-level) ────────────────────────────────────

    private final String pipelineName;
    private final ScheduleConfig schedule;          // nullable
    private final List<SourceConfig> sources;
    private final List<SinkConfig> sinks;
    private final List<StageConfig> stages;
    private final Map<String, String> metadata;

    private PipelineConfig(Builder b) {
        this.pipelineName = b.pipelineName;
        this.schedule = b.schedule;
        this.sources = Collections.unmodifiableList(new ArrayList<>(b.sources));
        this.sinks = Collections.unmodifiableList(new ArrayList<>(b.sinks));
        this.stages = Collections.unmodifiableList(new ArrayList<>(b.stages));
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(b.metadata));
    }

    public String getPipelineName()       { return pipelineName; }
    public ScheduleConfig getSchedule()   { return schedule; }
    public List<SourceConfig> getSources() { return sources; }
    public List<SinkConfig> getSinks()     { return sinks; }
    public List<StageConfig> getStages()   { return stages; }
    public Map<String, String> getMetadata() { return metadata; }

    /** True if the pipeline runs on a schedule (vs. ad-hoc). */
    public boolean isScheduled() { return schedule != null; }

    /** Find a source by name. */
    public SourceConfig findSource(String name) {
        for (SourceConfig s : sources) if (s.getName().equals(name)) return s;
        return null;
    }

    /** Find a sink by name. */
    public SinkConfig findSink(String name) {
        for (SinkConfig s : sinks) if (s.getName().equals(name)) return s;
        return null;
    }

    // ── Validation ─────────────────────────────────────────────────────

    /**
     * Validate the pipeline configuration.
     *
     * @return list of problems (empty = valid)
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();

        if (sources.isEmpty()) {
            problems.add("Pipeline '" + pipelineName + "' has no sources");
        }
        if (sinks.isEmpty()) {
            problems.add("Pipeline '" + pipelineName + "' has no sinks");
        }

        // Duplicate source names
        Map<String, Integer> srcNames = new LinkedHashMap<>();
        for (SourceConfig s : sources) {
            srcNames.merge(s.getName(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : srcNames.entrySet()) {
            if (e.getValue() > 1) {
                problems.add("Duplicate source name: '" + e.getKey() + "'");
            }
        }

        // Duplicate sink names
        Map<String, Integer> sinkNames = new LinkedHashMap<>();
        for (SinkConfig s : sinks) {
            sinkNames.merge(s.getName(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : sinkNames.entrySet()) {
            if (e.getValue() > 1) {
                problems.add("Duplicate sink name: '" + e.getKey() + "'");
            }
        }

        return problems;
    }

    /**
     * Render a human-readable summary of the pipeline.
     */
    public String formatSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Pipeline: ").append(pipelineName).append('\n');
        if (schedule != null) {
            sb.append("Schedule: ").append(schedule.describe()).append('\n');
        }
        sb.append("Sources (").append(sources.size()).append("):\n");
        for (SourceConfig s : sources) {
            sb.append("  ").append(s).append('\n');
        }
        sb.append("Sinks (").append(sinks.size()).append("):\n");
        for (SinkConfig s : sinks) {
            sb.append("  ").append(s).append('\n');
        }
        sb.append("Stages (").append(stages.size()).append("):\n");
        for (StageConfig s : stages) {
            sb.append("  ").append(s).append('\n');
        }
        if (!metadata.isEmpty()) {
            sb.append("Metadata:\n");
            metadata.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append('\n'));
        }
        return sb.toString();
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static Builder builder(String pipelineName) { return new Builder(pipelineName); }

    public static final class Builder {
        private final String pipelineName;
        private ScheduleConfig schedule;
        private final List<SourceConfig> sources = new ArrayList<>();
        private final List<SinkConfig> sinks = new ArrayList<>();
        private final List<StageConfig> stages = new ArrayList<>();
        private final Map<String, String> metadata = new LinkedHashMap<>();

        private Builder(String pipelineName) {
            this.pipelineName = Objects.requireNonNull(pipelineName);
        }

        public Builder schedule(ScheduleConfig s)    { this.schedule = s; return this; }
        public Builder schedule(String cron)          { this.schedule = new ScheduleConfig(cron); return this; }
        public Builder source(SourceConfig s)         { sources.add(s); return this; }
        public Builder sink(SinkConfig s)             { sinks.add(s); return this; }
        public Builder stage(StageConfig s)           { stages.add(s); return this; }
        public Builder metadata(String k, String v)   { metadata.put(k, v); return this; }

        /** Convenience: add a source from annotation-style URI. */
        public Builder source(String name, String uri) {
            return source(SourceConfig.builder(name, uri).build());
        }

        /** Convenience: add a sink from annotation-style URI. */
        public Builder sink(String name, String uri) {
            return sink(SinkConfig.builder(name, uri).build());
        }

        public PipelineConfig build() { return new PipelineConfig(this); }
    }
}
