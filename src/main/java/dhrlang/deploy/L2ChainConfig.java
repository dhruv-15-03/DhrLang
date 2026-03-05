package dhrlang.deploy;

import java.util.*;

/**
 * Configuration for Ethereum mainnet and Layer 2 chains supported by DhrLang.
 *
 * <p>Each chain configuration includes:
 * <ul>
 *   <li>Chain ID (EIP-155)</li>
 *   <li>Chain name</li>
 *   <li>RPC URL template</li>
 *   <li>Block explorer URL</li>
 *   <li>Gas limit</li>
 *   <li>Average block time</li>
 *   <li>Native token symbol</li>
 *   <li>Whether the chain uses EIP-1559 (type-2 transactions)</li>
 * </ul>
 *
 * <p><b>User story:</b> SC-504 — As a developer, I want L2 deployment (Arbitrum, Base).</p>
 */
public final class L2ChainConfig {

    // ── Pre-defined chain configurations ─────────────────────────────────

    /** Ethereum Mainnet (chainId 1). */
    public static final L2ChainConfig ETHEREUM_MAINNET = new L2ChainConfig(
            "1", "Ethereum Mainnet", "https://mainnet.infura.io/v3/{API_KEY}",
            "https://etherscan.io", 30_000_000L, 12.0, "ETH", true, ChainType.L1
    );

    /** Ethereum Sepolia Testnet (chainId 11155111). */
    public static final L2ChainConfig SEPOLIA = new L2ChainConfig(
            "11155111", "Sepolia Testnet", "https://sepolia.infura.io/v3/{API_KEY}",
            "https://sepolia.etherscan.io", 30_000_000L, 12.0, "ETH", true, ChainType.TESTNET
    );

    /** Arbitrum One (chainId 42161). */
    public static final L2ChainConfig ARBITRUM_ONE = new L2ChainConfig(
            "42161", "Arbitrum One", "https://arb1.arbitrum.io/rpc",
            "https://arbiscan.io", 1_125_899_906_842_624L, 0.25, "ETH", true, ChainType.L2_OPTIMISTIC
    );

    /** Arbitrum Sepolia Testnet (chainId 421614). */
    public static final L2ChainConfig ARBITRUM_SEPOLIA = new L2ChainConfig(
            "421614", "Arbitrum Sepolia", "https://sepolia-rollup.arbitrum.io/rpc",
            "https://sepolia.arbiscan.io", 1_125_899_906_842_624L, 0.25, "ETH", true, ChainType.TESTNET
    );

    /** Base Mainnet (chainId 8453). */
    public static final L2ChainConfig BASE_MAINNET = new L2ChainConfig(
            "8453", "Base", "https://mainnet.base.org",
            "https://basescan.org", 30_000_000L, 2.0, "ETH", true, ChainType.L2_OPTIMISTIC
    );

    /** Base Sepolia Testnet (chainId 84532). */
    public static final L2ChainConfig BASE_SEPOLIA = new L2ChainConfig(
            "84532", "Base Sepolia", "https://sepolia.base.org",
            "https://sepolia.basescan.org", 30_000_000L, 2.0, "ETH", true, ChainType.TESTNET
    );

    /** Optimism Mainnet (chainId 10). */
    public static final L2ChainConfig OPTIMISM = new L2ChainConfig(
            "10", "Optimism", "https://mainnet.optimism.io",
            "https://optimistic.etherscan.io", 30_000_000L, 2.0, "ETH", true, ChainType.L2_OPTIMISTIC
    );

    /** Polygon POS (chainId 137). */
    public static final L2ChainConfig POLYGON = new L2ChainConfig(
            "137", "Polygon", "https://polygon-rpc.com",
            "https://polygonscan.com", 30_000_000L, 2.0, "MATIC", true, ChainType.SIDECHAIN
    );

    /** Local Anvil / Hardhat (chainId 31337). */
    public static final L2ChainConfig LOCAL_ANVIL = new L2ChainConfig(
            "31337", "Local (Anvil)", "http://127.0.0.1:8545",
            null, 30_000_000L, 1.0, "ETH", true, ChainType.LOCAL
    );

    // ── Chain Types ──────────────────────────────────────────────────────

    /**
     * Type of blockchain network.
     */
    public enum ChainType {
        L1,
        L2_OPTIMISTIC,
        L2_ZK,
        SIDECHAIN,
        TESTNET,
        LOCAL
    }

    // ── Registry ─────────────────────────────────────────────────────────

