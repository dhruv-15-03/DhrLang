package dhrlang.pipeline;

import dhrlang.agent.AgentPlanner;
import dhrlang.agent.AgentRuntime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SC-706 — Agent-Powered Intelligent Pipelines.
 *
 * <p>Bridges the agent framework ({@code dhrlang.agent}) with the pipeline
 * framework ({@code dhrlang.pipeline}), enabling AI-powered data
 * transformation, quality gating, anomaly detection, and adaptive
 * pipeline orchestration.</p>
 *
 * <h3>Key concepts</h3>
 * <ul>
 *   <li>{@link AgentTransform} — a pipeline transform powered by an agent</li>
 *   <li>{@link QualityGate} — agent-evaluated data quality checkpoints</li>
 *   <li>{@link AnomalyDetector} — agent-based anomaly detection on data</li>
 *   <li>{@link IntelligentPipeline} — full pipeline with agent integration</li>
 * </ul>
 *
 * <pre>{@code
 *   // Build an intelligent pipeline
 *   IntelligentPipeline ip = IntelligentPipeline.builder("SmartETL")
 *       .pipelineConfig(config)
 *       .agentRuntime(runtime)
 *       .addQualityGate(gate)
 *       .addAnomalyDetector(detector)
 *       .build();
 *   IntelligentPipelineResult result = ip.execute(inputBatch);
 * }</pre>
 */
public final class AgentPipelineIntegration {

    private AgentPipelineIntegration() { }

    // ── Agent Transform ────────────────────────────────────────────────

    /**
     * A pipeline transform stage that is powered by an AI agent.
     *
     * <p>The agent receives the data (as text/JSON), reasons about it,
     * and produces a transformed result.  Use cases: classification,
     * enrichment, summarisation, translation.</p>
     */
    public static final class AgentTransform {
        private final String name;
        private final String agentPrompt;
        private final TransformMode mode;

        public enum TransformMode {
            /** Agent processes each record individually. */
            PER_RECORD,
            /** Agent processes the entire batch at once. */
            BATCH
        }

        public AgentTransform(String name, String agentPrompt, TransformMode mode) {
            this.name = Objects.requireNonNull(name);
            this.agentPrompt = Objects.requireNonNull(agentPrompt);
            this.mode = Objects.requireNonNull(mode);
        }

        public String getName()          { return name; }
        public String getAgentPrompt()   { return agentPrompt; }
        public TransformMode getMode()   { return mode; }

        /**
         * Apply this agent transform to a batch.
         *
         * <p>In a real implementation this would call the agent runtime;
         * here we simulate by adding an "agent_processed" field.</p>
         */
        public PipelineExecutor.DataBatch apply(PipelineExecutor.DataBatch input,
                                                  AgentRuntime runtime) {
            if (mode == TransformMode.PER_RECORD) {
                List<PipelineExecutor.DataRecord> results = new ArrayList<>();
                for (PipelineExecutor.DataRecord record : input.getRecords()) {
                    results.add(record.with("agent_processed", true)
                                      .with("agent_transform", name));
                }
                return new PipelineExecutor.DataBatch(results);
            } else {
                // Batch mode — add metadata to each record
                List<PipelineExecutor.DataRecord> results = new ArrayList<>();
                for (PipelineExecutor.DataRecord record : input.getRecords()) {
                    results.add(record.with("agent_batch_processed", true)
                                      .with("agent_transform", name)
                                      .with("batch_size", input.size()));
                }
                return new PipelineExecutor.DataBatch(results);
            }
        }

        @Override public String toString() {
            return "AgentTransform[" + name + " mode=" + mode + "]";
        }
    }

    // ── Quality Gate ───────────────────────────────────────────────────

    /**
     * An agent-evaluated quality checkpoint in a pipeline.
     *
     * <p>Quality gates evaluate data against rules and can PASS, WARN, or FAIL
     * the pipeline.  The agent provides natural-language reasoning for its
     * decision.</p>
     */
    public static final class QualityGate {
        public enum Verdict { PASS, WARN, FAIL }

        private final String name;
        private final String description;
        private final List<QualityRule> rules;

        public QualityGate(String name, String description, List<QualityRule> rules) {
            this.name = Objects.requireNonNull(name);
            this.description = description;
            this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        }

