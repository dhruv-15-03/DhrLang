package dhrlang.deploy;

import dhrlang.evm.EvmContractCompiler;
import dhrlang.evm.EvmContractCompiler.ContractArtifact;
import dhrlang.evm.FunctionSelector;

import java.util.*;

/**
 * Manages deployment of compiled DhrLang contracts to Ethereum and L2 networks.
 *
 * <p>Builds unsigned deployment transactions, tracks deployed addresses,
 * generates deployment scripts, and supports verification.</p>
 *
 * <p><b>User story:</b> SC-503 — As a developer, I want Ethereum mainnet deployment.</p>
 *
 * @see EvmContractCompiler
 * @see L2ChainConfig
 */
public final class DeploymentManager {

    // ── DeploymentStatus ─────────────────────────────────────────────────

    /**
     * Status of a single contract deployment.
     */
    public enum DeploymentStatus {
        PENDING,
        TX_BUILT,
        SUBMITTED,
        CONFIRMED,
        FAILED,
        VERIFIED
    }

    // ── DeploymentTx ─────────────────────────────────────────────────────

    /**
     * Represents an unsigned deployment transaction.
     */
    public static final class DeploymentTx {
        private final String contractName;
        private final String chainId;
        private final String chainName;
        private final String creationBytecodeHex;
        private final long estimatedGas;
        private final long gasLimit;
        private final long maxFeePerGas;       // wei
        private final long maxPriorityFeePerGas; // wei
        private final String fromAddress;
        private final long nonce;

        DeploymentTx(String contractName, String chainId, String chainName,
                     String creationBytecodeHex, long estimatedGas, long gasLimit,
                     long maxFeePerGas, long maxPriorityFeePerGas,
                     String fromAddress, long nonce) {
            this.contractName = contractName;
            this.chainId = chainId;
            this.chainName = chainName;
            this.creationBytecodeHex = creationBytecodeHex;
            this.estimatedGas = estimatedGas;
            this.gasLimit = gasLimit;
            this.maxFeePerGas = maxFeePerGas;
            this.maxPriorityFeePerGas = maxPriorityFeePerGas;
            this.fromAddress = fromAddress;
            this.nonce = nonce;
        }

        public String getContractName() { return contractName; }
        public String getChainId() { return chainId; }
        public String getChainName() { return chainName; }
        public String getCreationBytecodeHex() { return creationBytecodeHex; }
        public long getEstimatedGas() { return estimatedGas; }
        public long getGasLimit() { return gasLimit; }
        public long getMaxFeePerGas() { return maxFeePerGas; }
        public long getMaxPriorityFeePerGas() { return maxPriorityFeePerGas; }
        public String getFromAddress() { return fromAddress; }
        public long getNonce() { return nonce; }
    }

    // ── DeploymentRecord ─────────────────────────────────────────────────

    /**
     * Record of a completed (or pending) deployment.
     */
    public static final class DeploymentRecord {
        private final String contractName;
        private final String chainId;
        private final String chainName;
        private String contractAddress;
        private String txHash;
        private DeploymentStatus status;
        private long blockNumber;
        private long gasUsed;
        private final long timestamp;

        DeploymentRecord(String contractName, String chainId, String chainName) {
            this.contractName = contractName;
            this.chainId = chainId;
            this.chainName = chainName;
            this.status = DeploymentStatus.PENDING;
            this.timestamp = System.currentTimeMillis();
        }

        public String getContractName() { return contractName; }
        public String getChainId() { return chainId; }
        public String getChainName() { return chainName; }
        public String getContractAddress() { return contractAddress; }
        public String getTxHash() { return txHash; }
        public DeploymentStatus getStatus() { return status; }
        public long getBlockNumber() { return blockNumber; }
        public long getGasUsed() { return gasUsed; }
        public long getTimestamp() { return timestamp; }

        void setContractAddress(String contractAddress) { this.contractAddress = contractAddress; }
        void setTxHash(String txHash) { this.txHash = txHash; }
        void setStatus(DeploymentStatus status) { this.status = status; }
        void setBlockNumber(long blockNumber) { this.blockNumber = blockNumber; }
        void setGasUsed(long gasUsed) { this.gasUsed = gasUsed; }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private String deployerAddress = "0x0000000000000000000000000000000000000000";
    private long nonce = 0;
    private long maxFeePerGas = 30_000_000_000L;         // 30 gwei
    private long maxPriorityFeePerGas = 2_000_000_000L;  // 2 gwei
    private double gasMultiplier = 1.2;                   // 20% buffer
    private L2ChainConfig targetChain = L2ChainConfig.ETHEREUM_MAINNET;

    private final List<DeploymentRecord> deployments = new ArrayList<>();
    private final Map<String, String> deployedAddresses = new LinkedHashMap<>();

    // ── Configuration ────────────────────────────────────────────────────

    public DeploymentManager setDeployerAddress(String address) {
        this.deployerAddress = address;
        return this;
    }

    public DeploymentManager setNonce(long nonce) {
        this.nonce = nonce;
        return this;
    }

