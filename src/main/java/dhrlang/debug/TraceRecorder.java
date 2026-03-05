package dhrlang.debug;

import java.util.*;

/**
 * Records an execution trace of DhrLang function calls, returns,
 * state changes, and control-flow decisions.
 *
 * <p>Typical usage:
 * <pre>
 *   var recorder = new TraceRecorder();
 *   recorder.enable();
 *   recorder.recordCall("transfer", "Token", List.of(addr, amount));
 *   recorder.recordStateChange("balance", 100L, 80L);
 *   recorder.recordReturn("transfer", null);
 *   String trace = recorder.formatTrace();
 * </pre>
 *
 * <p>The recorder is disabled by default. Enable it with {@link #enable()}
 * before recording any events. Events recorded while disabled are silently
 * discarded.</p>
 */
public class TraceRecorder {

    // ── Trace entry types ────────────────────────────────────────────────

    /** Types of trace events. */
    public enum EntryType {
        CALL,
        RETURN,
        STATE_CHANGE,
        BRANCH,
        EXCEPTION,
        LOG_EVENT
    }

    /**
     * A single trace event with timestamp, type, and contextual data.
     */
    public static class TraceEntry {
        private final long sequenceNumber;
        private final EntryType type;
        private final String label;
        private final String detail;
        private final int depth;
        private final long timestampNanos;

        public TraceEntry(long sequenceNumber, EntryType type, String label,
                          String detail, int depth) {
            this.sequenceNumber = sequenceNumber;
            this.type = type;
            this.label = label;
            this.detail = detail;
            this.depth = depth;
            this.timestampNanos = System.nanoTime();
        }

        public long getSequenceNumber() { return sequenceNumber; }
        public EntryType getType()       { return type; }
        public String getLabel()         { return label; }
        public String getDetail()        { return detail; }
        public int getDepth()            { return depth; }
        public long getTimestampNanos()  { return timestampNanos; }

