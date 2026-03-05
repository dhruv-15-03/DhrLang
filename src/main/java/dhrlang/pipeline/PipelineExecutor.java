package dhrlang.pipeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * SC-705 — Pipeline Stage Executor.
 *
 * <p>Provides the runtime execution engine for DhrLang v3.0 data pipelines.
 * {@link PipelineConfig} defines <em>what</em> the pipeline does; this class
 * executes it — reading from sources, applying transforms, and writing to
 * sinks.</p>
 *
 * <h3>Core concepts</h3>
 * <ul>
 *   <li>{@link DataRecord} — a single key/value row</li>
 *   <li>{@link DataBatch} — a batch of records</li>
 *   <li>{@link TransformOp} — the kind of transformation</li>
 *   <li>{@link Transform} — a concrete transformation with predicate/function</li>
 *   <li>{@link StageResult} — outcome of executing one stage</li>
 *   <li>{@link PipelineResult} — outcome of executing the whole pipeline</li>
 * </ul>
 */
public final class PipelineExecutor {

    // ── Data Record ────────────────────────────────────────────────────

    /**
     * A single data record — essentially a {@code Map<String, Object>}.
     */
    public static final class DataRecord {
        private final Map<String, Object> fields;

        public DataRecord(Map<String, Object> fields) {
            this.fields = new LinkedHashMap<>(Objects.requireNonNull(fields));
        }

        public Map<String, Object> getFields() {
            return Collections.unmodifiableMap(fields);
        }

        public Object get(String key) { return fields.get(key); }

        public String getString(String key) {
            Object v = fields.get(key);
            return v != null ? v.toString() : null;
        }

        public int getInt(String key) {
            Object v = fields.get(key);
            if (v instanceof Number) return ((Number) v).intValue();
            throw new IllegalStateException("Field '" + key + "' is not a number");
        }

        public double getDouble(String key) {
            Object v = fields.get(key);
            if (v instanceof Number) return ((Number) v).doubleValue();
            throw new IllegalStateException("Field '" + key + "' is not a number");
        }

        public boolean hasField(String key) { return fields.containsKey(key); }

        /** Create a new record with an additional/replaced field. */
        public DataRecord with(String key, Object value) {
            Map<String, Object> copy = new LinkedHashMap<>(fields);
            copy.put(key, value);
            return new DataRecord(copy);
        }

        /** Create a record with only the specified fields. */
        public DataRecord project(List<String> keys) {
            Map<String, Object> projected = new LinkedHashMap<>();
            for (String k : keys) {
                if (fields.containsKey(k)) projected.put(k, fields.get(k));
            }
            return new DataRecord(projected);
        }

        @Override public String toString() {
            return fields.toString();
        }
    }

    // ── Data Batch ─────────────────────────────────────────────────────

    /** A collection of data records. */
    public static final class DataBatch {
        private final List<DataRecord> records;

        public DataBatch(List<DataRecord> records) {
            this.records = new ArrayList<>(Objects.requireNonNull(records));
        }

        public static DataBatch empty() { return new DataBatch(Collections.emptyList()); }

        public static DataBatch of(DataRecord... records) {
            return new DataBatch(List.of(records));
        }

        public List<DataRecord> getRecords() {
            return Collections.unmodifiableList(records);
        }

        public int size()      { return records.size(); }
        public boolean isEmpty() { return records.isEmpty(); }

        public DataRecord get(int index) { return records.get(index); }

        @Override public String toString() {
            return "DataBatch[" + records.size() + " records]";
        }
    }

    // ── Transform Operations ───────────────────────────────────────────

    /** The type of transformation to apply. */
    public enum TransformOp {
        FILTER, MAP, FLAT_MAP, AGGREGATE, JOIN, SORT, DISTINCT, LIMIT, PROJECT
    }

    /**
     * A concrete data transformation step.
     */
    public static final class Transform {
        private final String name;
        private final TransformOp op;
        private final Predicate<DataRecord> filterPredicate;
        private final Function<DataRecord, DataRecord> mapFunction;
        private final int limitCount;
        private final String sortKey;
        private final boolean sortAscending;
        private final List<String> projectFields;
        private final AggregateSpec aggregateSpec;

