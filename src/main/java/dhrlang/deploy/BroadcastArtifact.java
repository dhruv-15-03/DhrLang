package dhrlang.deploy;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a Foundry-compatible deployment <b>broadcast artifact</b>
 * ({@code broadcast/<Script>/<chainId>/run-latest.json}).
 *
 * <p>Foundry's {@code forge script --broadcast} writes a machine-readable record of
 * every transaction it sent so tooling (verifiers, indexers, CI) can pick up deployed
 * addresses and receipts. DhrLang emits the <b>same schema</b> from its own
 * {@code contract deploy} so a DhrLang deployment drops cleanly into a Foundry-shaped
 * project layout.</p>
 *
 * <p>The artifact is produced for all three deploy paths:</p>
 * <ul>
 *   <li><b>dry-run</b> - planned transactions with the <em>predicted</em> CREATE address
 *       (see {@link WalletManager#computeCreateAddress}) and {@code null} hashes;</li>
 *   <li><b>offline</b> (signed, no reachable RPC) - same, plus the signed tx is written
 *       separately;</li>
 *   <li><b>live</b> - real tx hash, on-chain contract address, and a populated
 *       {@code receipts[]} entry.</li>
 * </ul>
 *
 * <p>The JSON is hand-rolled (no third-party JSON dependency, matching the rest of the
 * deploy package) and is deterministic given a fixed {@link #setTimestamp(long) timestamp},
 * which makes it straightforward to unit-test.</p>
 *
 * @see DeploymentManager
 * @see WalletManager#computeCreateAddress(String, long)
 */
public final class BroadcastArtifact {

    /** A single CREATE (contract-creation) transaction in the broadcast. */
    public static final class Tx {
        final String contractName;
        final String from;
        final long nonce;
        final long gas;
        final String input;            // 0x-prefixed creation bytecode
        final String contractAddress;  // predicted (dry-run/offline) or on-chain (live)
        final String txHash;           // null until broadcast
        final boolean hasReceipt;
        final long gasUsed;
        final long blockNumber;
        final int status;              // 1 = success, 0 = reverted

        private Tx(String contractName, String from, long nonce, long gas, String input,
                   String contractAddress, String txHash, boolean hasReceipt,
                   long gasUsed, long blockNumber, int status) {
            this.contractName = contractName;
            this.from = from;
            this.nonce = nonce;
            this.gas = gas;
            this.input = ensure0x(input);
            this.contractAddress = contractAddress;
            this.txHash = txHash;
            this.hasReceipt = hasReceipt;
            this.gasUsed = gasUsed;
            this.blockNumber = blockNumber;
            this.status = status;
        }

        /** A planned (not-yet-sent) deployment: predicted address, no hash, no receipt. */
        static Tx planned(String contractName, String from, long nonce, long gas,
                          String input, String predictedAddress) {
            return new Tx(contractName, from, nonce, gas, input, predictedAddress,
                    null, false, 0L, 0L, 0);
        }

        /** A confirmed live deployment: real hash, on-chain address, and a receipt. */
        static Tx broadcast(String contractName, String from, long nonce, long gas,
                            String input, String contractAddress, String txHash,
                            long gasUsed, long blockNumber) {
            return new Tx(contractName, from, nonce, gas, input, contractAddress,
                    txHash, true, gasUsed, blockNumber, 1);
        }
    }

    private final long chainId;
    private String scriptName = "Deploy.s.sol";
    private String commit;                       // optional source commit hash
    private long timestamp = System.currentTimeMillis() / 1000L;
    private final List<Tx> txs = new ArrayList<>();

    public BroadcastArtifact(long chainId) {
        this.chainId = chainId;
    }

    public BroadcastArtifact setScriptName(String scriptName) {
        if (scriptName != null && !scriptName.isBlank()) this.scriptName = scriptName;
        return this;
    }

    public BroadcastArtifact setCommit(String commit) {
        this.commit = commit;
        return this;
    }

    /** Override the wall-clock timestamp (seconds) - used for deterministic output/tests. */
    public BroadcastArtifact setTimestamp(long epochSeconds) {
        this.timestamp = epochSeconds;
        return this;
    }

    public BroadcastArtifact addPlanned(String contractName, String from, long nonce,
                                        long gas, String input, String predictedAddress) {
        txs.add(Tx.planned(contractName, from, nonce, gas, input, predictedAddress));
        return this;
    }

    public BroadcastArtifact addBroadcast(String contractName, String from, long nonce,
                                          long gas, String input, String contractAddress,
                                          String txHash, long gasUsed, long blockNumber) {
        txs.add(Tx.broadcast(contractName, from, nonce, gas, input, contractAddress,
                txHash, gasUsed, blockNumber));
        return this;
    }

    public long getChainId() { return chainId; }

    public List<Tx> getTransactions() { return txs; }

    /**
     * The Foundry-style relative path this artifact should be written to.
     *
     * @param dryRun whether this is a simulation (Foundry nests dry runs under
     *               {@code dry-run/})
     * @return e.g. {@code broadcast/Deploy.s.sol/31337/run-latest.json}
     */
    public String relativePath(boolean dryRun) {
        return "broadcast/" + scriptName + "/" + chainId
                + (dryRun ? "/dry-run" : "") + "/run-latest.json";
    }

    /** Render the broadcast artifact as Foundry-compatible JSON. */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"transactions\": [");
        for (int i = 0; i < txs.size(); i++) {
            sb.append(i == 0 ? "\n" : ",\n");
            appendTransaction(sb, txs.get(i));
        }        sb.append(txs.isEmpty() ? "],\n" : "\n  ],\n");

        sb.append("  \"receipts\": [");
        boolean firstReceipt = true;
        for (Tx tx : txs) {
            if (!tx.hasReceipt) continue;
            sb.append(firstReceipt ? "\n" : ",\n");
            appendReceipt(sb, tx);
            firstReceipt = false;
        }
        sb.append(firstReceipt ? "],\n" : "\n  ],\n");

        sb.append("  \"libraries\": [],\n");
        sb.append("  \"pending\": [],\n");
        sb.append("  \"returns\": {},\n");
        sb.append("  \"timestamp\": ").append(timestamp).append(",\n");
        sb.append("  \"chain\": ").append(chainId).append(",\n");
        if (commit != null && !commit.isBlank()) {
            sb.append("  \"commit\": \"").append(esc(commit)).append("\",\n");
        }
        sb.append("  \"multi\": false\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ── Internal rendering ───────────────────────────────────────────────

    private void appendTransaction(StringBuilder sb, Tx tx) {
        sb.append("    {\n");
        sb.append("      \"hash\": ").append(jsonOrNull(tx.txHash)).append(",\n");
        sb.append("      \"transactionType\": \"CREATE\",\n");
        sb.append("      \"contractName\": \"").append(esc(tx.contractName)).append("\",\n");
        sb.append("      \"contractAddress\": ").append(jsonOrNull(lower(tx.contractAddress))).append(",\n");
        sb.append("      \"function\": null,\n");
        sb.append("      \"arguments\": null,\n");
        sb.append("      \"transaction\": {\n");
        sb.append("        \"from\": \"").append(esc(lower(tx.from))).append("\",\n");
        sb.append("        \"gas\": \"").append(hex(tx.gas)).append("\",\n");
        sb.append("        \"value\": \"0x0\",\n");
        sb.append("        \"input\": \"").append(esc(tx.input)).append("\",\n");
        sb.append("        \"nonce\": \"").append(hex(tx.nonce)).append("\",\n");
        sb.append("        \"chainId\": \"").append(hex(chainId)).append("\"\n");
        sb.append("      },\n");
        sb.append("      \"additionalContracts\": [],\n");
        sb.append("      \"isFixedGasLimit\": false\n");
        sb.append("    }");
    }

    private static void appendReceipt(StringBuilder sb, Tx tx) {
        sb.append("    {\n");
        sb.append("      \"transactionHash\": ").append(jsonOrNull(tx.txHash)).append(",\n");
        sb.append("      \"transactionIndex\": \"0x0\",\n");
        sb.append("      \"blockNumber\": \"").append(hex(tx.blockNumber)).append("\",\n");
        sb.append("      \"contractAddress\": ").append(jsonOrNull(lower(tx.contractAddress))).append(",\n");
        sb.append("      \"gasUsed\": \"").append(hex(tx.gasUsed)).append("\",\n");
        sb.append("      \"status\": \"").append(hex(tx.status)).append("\"\n");
        sb.append("    }");
    }

    private static String hex(long v) {
        return "0x" + Long.toHexString(v);
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase();
    }

    private static String jsonOrNull(String s) {
        return s == null ? "null" : "\"" + esc(s) + "\"";
    }

    private static String ensure0x(String hex) {
        if (hex == null || hex.isEmpty()) return "0x";
        return hex.startsWith("0x") || hex.startsWith("0X") ? hex : "0x" + hex;
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
