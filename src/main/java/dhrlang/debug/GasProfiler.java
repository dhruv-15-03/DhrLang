package dhrlang.debug;

import dhrlang.evm.EvmOpcode;

import java.util.*;

/**
 * Profiles EVM gas usage per function, per opcode, and in aggregate.
 *
 * <p>Tracks gas consumption as opcodes are executed. Provides per-function
 * breakdowns, per-opcode totals, and hotspot reports sorted by cost.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 *   var profiler = new GasProfiler();
 *   profiler.startFunction("transfer");
 *   profiler.recordOpcode(EvmOpcode.SLOAD);
 *   profiler.recordOpcode(EvmOpcode.ADD);
 *   profiler.recordOpcode(EvmOpcode.SSTORE);
 *   profiler.endFunction("transfer");
 *   System.out.println(profiler.formatReport());
 * </pre>
 */
public class GasProfiler {

    // ── Inner types ──────────────────────────────────────────────────────

    /**
     * Per-function profile data.
     */
    public static class FunctionProfile {
        private final String name;
        private long gasUsed;
        private int opcodeCount;
        private final Map<EvmOpcode, Long> opcodeGas = new EnumMap<>(EvmOpcode.class);
        private final Map<EvmOpcode, Integer> opcodeCountMap = new EnumMap<>(EvmOpcode.class);

        public FunctionProfile(String name) {
            this.name = name;
        }

        void addOpcode(EvmOpcode op) {
            long cost = op.gasCost;
            gasUsed += cost;
            opcodeCount++;
            opcodeGas.merge(op, cost, Long::sum);
            opcodeCountMap.merge(op, 1, Integer::sum);
        }

        void addCustomGas(EvmOpcode op, long gas) {
            gasUsed += gas;
            opcodeCount++;
            opcodeGas.merge(op, gas, Long::sum);
            opcodeCountMap.merge(op, 1, Integer::sum);
        }

        public String getName()           { return name; }
        public long getGasUsed()           { return gasUsed; }
        public int getOpcodeCount()        { return opcodeCount; }
        public Map<EvmOpcode, Long> getOpcodeGas()      { return Collections.unmodifiableMap(opcodeGas); }
        public Map<EvmOpcode, Integer> getOpcodeCountMap() { return Collections.unmodifiableMap(opcodeCountMap); }

        @Override
        public String toString() {
            return name + ": " + gasUsed + " gas (" + opcodeCount + " ops)";
        }
    }

    /**
     * A gas hotspot — a function with its gas rank.
     */
    public static class GasHotspot {
        private final String functionName;
        private final long gasUsed;
        private final double percentage;

        public GasHotspot(String functionName, long gasUsed, double percentage) {
            this.functionName = functionName;
            this.gasUsed = gasUsed;
            this.percentage = percentage;
        }

        public String getFunctionName() { return functionName; }
        public long getGasUsed()         { return gasUsed; }
        public double getPercentage()    { return percentage; }

        @Override
        public String toString() {
            return String.format("%s: %,d gas (%.1f%%)", functionName, gasUsed, percentage);
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private final Map<String, FunctionProfile> functionProfiles = new LinkedHashMap<>();
    private final Map<EvmOpcode, Long> globalOpcodeGas = new EnumMap<>(EvmOpcode.class);
    private final Map<EvmOpcode, Integer> globalOpcodeCount = new EnumMap<>(EvmOpcode.class);
    private final Deque<String> functionStack = new ArrayDeque<>();
    private long totalGas = 0;
    private int totalOpcodes = 0;

    // ── Function scope tracking ──────────────────────────────────────────

    /**
     * Mark the start of a function execution scope. Nested calls are supported.
     */
    public void startFunction(String name) {
        functionStack.push(name);
        functionProfiles.computeIfAbsent(name, FunctionProfile::new);
    }

    /**
     * Mark the end of a function execution scope.
     */
    public void endFunction(String name) {
        if (!functionStack.isEmpty() && functionStack.peek().equals(name)) {
            functionStack.pop();
        }
    }

    // ── Opcode recording ─────────────────────────────────────────────────

    /**
     * Record execution of an opcode using its base gas cost.
     */
    public void recordOpcode(EvmOpcode op) {
        long cost = op.gasCost;
        totalGas += cost;
        totalOpcodes++;
        globalOpcodeGas.merge(op, cost, Long::sum);
        globalOpcodeCount.merge(op, 1, Integer::sum);

        // Attribute to current function if in scope
        if (!functionStack.isEmpty()) {
            String current = functionStack.peek();
            FunctionProfile fp = functionProfiles.get(current);
            if (fp != null) fp.addOpcode(op);
        }
    }

    /**
     * Record execution of an opcode with a custom gas cost
     * (e.g., for dynamic costs like SSTORE cold/warm).
     */
    public void recordOpcodeWithGas(EvmOpcode op, long gas) {
        totalGas += gas;
        totalOpcodes++;
        globalOpcodeGas.merge(op, gas, Long::sum);
        globalOpcodeCount.merge(op, 1, Integer::sum);

        if (!functionStack.isEmpty()) {
            String current = functionStack.peek();
            FunctionProfile fp = functionProfiles.get(current);
            if (fp != null) fp.addCustomGas(op, gas);
        }
    }

    // ── Query methods ────────────────────────────────────────────────────

    /** Total gas consumed across all recorded opcodes. */
    public long getTotalGas() { return totalGas; }

    /** Total opcode executions recorded. */
    public int getTotalOpcodes() { return totalOpcodes; }

    /** Per-function gas breakdown. */
    public Map<String, Long> getPerFunctionGas() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, FunctionProfile> e : functionProfiles.entrySet()) {
            result.put(e.getKey(), e.getValue().getGasUsed());
        }
        return result;
    }