        private Transform(Builder b) {
            this.name = b.name;
            this.op = b.op;
            this.filterPredicate = b.filterPredicate;
            this.mapFunction = b.mapFunction;
            this.limitCount = b.limitCount;
            this.sortKey = b.sortKey;
            this.sortAscending = b.sortAscending;
            this.projectFields = b.projectFields == null ? null
                    : Collections.unmodifiableList(new ArrayList<>(b.projectFields));
            this.aggregateSpec = b.aggregateSpec;
        }

        public String getName()        { return name; }
        public TransformOp getOp()     { return op; }

        // ── Factory methods ──

        /** Filter records by a predicate. */
        public static Transform filter(String name, Predicate<DataRecord> pred) {
            Builder b = new Builder(name, TransformOp.FILTER);
            b.filterPredicate = pred;
            return b.build();
        }

        /** Map each record through a function. */
        public static Transform map(String name, Function<DataRecord, DataRecord> fn) {
            Builder b = new Builder(name, TransformOp.MAP);
            b.mapFunction = fn;
            return b.build();
        }

        /** Take at most N records. */
        public static Transform limit(String name, int n) {
            Builder b = new Builder(name, TransformOp.LIMIT);
            b.limitCount = n;
            return b.build();
        }

        /** Sort records by a key. */
        public static Transform sort(String name, String key, boolean ascending) {
            Builder b = new Builder(name, TransformOp.SORT);
            b.sortKey = key;
            b.sortAscending = ascending;
            return b.build();
        }

        /** Remove duplicate records (by all fields). */
        public static Transform distinct(String name) {
            return new Builder(name, TransformOp.DISTINCT).build();
        }

        /** Project (select) specific fields. */
        public static Transform project(String name, List<String> fields) {
            Builder b = new Builder(name, TransformOp.PROJECT);
            b.projectFields = fields;
            return b.build();
        }

        /** Aggregate records. */
        public static Transform aggregate(String name, AggregateSpec spec) {
            Builder b = new Builder(name, TransformOp.AGGREGATE);
            b.aggregateSpec = spec;
            return b.build();
        }

        /** Apply this transform to a batch, producing a new batch. */
        public DataBatch apply(DataBatch input) {
            switch (op) {
                case FILTER:
                    return new DataBatch(
                            input.getRecords().stream()
                                    .filter(filterPredicate)
                                    .collect(Collectors.toList()));
                case MAP:
                    return new DataBatch(
                            input.getRecords().stream()
                                    .map(mapFunction)
                                    .collect(Collectors.toList()));
                case LIMIT:
                    return new DataBatch(
                            input.getRecords().stream()
                                    .limit(limitCount)
                                    .collect(Collectors.toList()));
                case SORT:
                    List<DataRecord> sorted = new ArrayList<>(input.getRecords());
                    sorted.sort((a, b) -> {
                        Comparable<Object> va = toComparable(a.get(sortKey));
                        Comparable<Object> vb = toComparable(b.get(sortKey));
                        if (va == null && vb == null) return 0;
                        if (va == null) return sortAscending ? -1 : 1;
                        if (vb == null) return sortAscending ? 1 : -1;
                        int cmp = va.compareTo(b.get(sortKey));
                        return sortAscending ? cmp : -cmp;
                    });
                    return new DataBatch(sorted);
                case DISTINCT:
                    List<DataRecord> seen = new ArrayList<>();
                    List<Map<String, Object>> fieldSets = new ArrayList<>();
                    for (DataRecord r : input.getRecords()) {
                        if (!fieldSets.contains(r.getFields())) {
                            fieldSets.add(r.getFields());
                            seen.add(r);
                        }
                    }
                    return new DataBatch(seen);
                case PROJECT:
                    return new DataBatch(
                            input.getRecords().stream()
                                    .map(r -> r.project(projectFields))
                                    .collect(Collectors.toList()));
                case AGGREGATE:
                    return applyAggregate(input);
                default:
                    return input;
            }
        }

        @SuppressWarnings("unchecked")
        private static Comparable<Object> toComparable(Object o) {
            if (o instanceof Comparable) return (Comparable<Object>) o;
            return null;
        }