    public DeploymentManager setMaxFeePerGas(long weiPerGas) {
        this.maxFeePerGas = weiPerGas;
        return this;
    }

    public DeploymentManager setMaxPriorityFeePerGas(long weiPerGas) {
        this.maxPriorityFeePerGas = weiPerGas;
        return this;
    }

    public DeploymentManager setGasMultiplier(double multiplier) {
        this.gasMultiplier = multiplier;
        return this;
    }

    public DeploymentManager setTargetChain(L2ChainConfig chain) {
        this.targetChain = chain;
        return this;
    }

    public String getDeployerAddress() { return deployerAddress; }
    public long getNonce() { return nonce; }
    public long getMaxFeePerGas() { return maxFeePerGas; }
    public long getMaxPriorityFeePerGas() { return maxPriorityFeePerGas; }
    public double getGasMultiplier() { return gasMultiplier; }
    public L2ChainConfig getTargetChain() { return targetChain; }

    // ── Deployment Transaction Building ──────────────────────────────────

    /**
     * Build an unsigned deployment transaction for a single contract artifact.
     *
     * @param artifact compiled contract artifact
     * @return the unsigned deployment transaction
     */
    public DeploymentTx buildDeployTx(ContractArtifact artifact) {
        long estimatedGas = artifact.getEstimatedDeployGas();
        long gasLimit = (long) (estimatedGas * gasMultiplier);

        // Cap gas limit at chain maximum
        gasLimit = Math.min(gasLimit, targetChain.getGasLimit());

        String bytecodeHex = artifact.getCreationBytecodeHex();

        DeploymentTx tx = new DeploymentTx(
                artifact.getContractName(),
                targetChain.getChainId(),
                targetChain.getName(),
                bytecodeHex,
                estimatedGas,
                gasLimit,
                maxFeePerGas,
                maxPriorityFeePerGas,
                deployerAddress,
                nonce
        );

        // Track the deployment
        DeploymentRecord record = new DeploymentRecord(
                artifact.getContractName(),
                targetChain.getChainId(),
                targetChain.getName()
        );
        record.setStatus(DeploymentStatus.TX_BUILT);
        deployments.add(record);

        nonce++;
        return tx;
    }

    /**
     * Build deployment transactions for multiple artifacts.
     */
    public List<DeploymentTx> buildDeployTxBatch(List<ContractArtifact> artifacts) {
        List<DeploymentTx> txs = new ArrayList<>();
        for (ContractArtifact artifact : artifacts) {
            txs.add(buildDeployTx(artifact));
        }
        return txs;
    }

    // ── Deployment Record Management ─────────────────────────────────────

    /**
     * Record a confirmed deployment.
     */
    public void recordDeployment(String contractName, String contractAddress,
                                  String txHash, long blockNumber, long gasUsed) {
        DeploymentRecord record = findRecord(contractName);
        if (record == null) {
            record = new DeploymentRecord(contractName, targetChain.getChainId(),
                    targetChain.getName());
            deployments.add(record);
        }
        record.setContractAddress(contractAddress);
        record.setTxHash(txHash);
        record.setBlockNumber(blockNumber);
        record.setGasUsed(gasUsed);
        record.setStatus(DeploymentStatus.CONFIRMED);
        deployedAddresses.put(contractName, contractAddress);
    }

    /**
     * Mark a deployment as verified on block explorer.
     */
    public void markVerified(String contractName) {
        DeploymentRecord record = findRecord(contractName);
        if (record != null && record.getStatus() == DeploymentStatus.CONFIRMED) {
            record.setStatus(DeploymentStatus.VERIFIED);
        }
    }

    /**
     * Get all deployment records.
     */
    public List<DeploymentRecord> getDeployments() {
        return Collections.unmodifiableList(deployments);
    }

    /**
     * Get the deployed address of a contract, or null.
     */
    public String getDeployedAddress(String contractName) {
        return deployedAddresses.get(contractName);
    }

    /**
     * Get all deployed addresses.
     */
    public Map<String, String> getDeployedAddresses() {
        return Collections.unmodifiableMap(deployedAddresses);
    }

    // ── Script Generation ────────────────────────────────────────────────

