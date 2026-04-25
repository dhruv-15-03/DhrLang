package dhrlang.deploy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Estimates gas costs for contract deployment and function calls.
 *
 * <p>Provides detailed gas breakdowns by category (intrinsic, calldata,
 * creation, storage, execution) and cost estimates in wei/gwei/ETH.</p>
 *
 * <p><b>User story:</b> As a developer, I want to see gas cost estimates
 * before deploying so I can budget deployment costs.</p>
 */
public final class GasEstimator {

    // ── Constants ────────────────────────────────────────────────────────

    /** Gas per non-zero byte of calldata (EIP-2028). */
    private static final long CALLDATA_NONZERO_GAS = 16;

    /** Gas per zero byte of calldata. */
    private static final long CALLDATA_ZERO_GAS = 4;

    /** Base transaction gas (EIP-2718). */
    private static final long TX_BASE_GAS = 21_000;

    /** Gas for contract creation transaction. */
    private static final long CREATE_GAS = 32_000;

    /** Gas per byte of deployed code (EIP-3860). */
    private static final long INITCODE_WORD_GAS = 2;

    /** Gas per 32-byte word of deployed code. */
    private static final long CODE_DEPOSIT_GAS = 200;

    /** Gas for SSTORE (cold, new value). */
    public static final long SSTORE_SET_GAS = 20_000;

    /** Gas for SSTORE (warm, modify existing). */
    public static final long SSTORE_RESET_GAS = 2_900;

    // ── Gas Breakdown ────────────────────────────────────────────────────

    /**
     * Detailed gas breakdown for a deployment.
     */
    public static final class GasBreakdown {
        private final long intrinsicGas;
        private final long calldataGas;
        private final long creationGas;
        private final long codeDepositGas;
        private final long initcodeGas;
        private final long estimatedExecutionGas;
        private final long totalEstimate;
        private final int bytecodeSize;
        private final int storageSlotCount;

        GasBreakdown(long intrinsicGas, long calldataGas, long creationGas,
                     long codeDepositGas, long initcodeGas,
                     long estimatedExecutionGas, int bytecodeSize, int storageSlotCount) {
            this.intrinsicGas = intrinsicGas;
            this.calldataGas = calldataGas;
            this.creationGas = creationGas;
            this.codeDepositGas = codeDepositGas;
            this.initcodeGas = initcodeGas;
            this.estimatedExecutionGas = estimatedExecutionGas;
            this.bytecodeSize = bytecodeSize;
            this.storageSlotCount = storageSlotCount;
            this.totalEstimate = intrinsicGas + calldataGas + creationGas
                    + codeDepositGas + initcodeGas + estimatedExecutionGas;
        }

        public long getIntrinsicGas() { return intrinsicGas; }
        public long getCalldataGas() { return calldataGas; }
        public long getCreationGas() { return creationGas; }
        public long getCodeDepositGas() { return codeDepositGas; }
        public long getInitcodeGas() { return initcodeGas; }
        public long getEstimatedExecutionGas() { return estimatedExecutionGas; }
        public long getTotalEstimate() { return totalEstimate; }
        public int getBytecodeSize() { return bytecodeSize; }
        public int getStorageSlotCount() { return storageSlotCount; }
    }

    // ── Cost Estimate ────────────────────────────────────────────────────

    /**
     * ETH cost estimate for a gas amount at given gas prices.
     */
    public static final class CostEstimate {
        private final long gasUsed;
        private final long gasPriceGwei;
        private final double costWei;
        private final double costGwei;
        private final double costEth;
        private final String networkName;

        CostEstimate(long gasUsed, long gasPriceGwei, String networkName) {
            this.gasUsed = gasUsed;
            this.gasPriceGwei = gasPriceGwei;
            this.costWei = (double) gasUsed * gasPriceGwei * 1_000_000_000L;
            this.costGwei = (double) gasUsed * gasPriceGwei;
            this.costEth = costGwei / 1_000_000_000.0;
            this.networkName = networkName;
        }

        public long getGasUsed() { return gasUsed; }
        public long getGasPriceGwei() { return gasPriceGwei; }
        public double getCostWei() { return costWei; }
        public double getCostGwei() { return costGwei; }
        public double getCostEth() { return costEth; }
        public String getNetworkName() { return networkName; }
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Estimate gas for deploying a contract from its creation bytecode.
     *
     * @param creationBytecode the full creation bytecode
     * @param runtimeBytecode  the runtime bytecode (code to be stored on-chain)
     * @param storageSlotCount number of storage slots initialized in constructor
     * @return a detailed gas breakdown
     */
    public static GasBreakdown estimateDeployGas(byte[] creationBytecode,
                                                  byte[] runtimeBytecode,
                                                  int storageSlotCount) {
        int creationSize = creationBytecode != null ? creationBytecode.length : 0;
        int runtimeSize = runtimeBytecode != null ? runtimeBytecode.length : 0;

        long intrinsic = TX_BASE_GAS;
        long calldata = estimateCalldataGas(creationBytecode);
        long creation = CREATE_GAS;
        long codeDeposit = (long) runtimeSize * CODE_DEPOSIT_GAS;
        long initcode = ((long) creationSize + 31) / 32 * INITCODE_WORD_GAS;
        long execution = estimateConstructorExecutionGas(storageSlotCount);

        return new GasBreakdown(intrinsic, calldata, creation, codeDeposit,
                initcode, execution, runtimeSize, storageSlotCount);
    }