        @Override
        public String toString() {
            String indent = "  ".repeat(Math.max(0, depth));
            return String.format("#%d %s%s %s%s",
                    sequenceNumber,
                    indent,
                    type.name(),
                    label,
                    detail.isEmpty() ? "" : " ─ " + detail);
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private final List<TraceEntry> entries = new ArrayList<>();
    private boolean enabled = false;
    private int currentDepth = 0;
    private long sequence = 0;
    private int maxDepth = 0;
    private int maxEntries = 100_000;

    // ── Enable / disable ─────────────────────────────────────────────────

    /** Enable trace recording. */
    public void enable() { this.enabled = true; }

    /** Disable trace recording. */
    public void disable() { this.enabled = false; }

    /** Whether recording is currently enabled. */
    public boolean isEnabled() { return enabled; }

    /** Set the maximum number of entries to keep (oldest are discarded). */
    public void setMaxEntries(int max) { this.maxEntries = max; }

    // ── Recording methods ────────────────────────────────────────────────

    /**
     * Record a function call.
     *
     * @param functionName the function being called
     * @param className    the owning class (may be null)
     * @param args         the argument values (may be empty)
     */
    public void recordCall(String functionName, String className, List<Object> args) {
        if (!enabled) return;

        String qualifiedName = className != null ? className + "." + functionName : functionName;
        String argsStr = formatArgs(args);

        addEntry(EntryType.CALL, qualifiedName, "args=" + argsStr);
        currentDepth++;
        maxDepth = Math.max(maxDepth, currentDepth);
    }

    /**
     * Record a function return.
     *
     * @param functionName the function returning
     * @param result       the return value (may be null)
     */
    public void recordReturn(String functionName, Object result) {
        if (!enabled) return;

        currentDepth = Math.max(0, currentDepth - 1);
        String resultStr = result != null ? formatCompact(result) : "void";
        addEntry(EntryType.RETURN, functionName, "result=" + resultStr);
    }

    /**
     * Record a state (variable) change.
     *
     * @param variable the variable name
     * @param oldValue the old value
     * @param newValue the new value
     */
    public void recordStateChange(String variable, Object oldValue, Object newValue) {
        if (!enabled) return;

        addEntry(EntryType.STATE_CHANGE, variable,
                formatCompact(oldValue) + " → " + formatCompact(newValue));
    }

    /**
     * Record a control-flow branch decision.
     *
     * @param condition description of the condition
     * @param taken     whether the branch was taken
     */
    public void recordBranch(String condition, boolean taken) {
        if (!enabled) return;

        addEntry(EntryType.BRANCH, condition, taken ? "taken" : "not-taken");
    }

    /**
     * Record an exception being thrown.
     *
     * @param exceptionType the exception class/type
     * @param message       the error message
     */
    public void recordException(String exceptionType, String message) {
        if (!enabled) return;

        addEntry(EntryType.EXCEPTION, exceptionType, message);
    }

    /**
     * Record a contract log/event emission.
     *
     * @param eventName the event name
     * @param data      event data description
     */
    public void recordLogEvent(String eventName, String data) {
        if (!enabled) return;

        addEntry(EntryType.LOG_EVENT, eventName, data);
    }

    // ── Query methods ────────────────────────────────────────────────────

    /** All recorded entries (defensive copy). */
    public List<TraceEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    /** Number of recorded entries. */
    public int getEntryCount() { return entries.size(); }

    /** Maximum call depth reached. */
    public int getMaxDepth() { return maxDepth; }

    /** Entries of a specific type. */
    public List<TraceEntry> getEntriesOfType(EntryType type) {
        List<TraceEntry> result = new ArrayList<>();
        for (TraceEntry e : entries) {
            if (e.getType() == type) result.add(e);
        }
        return result;
    }

    /** Count entries by type. */
    public Map<EntryType, Integer> getEntryCounts() {
        Map<EntryType, Integer> counts = new EnumMap<>(EntryType.class);
        for (EntryType t : EntryType.values()) counts.put(t, 0);
        for (TraceEntry e : entries) {
            counts.merge(e.getType(), 1, Integer::sum);
        }
        return counts;
    }

    /** Get the last N entries. */
    public List<TraceEntry> getLastEntries(int n) {
        int start = Math.max(0, entries.size() - n);
        return new ArrayList<>(entries.subList(start, entries.size()));
    }

    // ── Formatting ───────────────────────────────────────────────────────

    /**
     * Format the entire trace as a readable multi-line string.
     */
    public String formatTrace() {
        if (entries.isEmpty()) {
            return "trace: (empty)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═══ Execution Trace (").append(entries.size()).append(" events, ");
        sb.append("max depth ").append(maxDepth).append(") ═══\n");

        for (TraceEntry entry : entries) {
            sb.append(entry).append("\n");
        }

        sb.append("═══ End Trace ═══");
        return sb.toString();
    }

    /**
     * Format a compact summary of the trace.
     */
    public String formatSummary() {
        Map<EntryType, Integer> counts = getEntryCounts();
        StringBuilder sb = new StringBuilder();
        sb.append("Trace Summary: ");
        sb.append(entries.size()).append(" events, ");
        sb.append("max depth ").append(maxDepth).append("\n");

        for (Map.Entry<EntryType, Integer> e : counts.entrySet()) {
            if (e.getValue() > 0) {
                sb.append("  ").append(e.getKey().name()).append(": ")
                  .append(e.getValue()).append("\n");
            }
        }
        return sb.toString();
    }

    /** Clear all recorded entries and reset state. */
    public void clear() {
        entries.clear();
        currentDepth = 0;
        sequence = 0;
        maxDepth = 0;
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private void addEntry(EntryType type, String label, String detail) {
        if (entries.size() >= maxEntries) {
            entries.remove(0); // drop oldest
        }
        entries.add(new TraceEntry(sequence++, type, label, detail, currentDepth));
    }

    private String formatArgs(List<Object> args) {
        if (args == null || args.isEmpty()) return "()";
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatCompact(args.get(i)));
        }
        sb.append(")");
        return sb.toString();
    }

    private static String formatCompact(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) {
            if (s.length() > 30) return "\"" + s.substring(0, 27) + "...\"";
            return "\"" + s + "\"";
        }
        if (value instanceof Object[] arr) return "array[" + arr.length + "]";
        return String.valueOf(value);
    }
}
