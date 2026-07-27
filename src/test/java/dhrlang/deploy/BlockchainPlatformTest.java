package dhrlang.deploy;

import dhrlang.evm.EvmContractCompiler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the blockchain deployment gap-closing modules:
 * WalletManager, GasEstimator, ContractVerifier, BlockchainCLI.
 */
@DisplayName("Blockchain Platform Tests")
class BlockchainPlatformTest {

    // ═══════════════════════════════════════════════════════════════
    //  WalletManager Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WalletManager Tests")
    class WalletManagerTests {

        @Test
        @DisplayName("Set explicit key and derive address")
        void setExplicitKeyAndDeriveAddress() {
            WalletManager wallet = new WalletManager();
            // Valid 32-byte private key
            wallet.setExplicitKey("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
            assertTrue(wallet.isLoaded());
            assertNotNull(wallet.getAddress());
            assertTrue(wallet.getAddress().startsWith("0x"));
            assertEquals(42, wallet.getAddress().length()); // 0x + 40 hex chars
            assertEquals(WalletManager.KeySource.EXPLICIT, wallet.getKeySource());
        }

        @Test
        @DisplayName("Get private key hex roundtrip")
        void privateKeyHexRoundtrip() {
            WalletManager wallet = new WalletManager();
            String key = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
            wallet.setExplicitKey(key);
            assertEquals(key, wallet.getPrivateKeyHex());
        }

        @Test
        @DisplayName("Clear wipes key from memory")
        void clearWipesKey() {
            WalletManager wallet = new WalletManager();
            wallet.setExplicitKey("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
            assertTrue(wallet.isLoaded());
            wallet.clear();
            assertFalse(wallet.isLoaded());
            assertThrows(WalletManager.WalletException.class, wallet::getAddress);
        }

        @Test
        @DisplayName("Reject invalid key — too short")
        void rejectShortKey() {
            WalletManager wallet = new WalletManager();
            assertThrows(WalletManager.WalletException.class, () ->
                    wallet.setExplicitKey("0x1234"));
        }

        @Test
        @DisplayName("Reject invalid key — non-hex characters")
        void rejectNonHexKey() {
            WalletManager wallet = new WalletManager();
            assertThrows(WalletManager.WalletException.class, () ->
                    wallet.setExplicitKey("0xGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG"));
        }

        @Test
        @DisplayName("Reject zero key (out of secp256k1 range)")
        void rejectZeroKey() {
            WalletManager wallet = new WalletManager();
            assertThrows(WalletManager.WalletException.class, () ->
                    wallet.setExplicitKey("0x0000000000000000000000000000000000000000000000000000000000000000"));
        }

        @Test
        @DisplayName("Accept key without 0x prefix")
        void acceptKeyWithoutPrefix() {
            WalletManager wallet = new WalletManager();
            wallet.setExplicitKey("ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
            assertTrue(wallet.isLoaded());
        }

        @Test
        @DisplayName("Create and load encrypted keystore")
        void createAndLoadKeystore(@TempDir Path tempDir) {
            String key = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
            String password = "test-password-123";
            Path keystorePath = tempDir.resolve("keystore.enc");

            // Create
            WalletManager.createKeystore(keystorePath, key, password);
            assertTrue(Files.exists(keystorePath));

            // Load
            WalletManager wallet = new WalletManager();
            wallet.loadFromKeystore(keystorePath, password);
            assertTrue(wallet.isLoaded());
            assertEquals("ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
                    wallet.getPrivateKeyHex());
            assertEquals(WalletManager.KeySource.KEYSTORE_FILE, wallet.getKeySource());
        }

        @Test
        @DisplayName("Wrong password throws WalletException")
        void wrongPasswordThrows(@TempDir Path tempDir) {
            String key = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
            Path keystorePath = tempDir.resolve("keystore.enc");

            WalletManager.createKeystore(keystorePath, key, "correct-password");

            WalletManager wallet = new WalletManager();
            assertThrows(WalletManager.WalletException.class, () ->
                    wallet.loadFromKeystore(keystorePath, "wrong-password"));
        }

        @Test
        @DisplayName("Missing keystore throws WalletException")
        void missingKeystoreThrows(@TempDir Path tempDir) {
            WalletManager wallet = new WalletManager();
            assertThrows(WalletManager.WalletException.class, () ->
                    wallet.loadFromKeystore(tempDir.resolve("nonexistent.enc"), "password"));
        }

        @Test
        @DisplayName("Sign deployment transaction produces valid hex")
        void signDeployTx() {
            WalletManager wallet = new WalletManager();
            wallet.setExplicitKey("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

            DeploymentManager deployer = new DeploymentManager()
                    .setTargetChain(L2ChainConfig.SEPOLIA)
                    .setDeployerAddress(wallet.getAddress());

            // Create a minimal artifact for testing
            DeploymentManager.DeploymentTx tx = new DeploymentManager.DeploymentTx(
                    "TestContract", "11155111", "Sepolia",
                    "6080604052", 100000, 120000,
                    30000000000L, 2000000000L,
                    wallet.getAddress(), 0
            );

            String signedTx = wallet.signDeployTx(tx);
            assertNotNull(signedTx);
            assertTrue(signedTx.startsWith("0x"));
            assertTrue(signedTx.length() > 10);
        }

        @Test
        @DisplayName("getPrivateKeyHex throws when not loaded")
        void getKeyThrowsWhenNotLoaded() {
            WalletManager wallet = new WalletManager();
            assertThrows(WalletManager.WalletException.class, wallet::getPrivateKeyHex);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GasEstimator Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GasEstimator Tests")
    class GasEstimatorTests {

        @Test
        @DisplayName("Estimate deploy gas with valid bytecode")
        void estimateDeployGas() {
            byte[] creation = new byte[100]; // 100 bytes of zeros
            byte[] runtime = new byte[200];
            GasEstimator.GasBreakdown bd = GasEstimator.estimateDeployGas(creation, runtime, 3);

            assertTrue(bd.getTotalEstimate() > 0);
            assertEquals(21_000, bd.getIntrinsicGas());
            assertEquals(32_000, bd.getCreationGas());
            assertEquals(200L * 200, bd.getCodeDepositGas()); // 200 bytes * 200 gas/byte
            assertEquals(200, bd.getBytecodeSize());
            assertEquals(3, bd.getStorageSlotCount());
            assertTrue(bd.getEstimatedExecutionGas() >= 60_000); // 3 slots * 20k each + overhead
        }

        @Test
        @DisplayName("Calldata gas: zero bytes cost 4, non-zero cost 16")
        void calldataGasCost() {
            byte[] bytecodeAllZero = new byte[10]; // 10 zero bytes
            GasEstimator.GasBreakdown bd1 = GasEstimator.estimateDeployGas(bytecodeAllZero, new byte[0], 0);
            assertEquals(40, bd1.getCalldataGas()); // 10 * 4

            byte[] bytecodeNonZero = new byte[]{1, 2, 3, 4, 5}; // 5 non-zero bytes
            GasEstimator.GasBreakdown bd2 = GasEstimator.estimateDeployGas(bytecodeNonZero, new byte[0], 0);
            assertEquals(80, bd2.getCalldataGas()); // 5 * 16
        }

        @Test
        @DisplayName("Null bytecode returns zero gas")
        void nullBytecodeZeroGas() {
            GasEstimator.GasBreakdown bd = GasEstimator.estimateDeployGas(null, null, 0);
            assertEquals(0, bd.getCalldataGas());
            assertEquals(0, bd.getCodeDepositGas());
            assertEquals(0, bd.getBytecodeSize());
        }

        @Test
        @DisplayName("EstimateCosts returns 4 price levels")
        void estimateCostsSets() {
            GasEstimator.GasBreakdown bd = GasEstimator.estimateDeployGas(new byte[100], new byte[200], 2);
            var costs = GasEstimator.estimateCosts(bd);
            assertEquals(4, costs.size());
            for (var c : costs.values()) {
                assertTrue(c.getCostEth() >= 0);
                assertTrue(c.getGasUsed() > 0);
            }
        }

        @Test
        @DisplayName("L2 cost estimates use lower gas prices")
        void l2CostEstimates() {
            GasEstimator.GasBreakdown bd = GasEstimator.estimateDeployGas(new byte[100], new byte[200], 2);
            var l2Costs = GasEstimator.estimateL2Costs(bd, L2ChainConfig.ARBITRUM_ONE);
            assertEquals(3, l2Costs.size());
            for (var key : l2Costs.keySet()) {
                assertTrue(key.contains("Arbitrum"));
            }
        }

        @Test
        @DisplayName("Format report produces ASCII box")
        void formatReportAsciiBox() {
            GasEstimator.GasBreakdown bd = GasEstimator.estimateDeployGas(new byte[50], new byte[100], 1);
            String report = GasEstimator.formatReport(bd);
            assertTrue(report.contains("GAS ESTIMATION REPORT"));
            assertTrue(report.contains("Intrinsic"));
            assertTrue(report.contains("Calldata"));
            assertTrue(report.contains("TOTAL"));
        }

        @Test
        @DisplayName("Format JSON produces valid structure")
        void formatJson() {
            GasEstimator.GasBreakdown bd = GasEstimator.estimateDeployGas(new byte[50], new byte[100], 1);
            String json = GasEstimator.formatJson(bd);
            assertTrue(json.contains("\"totalGas\""));
            assertTrue(json.contains("\"gasBreakdown\""));
            assertTrue(json.contains("\"costEstimates\""));
            assertTrue(json.startsWith("{"));
            assertTrue(json.endsWith("}"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ContractVerifier Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ContractVerifier Tests")
    class ContractVerifierTests {

        @Test
        @DisplayName("Verify without API key returns error")
        void verifyWithoutApiKey() {
            ContractVerifier verifier = new ContractVerifier().setApiKey(null);
            var result = verifier.verify(null, "0x1234", "source");
            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("API key"));
        }

        @Test
        @DisplayName("Dry run succeeds without network")
        void dryRunSucceeds() {
            ContractVerifier verifier = new ContractVerifier()
                    .setApiKey("test-key")
                    .setChain(L2ChainConfig.SEPOLIA)
                    .setDryRun(true);

            // Create a minimal mock artifact
            var artifact = createMockArtifact("TestToken");
            var result = verifier.verify(artifact, "0xabcdef1234567890abcdef1234567890abcdef12", "source");
            assertTrue(result.isSuccess());
            assertTrue(result.getMessage().contains("Dry run"));
            assertNotNull(result.getExplorerUrl());
        }

        @Test
        @DisplayName("Generate verify command includes all options")
        void generateVerifyCommand() {
            ContractVerifier verifier = new ContractVerifier()
                    .setChain(L2ChainConfig.SEPOLIA);

            var artifact = createMockArtifact("MyToken");
            String cmd = verifier.generateVerifyCommand(artifact, "0xabcdef");
            assertTrue(cmd.contains("MyToken"));
            assertTrue(cmd.contains("0xabcdef"));
            assertTrue(cmd.contains("Sepolia"));
            assertTrue(cmd.contains("forge verify-contract"));
            assertTrue(cmd.contains("DhrLang-2.0.0"));
        }

        @Test
        @DisplayName("Etherscan chain → API URL mapping")
        void chainApiUrlMapping() {
            ContractVerifier verifier = new ContractVerifier()
                    .setChain(L2ChainConfig.ETHEREUM_MAINNET)
                    .setApiKey("key")
                    .setDryRun(true);
            var result = verifier.verify(createMockArtifact("T"), "0x1234", "src");
            assertTrue(result.isSuccess());
            assertTrue(result.getExplorerUrl().contains("etherscan.io"));
        }

        @Test
        @DisplayName("Local chain has no API URL")
        void localChainNoApiUrl() {
            ContractVerifier verifier = new ContractVerifier()
                    .setChain(L2ChainConfig.LOCAL_ANVIL)
                    .setApiKey("key");
            var result = verifier.verify(createMockArtifact("T"), "0x1234", "src");
            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("No API URL"));
        }

        private EvmContractCompiler.ContractArtifact createMockArtifact(String name) {
            return new EvmContractCompiler.ContractArtifact(
                    name, new byte[]{0x60, (byte) 0x80}, new byte[]{0x60, (byte) 0x80},
                    "[]", java.util.List.of(), null, 100000
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  BlockchainCLI Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BlockchainCLI Tests")
    class BlockchainCLITests {

        @Test
        @DisplayName("Parse 'compile' subcommand")
        void parseCompileSubcommand() {
            var opts = BlockchainCLI.parseArgs(
                    new String[]{"contract", "compile", "--output=out", "token.dhr"}, 1);
            assertEquals("compile", opts.subcommand);
            assertEquals("out", opts.outputDir);
        }

        @Test
        @DisplayName("Parse 'deploy' subcommand with network")
        void parseDeploySubcommand() {
            var opts = BlockchainCLI.parseArgs(
                    new String[]{"contract", "deploy", "--network=sepolia", "--dry-run", "token.dhr"}, 1);
            assertEquals("deploy", opts.subcommand);
            assertEquals("sepolia", opts.network);
            assertTrue(opts.dryRun);
        }

        @Test
        @DisplayName("Parse 'verify' subcommand with address")
        void parseVerifySubcommand() {
            var opts = BlockchainCLI.parseArgs(
                    new String[]{"contract", "verify", "--address=0xabcdef", "--network=arbitrum"}, 1);
            assertEquals("verify", opts.subcommand);
            assertEquals("0xabcdef", opts.address);
            assertEquals("arbitrum", opts.network);
        }

        @Test
        @DisplayName("Parse 'gas' subcommand with JSON flag")
        void parseGasSubcommand() {
            var opts = BlockchainCLI.parseArgs(
                    new String[]{"contract", "gas", "--json", "token.dhr"}, 1);
            assertEquals("gas", opts.subcommand);
            assertTrue(opts.json);
        }

        @Test
        @DisplayName("Parse 'wallet create' subcommand")
        void parseWalletCreate() {
            var opts = BlockchainCLI.parseArgs(
                    new String[]{"contract", "wallet", "create"}, 1);
            assertEquals("wallet", opts.subcommand);
            assertEquals("create", opts.walletAction);
        }

        @Test
        @DisplayName("Parse etherscan-key option")
        void parseEtherscanKey() {
            var opts = BlockchainCLI.parseArgs(
                    new String[]{"contract", "verify", "--etherscan-key=abc123"}, 1);
            assertEquals("abc123", opts.etherscanKey);
        }

        @Test
        @DisplayName("Default network is 'local'")
        void defaultNetwork() {
            var opts = BlockchainCLI.parseArgs(
                    new String[]{"contract", "compile"}, 1);
            assertEquals("local", opts.network);
        }

        @Test
        @DisplayName("Resolve chain by name")
        void resolveChainByName() {
            assertNotNull(BlockchainCLI.resolveChain("mainnet"));
            assertNotNull(BlockchainCLI.resolveChain("sepolia"));
            assertNotNull(BlockchainCLI.resolveChain("arbitrum"));
            assertNotNull(BlockchainCLI.resolveChain("base"));
            assertNotNull(BlockchainCLI.resolveChain("optimism"));
            assertNotNull(BlockchainCLI.resolveChain("polygon"));
            assertNotNull(BlockchainCLI.resolveChain("local"));
            assertNull(BlockchainCLI.resolveChain("unknown-chain"));
        }

        @Test
        @DisplayName("Resolve chain by chain ID")
        void resolveChainByChainId() {
            L2ChainConfig chain = BlockchainCLI.resolveChain("1");
            assertNotNull(chain);
            assertEquals("1", chain.getChainId());

            chain = BlockchainCLI.resolveChain("11155111");
            assertNotNull(chain);
            assertEquals("Sepolia Testnet", chain.getName());
        }

        @Test
        @DisplayName("Resolve chain is case-insensitive")
        void resolveChainCaseInsensitive() {
            assertNotNull(BlockchainCLI.resolveChain("Sepolia"));
            assertNotNull(BlockchainCLI.resolveChain("MAINNET"));
            assertNotNull(BlockchainCLI.resolveChain("Arbitrum"));
        }

        @Test
        @DisplayName("Verbose flag is parsed")
        void verboseFlag() {
            var opts = BlockchainCLI.parseArgs(
                    new String[]{"contract", "compile", "--verbose"}, 1);
            assertTrue(opts.verbose);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  L2ChainConfig Additional Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2ChainConfig Tests")
    class L2ChainConfigTests {

        @Test
        @DisplayName("All predefined chains are registered")
        void allChainsRegistered() {
            var chains = L2ChainConfig.allChains();
            assertTrue(chains.size() >= 9);
        }

        @Test
        @DisplayName("Mainnet is production, not test")
        void mainnetIsProduction() {
            assertTrue(L2ChainConfig.ETHEREUM_MAINNET.isProduction());
            assertFalse(L2ChainConfig.ETHEREUM_MAINNET.isTestNetwork());
        }

        @Test
        @DisplayName("Sepolia is test, not production")
        void sepoliaIsTest() {
            assertTrue(L2ChainConfig.SEPOLIA.isTestNetwork());
            assertFalse(L2ChainConfig.SEPOLIA.isProduction());
        }

        @Test
        @DisplayName("Arbitrum is L2")
        void arbitrumIsL2() {
            assertTrue(L2ChainConfig.ARBITRUM_ONE.isL2());
        }

        @Test
        @DisplayName("Local Anvil is test network")
        void localIsTest() {
            assertTrue(L2ChainConfig.LOCAL_ANVIL.isTestNetwork());
        }

        @Test
        @DisplayName("Contract URL generation")
        void contractUrl() {
            String url = L2ChainConfig.SEPOLIA.getContractUrl("0xabcdef");
            assertEquals("https://sepolia.etherscan.io/address/0xabcdef", url);
        }

        @Test
        @DisplayName("Tx URL generation")
        void txUrl() {
            String url = L2ChainConfig.ETHEREUM_MAINNET.getTxUrl("0x123abc");
            assertEquals("https://etherscan.io/tx/0x123abc", url);
        }
    }
}