    /**
     * Generate a Foundry-compatible deployment script.
     */
    public String generateFoundryScript(List<ContractArtifact> artifacts) {
        StringBuilder sb = new StringBuilder();
        sb.append("// SPDX-License-Identifier: MIT\n");
        sb.append("// Auto-generated by DhrLang Deployment Manager\n");
        sb.append("// Target chain: ").append(targetChain.getName())
                .append(" (chainId: ").append(targetChain.getChainId()).append(")\n\n");
        sb.append("pragma solidity ^0.8.20;\n\n");
        sb.append("import \"forge-std/Script.sol\";\n\n");
        sb.append("contract Deploy extends Script {\n");
        sb.append("    function run() external {\n");
        sb.append("        vm.startBroadcast();\n\n");

        for (ContractArtifact artifact : artifacts) {
            sb.append("        // Deploy ").append(artifact.getContractName()).append('\n');
            sb.append("        bytes memory bytecode_").append(artifact.getContractName())
                    .append(" = hex\"").append(artifact.getCreationBytecodeHex()).append("\";\n");
            sb.append("        address ").append(toVarName(artifact.getContractName()))
                    .append(";\n");
            sb.append("        assembly {\n");
            sb.append("            ").append(toVarName(artifact.getContractName()))
                    .append(" := create(0, add(bytecode_").append(artifact.getContractName())
                    .append(", 0x20), mload(bytecode_").append(artifact.getContractName())
                    .append("))\n");
            sb.append("        }\n");
            sb.append("        require(").append(toVarName(artifact.getContractName()))
                    .append(" != address(0), \"Deploy failed: ").append(artifact.getContractName())
                    .append("\");\n");
            sb.append("        console.log(\"").append(artifact.getContractName())
                    .append(" deployed:\", ").append(toVarName(artifact.getContractName()))
                    .append(");\n\n");
        }

        sb.append("        vm.stopBroadcast();\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Generate an ethers.js deployment script.
     */
    public String generateEthersScript(List<ContractArtifact> artifacts) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated by DhrLang Deployment Manager\n");
        sb.append("// Target chain: ").append(targetChain.getName())
                .append(" (chainId: ").append(targetChain.getChainId()).append(")\n\n");
        sb.append("const { ethers } = require(\"ethers\");\n\n");
        sb.append("async function main() {\n");
        sb.append("  const provider = new ethers.JsonRpcProvider(process.env.RPC_URL);\n");
        sb.append("  const wallet = new ethers.Wallet(process.env.PRIVATE_KEY, provider);\n\n");

        for (ContractArtifact artifact : artifacts) {
            sb.append("  // Deploy ").append(artifact.getContractName()).append('\n');
            sb.append("  const tx_").append(toVarName(artifact.getContractName())).append(" = {\n");
            sb.append("    data: \"0x").append(artifact.getCreationBytecodeHex()).append("\",\n");
            sb.append("    gasLimit: ")
                    .append((long) (artifact.getEstimatedDeployGas() * gasMultiplier))
                    .append(",\n");
            sb.append("  };\n");
            sb.append("  const receipt_").append(toVarName(artifact.getContractName()))
                    .append(" = await (await wallet.sendTransaction(tx_")
                    .append(toVarName(artifact.getContractName())).append(")).wait();\n");
            sb.append("  console.log(\"").append(artifact.getContractName())
                    .append(" deployed:\", receipt_").append(toVarName(artifact.getContractName()))
                    .append(".contractAddress);\n\n");
        }

        sb.append("}\n\nmain().catch(console.error);\n");
        return sb.toString();
    }

    // ── Deployment Summary ───────────────────────────────────────────────

    /**
     * Format a summary of all deployments.
     */
    public String formatSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║            DhrLang Deployment Summary                       ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        sb.append("Target Chain: ").append(targetChain.getName())
                .append(" (").append(targetChain.getChainId()).append(")\n");
        sb.append("Deployer:     ").append(deployerAddress).append("\n\n");

        if (deployments.isEmpty()) {
            sb.append("  No deployments recorded.\n");
        } else {
            for (DeploymentRecord r : deployments) {
                sb.append("  ").append(r.getContractName())
                        .append("  [").append(r.getStatus()).append("]\n");
                if (r.getContractAddress() != null) {
                    sb.append("    Address: ").append(r.getContractAddress()).append('\n');
                }
                if (r.getTxHash() != null) {
                    sb.append("    TX:      ").append(r.getTxHash()).append('\n');
                }
                if (r.getBlockNumber() > 0) {
                    sb.append("    Block:   ").append(r.getBlockNumber()).append('\n');
                }
                if (r.getGasUsed() > 0) {
                    sb.append("    Gas:     ").append(r.getGasUsed()).append('\n');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private DeploymentRecord findRecord(String contractName) {
        for (int i = deployments.size() - 1; i >= 0; i--) {
            if (deployments.get(i).getContractName().equals(contractName)) {
                return deployments.get(i);
            }
        }
        return null;
    }

    static String toVarName(String contractName) {
        if (contractName == null || contractName.isEmpty()) return "contract";
        return Character.toLowerCase(contractName.charAt(0)) + contractName.substring(1);
    }

    /**
     * Estimate total deployment cost across all built transactions in ETH.
     */
    public double estimateTotalCostEth() {
        long totalGas = 0;
        for (DeploymentRecord r : deployments) {
            totalGas += r.getGasUsed() > 0 ? r.getGasUsed() : 0;
        }
        // fallback: use max fee * estimated gas for pending
        if (totalGas == 0) {
            for (DeploymentRecord r : deployments) {
                // simplified estimate
                totalGas += 53_000L;
            }
        }
        return (totalGas * maxFeePerGas) / 1e18;
    }

    /**
     * Reset all deployment state.
     */
    public void reset() {
        deployments.clear();
        deployedAddresses.clear();
        nonce = 0;
    }
}