    /**
     * Estimate cost at different gas price levels.
     *
     * @param breakdown the gas breakdown
     * @return map of price level name → cost estimate
     */
    public static Map<String, CostEstimate> estimateCosts(GasBreakdown breakdown) {
        Map<String, CostEstimate> costs = new LinkedHashMap<>();
        costs.put("Low (15 gwei)", new CostEstimate(breakdown.getTotalEstimate(), 15, "Ethereum Mainnet"));
        costs.put("Average (30 gwei)", new CostEstimate(breakdown.getTotalEstimate(), 30, "Ethereum Mainnet"));
        costs.put("High (50 gwei)", new CostEstimate(breakdown.getTotalEstimate(), 50, "Ethereum Mainnet"));
        costs.put("Urgent (100 gwei)", new CostEstimate(breakdown.getTotalEstimate(), 100, "Ethereum Mainnet"));
        return costs;
    }

    /**
     * Estimate costs for an L2 chain (typically much cheaper).
     */
    public static Map<String, CostEstimate> estimateL2Costs(GasBreakdown breakdown,
                                                             L2ChainConfig chain) {
        Map<String, CostEstimate> costs = new LinkedHashMap<>();
        // L2s typically have much lower gas prices
        long l2LowGwei = 0; // Some L2s can be as low as 0.001 gwei
        long l2AvgGwei = 1;
        long l2HighGwei = 5;

        String name = chain.getName();
        costs.put(name + " Low (0.01 gwei)", new CostEstimate(breakdown.getTotalEstimate(), l2LowGwei, name));
        costs.put(name + " Average (0.1 gwei)", new CostEstimate(breakdown.getTotalEstimate(), l2AvgGwei, name));
        costs.put(name + " High (1 gwei)", new CostEstimate(breakdown.getTotalEstimate(), l2HighGwei, name));
        return costs;
    }

    /**
     * Format a gas breakdown as a human-readable report.
     */
    public static String formatReport(GasBreakdown breakdown) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                    GAS ESTIMATION REPORT                    ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Bytecode size:        %,10d bytes                     ║%n", breakdown.getBytecodeSize()));
        sb.append(String.format("║  Storage slots:        %,10d                           ║%n", breakdown.getStorageSlotCount()));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append("║  Gas Breakdown:                                            ║\n");
        sb.append(String.format("║    Intrinsic (tx base):    %,10d gas                  ║%n", breakdown.getIntrinsicGas()));
        sb.append(String.format("║    Calldata:               %,10d gas                  ║%n", breakdown.getCalldataGas()));
        sb.append(String.format("║    Contract creation:      %,10d gas                  ║%n", breakdown.getCreationGas()));
        sb.append(String.format("║    Code deposit:           %,10d gas                  ║%n", breakdown.getCodeDepositGas()));
        sb.append(String.format("║    Initcode:               %,10d gas                  ║%n", breakdown.getInitcodeGas()));
        sb.append(String.format("║    Constructor execution:  %,10d gas                  ║%n", breakdown.getEstimatedExecutionGas()));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  TOTAL ESTIMATED GAS:      %,10d                      ║%n", breakdown.getTotalEstimate()));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append("║  Cost Estimates (ETH @ different gas prices):               ║\n");
        for (var entry : estimateCosts(breakdown).entrySet()) {
            sb.append(String.format("║    %-24s %12.6f ETH              ║%n",
                    entry.getKey() + ":", entry.getValue().getCostEth()));
        }
        sb.append("╚══════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }

    /**
     * Format as JSON.
     */
    public static String formatJson(GasBreakdown breakdown) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"bytecodeSize\": ").append(breakdown.getBytecodeSize()).append(",\n");
        sb.append("  \"storageSlots\": ").append(breakdown.getStorageSlotCount()).append(",\n");
        sb.append("  \"gasBreakdown\": {\n");
        sb.append("    \"intrinsic\": ").append(breakdown.getIntrinsicGas()).append(",\n");
        sb.append("    \"calldata\": ").append(breakdown.getCalldataGas()).append(",\n");
        sb.append("    \"creation\": ").append(breakdown.getCreationGas()).append(",\n");
        sb.append("    \"codeDeposit\": ").append(breakdown.getCodeDepositGas()).append(",\n");
        sb.append("    \"initcode\": ").append(breakdown.getInitcodeGas()).append(",\n");
        sb.append("    \"execution\": ").append(breakdown.getEstimatedExecutionGas()).append("\n");
        sb.append("  },\n");
        sb.append("  \"totalGas\": ").append(breakdown.getTotalEstimate()).append(",\n");
        sb.append("  \"costEstimates\": {\n");
        var costs = estimateCosts(breakdown);
        int i = 0;
        for (var entry : costs.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": ").append(entry.getValue().getCostEth());
            if (++i < costs.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    private static long estimateCalldataGas(byte[] bytecode) {
        if (bytecode == null || bytecode.length == 0) return 0;
        long gas = 0;
        for (byte b : bytecode) {
            gas += (b == 0) ? CALLDATA_ZERO_GAS : CALLDATA_NONZERO_GAS;
        }
        return gas;
    }

    private static long estimateConstructorExecutionGas(int storageSlotCount) {
        // Each SSTORE in constructor costs 20,000 gas (cold)
        // Plus some overhead for stack operations, memory, etc.
        long storageGas = (long) storageSlotCount * SSTORE_SET_GAS;
        long overheadGas = 10_000; // base constructor overhead
        return storageGas + overheadGas;
    }
}