    /** Per-opcode gas breakdown (global). */
    public Map<EvmOpcode, Long> getPerOpcodeGas() {
        return Collections.unmodifiableMap(globalOpcodeGas);
    }

    /** Per-opcode execution count (global). */
    public Map<EvmOpcode, Integer> getPerOpcodeCount() {
        return Collections.unmodifiableMap(globalOpcodeCount);
    }

    /** Get detailed profile for a specific function. */
    public FunctionProfile getFunctionProfile(String name) {
        return functionProfiles.get(name);
    }

    /** Get all function profiles. */
    public Map<String, FunctionProfile> getAllProfiles() {
        return Collections.unmodifiableMap(functionProfiles);
    }

    /**
     * Get the top-N gas hotspots sorted by gas consumption (descending).
     */
    public List<GasHotspot> getHotspots(int topN) {
        List<Map.Entry<String, FunctionProfile>> sorted = new ArrayList<>(functionProfiles.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().getGasUsed(), a.getValue().getGasUsed()));

        List<GasHotspot> hotspots = new ArrayList<>();
        int limit = Math.min(topN, sorted.size());
        for (int i = 0; i < limit; i++) {
            FunctionProfile fp = sorted.get(i).getValue();
            double pct = totalGas > 0 ? (fp.getGasUsed() * 100.0) / totalGas : 0.0;
            hotspots.add(new GasHotspot(fp.getName(), fp.getGasUsed(), pct));
        }
        return hotspots;
    }

    /**
     * Get the most expensive opcode types sorted by total gas (descending).
     */
    public List<Map.Entry<EvmOpcode, Long>> getExpensiveOpcodes(int topN) {
        List<Map.Entry<EvmOpcode, Long>> sorted = new ArrayList<>(globalOpcodeGas.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return sorted.subList(0, Math.min(topN, sorted.size()));
    }

    // ── Formatting ───────────────────────────────────────────────────────

    /**
     * Format a complete gas profiling report.
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Gas Profile Report ═══\n");
        sb.append(String.format("Total gas: %,d  |  Total opcodes: %,d\n\n", totalGas, totalOpcodes));

        // Per-function breakdown
        if (!functionProfiles.isEmpty()) {
            sb.append("── Per-Function Breakdown ──\n");
            List<GasHotspot> hotspots = getHotspots(functionProfiles.size());
            for (GasHotspot hs : hotspots) {
                sb.append(String.format("  %-30s %,10d gas  (%5.1f%%)\n",
                        hs.getFunctionName(), hs.getGasUsed(), hs.getPercentage()));
            }
            sb.append("\n");
        }

        // Per-opcode breakdown (top 10)
        if (!globalOpcodeGas.isEmpty()) {
            sb.append("── Top Opcodes by Gas ──\n");
            List<Map.Entry<EvmOpcode, Long>> expensive = getExpensiveOpcodes(10);
            for (Map.Entry<EvmOpcode, Long> e : expensive) {
                int count = globalOpcodeCount.getOrDefault(e.getKey(), 0);
                double pct = totalGas > 0 ? (e.getValue() * 100.0) / totalGas : 0.0;
                sb.append(String.format("  %-12s  %,10d gas  (%5.1f%%)  × %d\n",
                        e.getKey().name(), e.getValue(), pct, count));
            }
        }

        sb.append("═══ End Report ═══");
        return sb.toString();
    }

    /**
     * Format a compact one-line summary.
     */
    public String formatCompact() {
        return String.format("Gas: %,d total, %d ops, %d functions",
                totalGas, totalOpcodes, functionProfiles.size());
    }

    /** Reset all profiling data. */
    public void reset() {
        functionProfiles.clear();
        globalOpcodeGas.clear();
        globalOpcodeCount.clear();
        functionStack.clear();
        totalGas = 0;
        totalOpcodes = 0;
    }
}