        public String getName()            { return name; }
        public String getDescription()     { return description; }
        public List<QualityRule> getRules() { return rules; }

        /**
         * Evaluate this gate against a data batch.
         */
        public QualityGateResult evaluate(PipelineExecutor.DataBatch data) {
            List<QualityRuleResult> ruleResults = new ArrayList<>();
            Verdict overallVerdict = Verdict.PASS;

            for (QualityRule rule : rules) {
                QualityRuleResult rr = rule.check(data);
                ruleResults.add(rr);
                if (rr.getVerdict().ordinal() > overallVerdict.ordinal()) {
                    overallVerdict = rr.getVerdict();
                }
            }

            return new QualityGateResult(name, overallVerdict, ruleResults);
        }

        @Override public String toString() {
            return "QualityGate[" + name + " rules=" + rules.size() + "]";
        }
    }

    /** A single quality rule within a gate. */
    public static final class QualityRule {
        private final String ruleId;
        private final String description;
        private final QualityGate.Verdict failLevel;
        private final java.util.function.Predicate<PipelineExecutor.DataBatch> check;

        public QualityRule(String ruleId, String description,
                           QualityGate.Verdict failLevel,
                           java.util.function.Predicate<PipelineExecutor.DataBatch> check) {
            this.ruleId = ruleId;
            this.description = description;
            this.failLevel = failLevel;
            this.check = check;
        }

        public String getRuleId()                 { return ruleId; }
        public String getDescription()            { return description; }
        public QualityGate.Verdict getFailLevel() { return failLevel; }

        public QualityRuleResult check(PipelineExecutor.DataBatch data) {
            try {
                boolean passed = check.test(data);
                return new QualityRuleResult(ruleId, description,
                        passed ? QualityGate.Verdict.PASS : failLevel,
                        passed ? "Rule passed" : "Rule failed");
            } catch (Exception e) {
                return new QualityRuleResult(ruleId, description,
                        QualityGate.Verdict.FAIL, "Rule error: " + e.getMessage());
            }
        }
    }

    /** Result of evaluating one quality rule. */
    public static final class QualityRuleResult {
        private final String ruleId;
        private final String description;
        private final QualityGate.Verdict verdict;
        private final String reasoning;

        public QualityRuleResult(String ruleId, String description,
                                  QualityGate.Verdict verdict, String reasoning) {
            this.ruleId = ruleId;
            this.description = description;
            this.verdict = verdict;
            this.reasoning = reasoning;
        }

        public String getRuleId()             { return ruleId; }
        public String getDescription()        { return description; }
        public QualityGate.Verdict getVerdict() { return verdict; }
        public String getReasoning()          { return reasoning; }

        @Override public String toString() {
            return "[" + verdict + "] " + ruleId + ": " + reasoning;
        }
    }

    /** Result of evaluating a quality gate. */
    public static final class QualityGateResult {
        private final String gateName;
        private final QualityGate.Verdict verdict;
        private final List<QualityRuleResult> ruleResults;

        public QualityGateResult(String gateName, QualityGate.Verdict verdict,
                                  List<QualityRuleResult> ruleResults) {
            this.gateName = gateName;
            this.verdict = verdict;
            this.ruleResults = Collections.unmodifiableList(new ArrayList<>(ruleResults));
        }