        private DataBatch applyAggregate(DataBatch input) {
            if (aggregateSpec == null) return input;
            // Group by groupKey, apply aggregation function
            Map<Object, List<DataRecord>> groups = new LinkedHashMap<>();
            for (DataRecord r : input.getRecords()) {
                Object key = aggregateSpec.groupBy != null ? r.get(aggregateSpec.groupBy) : "__all__";
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }

            List<DataRecord> results = new ArrayList<>();
            for (Map.Entry<Object, List<DataRecord>> entry : groups.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                if (aggregateSpec.groupBy != null) {
                    row.put(aggregateSpec.groupBy, entry.getKey());
                }
                switch (aggregateSpec.function) {
                    case COUNT:
                        row.put(aggregateSpec.outputField, entry.getValue().size());
                        break;
                    case SUM:
                        double sum = entry.getValue().stream()
                                .mapToDouble(r -> r.getDouble(aggregateSpec.valueField))
                                .sum();
                        row.put(aggregateSpec.outputField, sum);
                        break;
                    case AVG:
                        double avg = entry.getValue().stream()
                                .mapToDouble(r -> r.getDouble(aggregateSpec.valueField))
                                .average().orElse(0);
                        row.put(aggregateSpec.outputField, avg);
                        break;
                    case MIN:
                        double min = entry.getValue().stream()
                                .mapToDouble(r -> r.getDouble(aggregateSpec.valueField))
                                .min().orElse(0);
                        row.put(aggregateSpec.outputField, min);
                        break;
                    case MAX:
                        double max = entry.getValue().stream()
                                .mapToDouble(r -> r.getDouble(aggregateSpec.valueField))
                                .max().orElse(0);
                        row.put(aggregateSpec.outputField, max);
                        break;
                }
                results.add(new DataRecord(row));
            }
            return new DataBatch(results);
        }

        private static final class Builder {
            final String name;
            final TransformOp op;
            Predicate<DataRecord> filterPredicate;
            Function<DataRecord, DataRecord> mapFunction;
            int limitCount;
            String sortKey;
            boolean sortAscending = true;
            List<String> projectFields;
            AggregateSpec aggregateSpec;

            Builder(String name, TransformOp op) {
                this.name = name;
                this.op = op;
            }
            Transform build() { return new Transform(this); }
        }
    }

    // ── Aggregate Spec ─────────────────────────────────────────────────

    public enum AggregateFunction { COUNT, SUM, AVG, MIN, MAX }

    /** Specification for an aggregation operation. */
    public static final class AggregateSpec {
        public final String groupBy;      // nullable — null means aggregate all
        public final String valueField;   // field to aggregate
        public final AggregateFunction function;
        public final String outputField;

        public AggregateSpec(String groupBy, String valueField,
                             AggregateFunction function, String outputField) {
            this.groupBy = groupBy;
            this.valueField = valueField;
            this.function = function;
            this.outputField = outputField;
        }
    }

    // ── Stage Result ───────────────────────────────────────────────────

    /** The outcome of executing one pipeline stage. */
    public static final class StageResult {
        public enum Status { SUCCESS, FAILED, SKIPPED }

        private final String stageName;
        private final Status status;
        private final DataBatch output;
        private final int inputCount;
        private final int outputCount;
        private final long durationMs;
        private final String errorMessage;

        public StageResult(String stageName, Status status, DataBatch output,
                           int inputCount, int outputCount, long durationMs,
                           String errorMessage) {
            this.stageName = stageName;
            this.status = status;
            this.output = output;
            this.inputCount = inputCount;
            this.outputCount = outputCount;
            this.durationMs = durationMs;
            this.errorMessage = errorMessage;
        }

        public String getStageName()   { return stageName; }
        public Status getStatus()      { return status; }
        public DataBatch getOutput()   { return output; }
        public int getInputCount()     { return inputCount; }
        public int getOutputCount()    { return outputCount; }
        public long getDurationMs()    { return durationMs; }
        public String getErrorMessage() { return errorMessage; }

        @Override public String toString() {
            return stageName + " [" + status + "] " + inputCount + " → " + outputCount;
        }
    }

    // ── Pipeline Result ────────────────────────────────────────────────

    /** The outcome of executing an entire pipeline. */
    public static final class PipelineResult {
        public enum Status { SUCCESS, PARTIAL_SUCCESS, FAILED }

        private final String pipelineName;
        private final String executionId;
        private final Status status;
        private final List<StageResult> stageResults;
        private final DataBatch finalOutput;
        private final long totalDurationMs;
        private final Instant startTime;
        private final String errorMessage;

