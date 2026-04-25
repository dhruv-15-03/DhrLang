package dhrlang.deploy;

import dhrlang.stdlib.ContractStdlib;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that deploy real contracts to a local Anvil/Hardhat node.
 *
 * <p>These tests are <b>disabled by default</b> — they only run when the
 * environment variable {@code DHRLANG_ANVIL_TEST=true} is set and a local
 * Ethereum node is running on port 8545.</p>
 *
 * <h3>How to run:</h3>
 * <pre>
 * # Terminal 1: Start Anvil
 * anvil
 *
 * # Terminal 2: Run integration tests
 * set DHRLANG_ANVIL_TEST=true
 * ./gradlew test --tests "dhrlang.deploy.AnvilIntegrationTest"
 * </pre>
 */
@DisplayName("Anvil Live Deployment Tests")
@EnabledIfEnvironmentVariable(named = "DHRLANG_ANVIL_TEST", matches = "true")
class AnvilIntegrationTest {

    // Anvil default test account #0
    static final String DEPLOYER_KEY = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    static final String DEPLOYER_ADDR = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
    static final String ANVIL_RPC = "http://127.0.0.1:8545";

    private EthJsonRpcClient rpc;
    private WalletManager wallet;

    @BeforeEach
    void setup() {
        rpc = new EthJsonRpcClient(ANVIL_RPC);
        wallet = new WalletManager();
        wallet.setExplicitKey(DEPLOYER_KEY);
    }

    @AfterEach
    void cleanup() {
        wallet.clear();
    }

    @Test
    @DisplayName("Connect to Anvil and verify chain ID = 31337")
    void connectAndVerifyChainId() {
        long chainId = rpc.getChainId();
        assertEquals(31337, chainId);
    }

    @Test
    @DisplayName("Fetch deployer balance (should have 10000 ETH on Anvil)")
    void fetchDeployerBalance() {
        var balance = rpc.getBalance(DEPLOYER_ADDR);
        assertTrue(balance.signum() > 0, "Deployer should have non-zero balance");
    }

    @Test
    @DisplayName("Fetch transaction count (nonce)")
    void fetchNonce() {
        long nonce = rpc.getTransactionCount(DEPLOYER_ADDR);
        assertTrue(nonce >= 0);
    }

    @Test
    @DisplayName("Fetch current gas price")
    void fetchGasPrice() {
        long gasPrice = rpc.getGasPrice();
        assertTrue(gasPrice > 0, "Gas price should be positive");
    }

    @Test
    @DisplayName("Deploy a minimal contract and verify code exists")
    void deployMinimalContract() {
        // Minimal EVM bytecode: PUSH1 0x00 PUSH1 0x00 RETURN
        // This deploys an empty contract (no runtime code)
        // Better: deploy a contract that returns some code
        // PUSH1 0x01 PUSH1 0x00 MSTORE8 PUSH1 0x01 PUSH1 0x00 RETURN
        // This stores 0x01 at memory[0] and returns it as runtime code

        // Creation bytecode that deploys "0xFE" (INVALID opcode) as runtime code
        // 60 01 60 00 53 60 01 60 00 f3
        // PUSH1 01  PUSH1 00  MSTORE8  PUSH1 01  PUSH1 00  RETURN
        String creationHex = "600160005360016000f3";

        long nonce = rpc.getTransactionCount(DEPLOYER_ADDR);

        DeploymentManager.DeploymentTx tx = new DeploymentManager.DeploymentTx(
                "MinimalContract", "31337", "Local",
                creationHex, 50000, 100000,
                2000000000L, 1000000000L,
                DEPLOYER_ADDR, nonce
        );

        String signedTx = wallet.signDeployTx(tx);
        assertNotNull(signedTx);
        assertTrue(signedTx.startsWith("0x02"));

        // Broadcast
        String txHash = rpc.sendRawTransaction(signedTx);
        assertNotNull(txHash);
        assertTrue(txHash.startsWith("0x"));

        // Wait for receipt
        EthJsonRpcClient.TxReceipt receipt = rpc.waitForReceipt(txHash);
        assertNotNull(receipt, "Receipt should not be null — tx should be mined");
        assertTrue(receipt.isSuccess(), "Transaction should succeed. Status: " + receipt.status);
        assertNotNull(receipt.contractAddress, "Contract address should be set");
        assertTrue(receipt.gasUsed > 0, "Gas used should be > 0");

        // Verify code at deployed address
        String code = rpc.getCode(receipt.contractAddress);
        assertNotNull(code);
        assertNotEquals("0x", code, "Deployed address should have code");

        System.out.println("Successfully deployed contract!");
        System.out.println("  Tx hash:  " + txHash);
        System.out.println("  Address:  " + receipt.contractAddress);
        System.out.println("  Gas used: " + receipt.gasUsed);
        System.out.println("  Block:    " + receipt.blockNumber);
    }

    @Test
    @DisplayName("Standard library catalog is available")
    void stdlibCatalog() {
        var catalog = ContractStdlib.catalog();
        assertTrue(catalog.size() >= 8);
        assertNotNull(catalog.get("ERC20"));
        assertNotNull(catalog.get("Ownable"));
    }
}