    private static final Map<String, L2ChainConfig> REGISTRY = new LinkedHashMap<>();
    static {
        register(ETHEREUM_MAINNET);
        register(SEPOLIA);
        register(ARBITRUM_ONE);
        register(ARBITRUM_SEPOLIA);
        register(BASE_MAINNET);
        register(BASE_SEPOLIA);
        register(OPTIMISM);
        register(POLYGON);
        register(LOCAL_ANVIL);
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private final String chainId;
    private final String name;
    private final String rpcUrlTemplate;
    private final String explorerUrl;
    private final long gasLimit;
    private final double blockTimeSeconds;
    private final String nativeToken;
    private final boolean eip1559;
    private final ChainType chainType;

    // ── Constructor ──────────────────────────────────────────────────────

    public L2ChainConfig(String chainId, String name, String rpcUrlTemplate,
                         String explorerUrl, long gasLimit, double blockTimeSeconds,
                         String nativeToken, boolean eip1559, ChainType chainType) {
        this.chainId = chainId;
        this.name = name;
        this.rpcUrlTemplate = rpcUrlTemplate;
        this.explorerUrl = explorerUrl;
        this.gasLimit = gasLimit;
        this.blockTimeSeconds = blockTimeSeconds;
        this.nativeToken = nativeToken;
        this.eip1559 = eip1559;
        this.chainType = chainType;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public String getChainId() { return chainId; }
    public String getName() { return name; }
    public String getRpcUrlTemplate() { return rpcUrlTemplate; }
    public String getExplorerUrl() { return explorerUrl; }
    public long getGasLimit() { return gasLimit; }
    public double getBlockTimeSeconds() { return blockTimeSeconds; }
    public String getNativeToken() { return nativeToken; }
    public boolean isEip1559() { return eip1559; }
    public ChainType getChainType() { return chainType; }

    // ── Derived ──────────────────────────────────────────────────────────

    /**
     * Get the RPC URL with an API key substituted.
     */
    public String getRpcUrl(String apiKey) {
        if (rpcUrlTemplate == null) return null;
        return rpcUrlTemplate.replace("{API_KEY}", apiKey != null ? apiKey : "");
    }

    /**
     * Get the block explorer URL for a transaction hash.
     */
    public String getTxUrl(String txHash) {
        if (explorerUrl == null || txHash == null) return null;
        return explorerUrl + "/tx/" + txHash;
    }

    /**
     * Get the block explorer URL for a contract address.
     */
    public String getContractUrl(String address) {
        if (explorerUrl == null || address == null) return null;
        return explorerUrl + "/address/" + address;
    }

    /**
     * Check if this is a production (mainnet) chain.
     */
    public boolean isProduction() {
        return chainType == ChainType.L1 || chainType == ChainType.L2_OPTIMISTIC
                || chainType == ChainType.L2_ZK || chainType == ChainType.SIDECHAIN;
    }

    /**
     * Check if this is an L2 chain.
     */
    public boolean isL2() {
        return chainType == ChainType.L2_OPTIMISTIC || chainType == ChainType.L2_ZK;
    }

    /**
     * Check if this is a testnet or local network.
     */
    public boolean isTestNetwork() {
        return chainType == ChainType.TESTNET || chainType == ChainType.LOCAL;
    }

    // ── Registry Methods ─────────────────────────────────────────────────

    /**
     * Register a custom chain configuration.
     */
    public static void register(L2ChainConfig config) {
        REGISTRY.put(config.getChainId(), config);
    }

    /**
     * Look up a chain by its chain ID.
     */
    public static L2ChainConfig byChainId(String chainId) {
        return REGISTRY.get(chainId);
    }

    /**
     * Look up a chain by its name (case-insensitive).
     */
    public static L2ChainConfig byName(String name) {
        for (L2ChainConfig config : REGISTRY.values()) {
            if (config.getName().equalsIgnoreCase(name)) {
                return config;
            }
        }
        return null;
    }

    /**
     * Get all registered chains.
     */
    public static Collection<L2ChainConfig> allChains() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * Get all production (non-test) chains.
     */
    public static List<L2ChainConfig> productionChains() {
        List<L2ChainConfig> result = new ArrayList<>();
        for (L2ChainConfig config : REGISTRY.values()) {
            if (config.isProduction()) result.add(config);
        }
        return result;
    }

    /**
     * Get all L2 chains.
     */
    public static List<L2ChainConfig> l2Chains() {
        List<L2ChainConfig> result = new ArrayList<>();
        for (L2ChainConfig config : REGISTRY.values()) {
            if (config.isL2()) result.add(config);
        }
        return result;
    }

    /**
     * Get all testnet chains.
     */
    public static List<L2ChainConfig> testnetChains() {
        List<L2ChainConfig> result = new ArrayList<>();
        for (L2ChainConfig config : REGISTRY.values()) {
            if (config.isTestNetwork()) result.add(config);
        }
        return result;
    }

    // ── Format ───────────────────────────────────────────────────────────

    /**
     * Format a human-readable summary of this chain config.
     */
    public String formatSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Chain: ").append(name).append(" (").append(chainId).append(")\n");
        sb.append("  Type:        ").append(chainType).append('\n');
        sb.append("  Gas Limit:   ").append(gasLimit).append('\n');
        sb.append("  Block Time:  ").append(blockTimeSeconds).append("s\n");
        sb.append("  Token:       ").append(nativeToken).append('\n');
        sb.append("  EIP-1559:    ").append(eip1559 ? "Yes" : "No").append('\n');
        if (explorerUrl != null) {
            sb.append("  Explorer:    ").append(explorerUrl).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return name + " (" + chainId + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof L2ChainConfig)) return false;
        return chainId.equals(((L2ChainConfig) o).chainId);
    }

    @Override
    public int hashCode() {
        return chainId.hashCode();
    }
}