        public PipelineResult(String pipelineName, Status status,
                              List<StageResult> stageResults, DataBatch finalOutput,
                              long totalDurationMs, Instant startTime, String errorMessage) {
            this.pipelineName = pipelineName;
            this.executionId = UUID.randomUUID().toString().substring(0, 8);
            this.status = status;
            this.stageResults = Collections.unmodifiableList(new ArrayList<>(stageResults));
            this.finalOutput = finalOutput;
            this.totalDurationMs = totalDurationMs;
            this.startTime = startTime;
            this.errorMessage = errorMessage;
        }

        public String getPipelineName()          { return pipelineName; }
        public String getExecutionId()           { return executionId; }
        public Status getStatus()                { return status; }
        public List<StageResult> getStageResults() { return stageResults; }
        public DataBatch getFinalOutput()        { return finalOutput; }
        public long getTotalDurationMs()         { return totalDurationMs; }
        public Instant getStartTime()            { return startTime; }
        public String getErrorMessage()          { return errorMessage; }
        public boolean isSuccess()               { return status == Status.SUCCESS; }

        public String formatSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Pipeline Execution: ").append(pipelineName).append(" ===\n");
            sb.append("Execution ID: ").append(executionId).append('\n');
            sb.append("Status:       ").append(status).append('\n');
            sb.append("Duration:     ").append(totalDurationMs).append("ms\n");
            sb.append("Started:      ").append(startTime).append('\n');
            sb.append("Stages:\n");
            for (StageResult sr : stageResults) {
                sb.append("  ").append(sr).append(" (").append(sr.getDurationMs()).append("ms)\n");
            }
            if (finalOutput != null) {
                sb.append("Output:       ").append(finalOutput.size()).append(" records\n");
            }
            if (errorMessage != null) {
                sb.append("Error:        ").append(errorMessage).append('\n');
            }
            return sb.toString();
        }
    }

    // ── Executor ───────────────────────────────────────────────────────

    private boolean stopOnError = true;

    public void setStopOnError(boolean stop) { this.stopOnError = stop; }
    public boolean isStopOnError()           { return stopOnError; }

    /**
     * Execute a pipeline against provided input data.
     *
     * <p>Each transform in the config's stages is applied in order.
     * If a stage fails and {@link #isStopOnError()} is true, execution halts.</p>
     *
     * @param config pipeline configuration
     * @param transforms ordered transforms to apply
     * @param inputData initial data batch (representing source read)
     * @return pipeline execution result
     */
    public PipelineResult execute(PipelineConfig config,
                                   List<Transform> transforms,
                                   DataBatch inputData) {
        Instant start = Instant.now();
        long t0 = System.currentTimeMillis();
        List<StageResult> stageResults = new ArrayList<>();
        DataBatch current = inputData;
        boolean failed = false;

        for (Transform transform : transforms) {
            long st0 = System.currentTimeMillis();
            int inCount = current.size();

            try {
                DataBatch result = transform.apply(current);
                long elapsed = System.currentTimeMillis() - st0;
                stageResults.add(new StageResult(
                        transform.getName(), StageResult.Status.SUCCESS,
                        result, inCount, result.size(), elapsed, null));
                current = result;
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - st0;
                stageResults.add(new StageResult(
                        transform.getName(), StageResult.Status.FAILED,
                        DataBatch.empty(), inCount, 0, elapsed, e.getMessage()));
                failed = true;
                if (stopOnError) break;
            }
        }

        long totalMs = System.currentTimeMillis() - t0;
        PipelineResult.Status status;
        if (failed && stopOnError) {
            status = PipelineResult.Status.FAILED;
        } else if (failed) {
            status = PipelineResult.Status.PARTIAL_SUCCESS;
        } else {
            status = PipelineResult.Status.SUCCESS;
        }

        return new PipelineResult(
                config.getPipelineName(), status, stageResults,
                current, totalMs, start, failed ? "One or more stages failed" : null);
    }

    /**
     * Execute transforms without a full PipelineConfig (convenience for testing).
     */
    public DataBatch executeTransforms(List<Transform> transforms, DataBatch input) {
        DataBatch current = input;
        for (Transform t : transforms) {
            current = t.apply(current);
        }
        return current;
    }

    // ── Utility: record builders ───────────────────────────────────────

    /** Build a single data record from alternating key/value pairs. */
    public static DataRecord record(Object... keyValues) {
        if (keyValues.length % 2 != 0)
            throw new IllegalArgumentException("Must provide key/value pairs");
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            fields.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return new DataRecord(fields);
    }

    /** Build a batch from multiple records. */
    public static DataBatch batch(DataRecord... records) {
        return DataBatch.of(records);
    }
}