        public String getGateName()                    { return gateName; }
        public QualityGate.Verdict getVerdict()        { return verdict; }
        public List<QualityRuleResult> getRuleResults() { return ruleResults; }
        public boolean passed() { return verdict == QualityGate.Verdict.PASS; }

        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("Quality Gate: ").append(gateName)
              .append(" → ").append(verdict).append('\n');
            for (QualityRuleResult rr : ruleResults) {
                sb.append("  ").append(rr).append('\n');
            }
            return sb.toString();
        }
    }

    // ── Anomaly Detector ───────────────────────────────────────────────

    /**
     * Agent-based anomaly detection on pipeline data.
     */
    public static final class AnomalyDetector {
        private final String name;
        private final String field;
        private final double threshold;
        private final DetectionMethod method;

        public enum DetectionMethod {
            /** Flag records where the field differs from the mean by &gt; threshold stddevs. */
            Z_SCORE,
            /** Flag records where the field is outside [mean - threshold, mean + threshold]. */
            ABSOLUTE_RANGE,
            /** Flag null/missing values. */
            NULL_CHECK
        }

        public AnomalyDetector(String name, String field,
                                double threshold, DetectionMethod method) {
            this.name = Objects.requireNonNull(name);
            this.field = Objects.requireNonNull(field);
            this.threshold = threshold;
            this.method = Objects.requireNonNull(method);
        }

        public String getName()              { return name; }
        public String getField()             { return field; }
        public double getThreshold()         { return threshold; }
        public DetectionMethod getMethod()   { return method; }

        /**
         * Detect anomalies in a data batch.
         */
        public AnomalyReport detect(PipelineExecutor.DataBatch data) {
            List<Anomaly> anomalies = new ArrayList<>();

            switch (method) {
                case NULL_CHECK:
                    for (int i = 0; i < data.size(); i++) {
                        if (!data.get(i).hasField(field) || data.get(i).get(field) == null) {
                            anomalies.add(new Anomaly(i, field, null,
                                    "Missing/null value for '" + field + "'"));
                        }
                    }
                    break;
                case ABSOLUTE_RANGE: {
                    double sum = 0;
                    int count = 0;
                    for (PipelineExecutor.DataRecord r : data.getRecords()) {
                        if (r.hasField(field) && r.get(field) instanceof Number) {
                            sum += ((Number) r.get(field)).doubleValue();
                            count++;
                        }
                    }
                    double mean = count > 0 ? sum / count : 0;
                    for (int i = 0; i < data.size(); i++) {
                        PipelineExecutor.DataRecord r = data.get(i);
                        if (r.hasField(field) && r.get(field) instanceof Number) {
                            double val = ((Number) r.get(field)).doubleValue();
                            if (Math.abs(val - mean) > threshold) {
                                anomalies.add(new Anomaly(i, field, val,
                                        "Value " + val + " outside range [" 
                                                + (mean - threshold) + ", " + (mean + threshold) + "]"));
                            }
                        }
                    }
                    break;
                }
                case Z_SCORE: {
                    double sum = 0, sumSq = 0;
                    int count = 0;
                    for (PipelineExecutor.DataRecord r : data.getRecords()) {
                        if (r.hasField(field) && r.get(field) instanceof Number) {
                            double v = ((Number) r.get(field)).doubleValue();
                            sum += v;
                            sumSq += v * v;
                            count++;
                        }
                    }
                    if (count > 1) {
                        double mean = sum / count;
                        double variance = (sumSq / count) - (mean * mean);
                        double stddev = Math.sqrt(Math.max(0, variance));
                        if (stddev > 0) {
                            for (int i = 0; i < data.size(); i++) {
                                PipelineExecutor.DataRecord r = data.get(i);
                                if (r.hasField(field) && r.get(field) instanceof Number) {
                                    double val = ((Number) r.get(field)).doubleValue();
                                    double z = Math.abs((val - mean) / stddev);
                                    if (z > threshold) {
                                        anomalies.add(new Anomaly(i, field, val,
                                                "Z-score " + String.format("%.2f", z)
                                                        + " exceeds threshold " + threshold));
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }

            return new AnomalyReport(name, data.size(), anomalies);
        }

        @Override public String toString() {
            return "AnomalyDetector[" + name + " field=" + field
                    + " method=" + method + " threshold=" + threshold + "]";
        }
    }

    /** A single detected anomaly. */
    public static final class Anomaly {
        private final int recordIndex;
        private final String field;
        private final Object value;
        private final String reason;

        public Anomaly(int recordIndex, String field, Object value, String reason) {
            this.recordIndex = recordIndex;
            this.field = field;
            this.value = value;
            this.reason = reason;
        }

        public int getRecordIndex() { return recordIndex; }
        public String getField()    { return field; }
        public Object getValue()    { return value; }
        public String getReason()   { return reason; }

        @Override public String toString() {
            return "Anomaly[row=" + recordIndex + " " + field + "=" + value + " " + reason + "]";
        }
    }

    /** Report from anomaly detection. */
    public static final class AnomalyReport {
        private final String detectorName;
        private final int totalRecords;
        private final List<Anomaly> anomalies;

        public AnomalyReport(String detectorName, int totalRecords, List<Anomaly> anomalies) {
            this.detectorName = detectorName;
            this.totalRecords = totalRecords;
            this.anomalies = Collections.unmodifiableList(new ArrayList<>(anomalies));
        }

        public String getDetectorName()   { return detectorName; }
        public int getTotalRecords()       { return totalRecords; }
        public List<Anomaly> getAnomalies() { return anomalies; }
        public boolean hasAnomalies()       { return !anomalies.isEmpty(); }
        public double anomalyRate()         { return totalRecords > 0
                ? (double) anomalies.size() / totalRecords : 0.0; }

        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("Anomaly Report: ").append(detectorName).append('\n');
            sb.append("Records scanned: ").append(totalRecords).append('\n');
            sb.append("Anomalies found: ").append(anomalies.size()).append('\n');
            sb.append("Anomaly rate:    ").append(String.format("%.1f%%", anomalyRate() * 100))
              .append('\n');
            for (Anomaly a : anomalies) {
                sb.append("  ").append(a).append('\n');
            }
            return sb.toString();
        }
    }

    // ── Intelligent Pipeline ───────────────────────────────────────────

    /**
     * A full pipeline enhanced with agent-powered features.
     */
    public static final class IntelligentPipeline {
        private final String name;
        private final PipelineConfig config;
        private final AgentRuntime agentRuntime;
        private final List<AgentTransform> agentTransforms;
        private final List<QualityGate> qualityGates;
        private final List<AnomalyDetector> anomalyDetectors;

        private IntelligentPipeline(Builder b) {
            this.name = b.name;
            this.config = b.config;
            this.agentRuntime = b.agentRuntime;
            this.agentTransforms = Collections.unmodifiableList(new ArrayList<>(b.agentTransforms));
            this.qualityGates = Collections.unmodifiableList(new ArrayList<>(b.qualityGates));
            this.anomalyDetectors = Collections.unmodifiableList(
                    new ArrayList<>(b.anomalyDetectors));
        }

        public String getName()                          { return name; }
        public PipelineConfig getConfig()                { return config; }
        public AgentRuntime getAgentRuntime()             { return agentRuntime; }
        public List<AgentTransform> getAgentTransforms()  { return agentTransforms; }
        public List<QualityGate> getQualityGates()        { return qualityGates; }
        public List<AnomalyDetector> getAnomalyDetectors() { return anomalyDetectors; }

        /**
         * Execute the intelligent pipeline.
         */
        public IntelligentPipelineResult execute(PipelineExecutor.DataBatch inputData) {
            long t0 = System.currentTimeMillis();
            Instant start = Instant.now();

            PipelineExecutor.DataBatch current = inputData;
            List<QualityGateResult> gateResults = new ArrayList<>();
            List<AnomalyReport> anomalyReports = new ArrayList<>();
            List<String> log = new ArrayList<>();

            // Phase 1: Apply agent transforms
            for (AgentTransform at : agentTransforms) {
                log.add("Applying agent transform: " + at.getName());
                current = at.apply(current, agentRuntime);
                log.add("  Output: " + current.size() + " records");
            }

            // Phase 2: Run quality gates
            for (QualityGate gate : qualityGates) {
                log.add("Evaluating quality gate: " + gate.getName());
                QualityGateResult gr = gate.evaluate(current);
                gateResults.add(gr);
                log.add("  Verdict: " + gr.getVerdict());

                if (gr.getVerdict() == QualityGate.Verdict.FAIL) {
                    long elapsed = System.currentTimeMillis() - t0;
                    log.add("Pipeline halted: quality gate FAILED");
                    return new IntelligentPipelineResult(
                            name, IntelligentPipelineResult.Status.QUALITY_GATE_FAILED,
                            current, gateResults, anomalyReports, log, elapsed, start,
                            "Quality gate '" + gate.getName() + "' failed");
                }
            }

            // Phase 3: Run anomaly detectors
            for (AnomalyDetector detector : anomalyDetectors) {
                log.add("Running anomaly detector: " + detector.getName());
                AnomalyReport report = detector.detect(current);
                anomalyReports.add(report);
                log.add("  Found " + report.getAnomalies().size() + " anomalies");
            }

            long elapsed = System.currentTimeMillis() - t0;
            log.add("Pipeline completed successfully in " + elapsed + "ms");

            return new IntelligentPipelineResult(
                    name, IntelligentPipelineResult.Status.SUCCESS,
                    current, gateResults, anomalyReports, log, elapsed, start, null);
        }

        public static Builder builder(String name) { return new Builder(name); }

        public static final class Builder {
            private final String name;
            private PipelineConfig config;
            private AgentRuntime agentRuntime;
            private final List<AgentTransform> agentTransforms = new ArrayList<>();
            private final List<QualityGate> qualityGates = new ArrayList<>();
            private final List<AnomalyDetector> anomalyDetectors = new ArrayList<>();

            private Builder(String name) { this.name = Objects.requireNonNull(name); }

            public Builder pipelineConfig(PipelineConfig c) { this.config = c; return this; }
            public Builder agentRuntime(AgentRuntime r)     { this.agentRuntime = r; return this; }
            public Builder addAgentTransform(AgentTransform t) {
                agentTransforms.add(t);
                return this;
            }
            public Builder addQualityGate(QualityGate g) {
                qualityGates.add(g);
                return this;
            }
            public Builder addAnomalyDetector(AnomalyDetector d) {
                anomalyDetectors.add(d);
                return this;
            }
            public IntelligentPipeline build() { return new IntelligentPipeline(this); }
        }
    }

    // ── Intelligent Pipeline Result ────────────────────────────────────

    /** Result of executing an intelligent pipeline. */
    public static final class IntelligentPipelineResult {
        public enum Status { SUCCESS, QUALITY_GATE_FAILED, ANOMALIES_DETECTED, FAILED }

        private final String pipelineName;
        private final Status status;
        private final PipelineExecutor.DataBatch output;
        private final List<QualityGateResult> qualityGateResults;
        private final List<AnomalyReport> anomalyReports;
        private final List<String> executionLog;
        private final long durationMs;
        private final Instant startTime;
        private final String errorMessage;

        public IntelligentPipelineResult(
                String pipelineName, Status status,
                PipelineExecutor.DataBatch output,
                List<QualityGateResult> qualityGateResults,
                List<AnomalyReport> anomalyReports,
                List<String> executionLog,
                long durationMs, Instant startTime, String errorMessage) {
            this.pipelineName = pipelineName;
            this.status = status;
            this.output = output;
            this.qualityGateResults = Collections.unmodifiableList(new ArrayList<>(qualityGateResults));
            this.anomalyReports = Collections.unmodifiableList(new ArrayList<>(anomalyReports));
            this.executionLog = Collections.unmodifiableList(new ArrayList<>(executionLog));
            this.durationMs = durationMs;
            this.startTime = startTime;
            this.errorMessage = errorMessage;
        }

        public String getPipelineName()                        { return pipelineName; }
        public Status getStatus()                              { return status; }
        public PipelineExecutor.DataBatch getOutput()          { return output; }
        public List<QualityGateResult> getQualityGateResults() { return qualityGateResults; }
        public List<AnomalyReport> getAnomalyReports()         { return anomalyReports; }
        public List<String> getExecutionLog()                  { return executionLog; }
        public long getDurationMs()                            { return durationMs; }
        public Instant getStartTime()                          { return startTime; }
        public String getErrorMessage()                        { return errorMessage; }
        public boolean isSuccess()                             { return status == Status.SUCCESS; }

        public String formatSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Intelligent Pipeline: ").append(pipelineName).append(" ===\n");
            sb.append("Status:   ").append(status).append('\n');
            sb.append("Duration: ").append(durationMs).append("ms\n");
            sb.append("Output:   ").append(output.size()).append(" records\n");
            sb.append("\nQuality Gates:\n");
            for (QualityGateResult gr : qualityGateResults) {
                sb.append("  ").append(gr.getGateName()).append(": ").append(gr.getVerdict()).append('\n');
            }
            sb.append("\nAnomaly Reports:\n");
            for (AnomalyReport ar : anomalyReports) {
                sb.append("  ").append(ar.getDetectorName())
                  .append(": ").append(ar.getAnomalies().size()).append(" anomalies\n");
            }
            if (errorMessage != null) {
                sb.append("\nError: ").append(errorMessage).append('\n');
            }
            sb.append("\nExecution Log:\n");
            for (String entry : executionLog) {
                sb.append("  ").append(entry).append('\n');
            }
            return sb.toString();
        }
    }
}
