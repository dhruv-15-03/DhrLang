package dhrlang.deploy;

import dhrlang.deploy.BlockchainCLI.BlockchainOptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the v3.12.0 verified-deploy loop:
 * <ul>
 *   <li>{@link WalletManager#computeCreateAddress} against independent ethers.js
 *       {@code getCreateAddress} ground-truth vectors;</li>
 *   <li>the {@link BroadcastArtifact} Foundry {@code run-latest.json} shape;</li>
 *   <li>{@code --verify} / {@code --from} argument parsing;</li>
 *   <li>an end-to-end {@code contract deploy --dry-run} that writes a broadcast
 *       artifact with the deterministic predicted CREATE address.</li>
 * </ul>
 */
public class DeployBroadcastTest {

    private static final String JAVA =
            System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

    // ── computeCreateAddress ground-truth (ethers.js v6 getCreateAddress) ──

    @Nested
    class CreateAddressVectors {

        // sender 0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0 across the RLP nonce boundaries
        private static final String S1 = "0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0";
        // Anvil/Hardhat default account #0
        private static final String ANVIL0 = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

        @Test
        void nonceZero() {
            assertEquals("0xcd234a471b72ba2f1ccf0a70fcaba648a5eecd8d",
                    WalletManager.computeCreateAddress(S1, 0));
        }

        @Test
        void nonceOne() {
            assertEquals("0x343c43a37d37dff08ae8c4a11544c718abb4fcf8",
                    WalletManager.computeCreateAddress(S1, 1));
        }

        @Test
        void nonceTwo() {
            assertEquals("0xf778b86fa74e846c4f0a1fbd1335fe81c00a0c91",
                    WalletManager.computeCreateAddress(S1, 2));
        }

        @Test
        void nonce127_singleByteRlpBoundary() {
            assertEquals("0x06d9a77f5e4b311bae8d559db9cdb4df94104aa0",
                    WalletManager.computeCreateAddress(S1, 127));
        }

        @Test
        void nonce128_0x81PrefixBoundary() {
            assertEquals("0x08e190dcb7b73f5fcdabb43e102215c83659a76d",
                    WalletManager.computeCreateAddress(S1, 128));
        }

        @Test
        void nonce256_0x82PrefixBoundary() {
            assertEquals("0x3837c1ae70354f670550c746580199ac6a73cb0a",
                    WalletManager.computeCreateAddress(S1, 256));
        }

        @Test
        void anvilAccount0_nonceZero_isCanonicalFirstDeployAddress() {
            // The address every "first deploy on a fresh Anvil" lands on.
            assertEquals("0x5fbdb2315678afecb367f032d93f642f64180aa3",
                    WalletManager.computeCreateAddress(ANVIL0, 0));
        }

        @Test
        void anvilAccount0_nonceOne() {
            assertEquals("0xe7f1725e7734ce288f8367e1bb143e90bb3f0512",
                    WalletManager.computeCreateAddress(ANVIL0, 1));
        }

        @Test
        void senderCaseAndPrefixAreNormalized() {
            assertEquals(WalletManager.computeCreateAddress(S1, 0),
                    WalletManager.computeCreateAddress("6AC7EA33F8831EA9DCC53393AAA88B25A785DBF0", 0));
        }

        @Test
        void rejectsBadSenderAndNegativeNonce() {
            assertThrows(WalletManager.WalletException.class,
                    () -> WalletManager.computeCreateAddress("0x1234", 0));
            assertThrows(WalletManager.WalletException.class,
                    () -> WalletManager.computeCreateAddress(S1, -1));
        }
    }

    // ── BroadcastArtifact JSON shape ───────────────────────────────────────

    @Nested
    class BroadcastJson {

        @Test
        void plannedTxHasPredictedAddressNoHashNoReceipt() {
            String json = new BroadcastArtifact(11155111)
                    .setTimestamp(1_700_000_000L)
                    .addPlanned("MiniToken", "0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0",
                            0, 120_000, "0x6080604052", "0xcd234a471b72ba2f1ccf0a70fcaba648a5eecd8d")
                    .toJson();

            assertTrue(json.contains("\"transactionType\": \"CREATE\""), json);
            assertTrue(json.contains("\"contractName\": \"MiniToken\""), json);
            assertTrue(json.contains("0xcd234a471b72ba2f1ccf0a70fcaba648a5eecd8d"), json);
            // chainId rendered as hex (11155111 == 0xaa36a7)
            assertTrue(json.contains("\"chainId\": \"0xaa36a7\""), json);
            assertTrue(json.contains("\"chain\": 11155111"), json);
            assertTrue(json.contains("\"timestamp\": 1700000000"), json);
            assertTrue(json.contains("\"hash\": null"), json);
            // a planned-only artifact has an empty receipts array
            assertTrue(json.contains("\"receipts\": []"), json);
            assertTrue(json.contains("\"multi\": false"), json);
        }

        @Test
        void broadcastTxHasHashAndReceipt() {
            String json = new BroadcastArtifact(1)
                    .addBroadcast("MiniToken", "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
                            1, 200_000, "0x6080", "0xe7f1725e7734ce288f8367e1bb143e90bb3f0512",
                            "0xdeadbeef", 150_000, 42)
                    .toJson();

            assertTrue(json.contains("\"hash\": \"0xdeadbeef\""), json);
            assertTrue(json.contains("\"transactionHash\": \"0xdeadbeef\""), json); // receipt
            assertTrue(json.contains("\"blockNumber\": \"0x2a\""), json);            // 42
            assertTrue(json.contains("\"gasUsed\": \"0x249f0\""), json);             // 150000
            assertTrue(json.contains("\"status\": \"0x1\""), json);
            assertFalse(json.contains("\"receipts\": []"), "live deploy should have a receipt");
        }

        @Test
        void relativePathNestsDryRunUnderChainId() {
            BroadcastArtifact b = new BroadcastArtifact(31337);
            assertEquals("broadcast/Deploy.s.sol/31337/run-latest.json", b.relativePath(false));
            assertEquals("broadcast/Deploy.s.sol/31337/dry-run/run-latest.json", b.relativePath(true));
        }
    }

    // ── parseArgs ──────────────────────────────────────────────────────────

    @Test
    void parsesVerifyFlag() {
        BlockchainOptions opts = BlockchainCLI.parseArgs(
                new String[]{"contract", "deploy", "--verify", "token.dhr"}, 1);
        assertTrue(opts.verify);
    }

    @Test
    void verifyDefaultsFalse() {
        BlockchainOptions opts = BlockchainCLI.parseArgs(
                new String[]{"contract", "deploy", "token.dhr"}, 1);
        assertFalse(opts.verify);
    }

    @Test
    void parsesFromAddress() {
        BlockchainOptions opts = BlockchainCLI.parseArgs(
                new String[]{"contract", "deploy",
                        "--from=0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0", "token.dhr"}, 1);
        assertEquals("0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0", opts.deployerFrom);
    }

    // ── end-to-end dry-run deploy ──────────────────────────────────────────

    @Test
    void dryRunDeployWritesBroadcastArtifactWithPredictedAddress(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("MiniToken.dhr");
        Files.writeString(src,
                "@contract\n" +
                "class MiniToken {\n" +
                "    @storage num total;\n" +
                "    @constructor\n" +
                "    kaam init() { total = 100; }\n" +
                "    @view\n" +
                "    kaam getTotal() { return total; }\n" +
                "}\n");

        Path out = tmp.resolve("out");
        Result r = run("contract", "deploy", "--network=local", "--dry-run",
                "--output=" + out, src.toString());
        assertEquals(0, r.exit, "dry-run deploy should exit 0. Got:\n" + r.out);

        Path bc = out.resolve("broadcast/Deploy.s.sol/31337/dry-run/run-latest.json");
        assertTrue(Files.exists(bc),
                "Foundry-style broadcast artifact should be written. Out:\n" + r.out);

        String json = Files.readString(bc);
        assertTrue(json.contains("\"transactionType\": \"CREATE\""), json);
        assertTrue(json.contains("\"contractName\": \"MiniToken\""), json);
        // local + nonce 0 => the canonical Anvil first-deploy address, deterministically.
        assertTrue(json.contains("0x5fbdb2315678afecb367f032d93f642f64180aa3"),
                "predicted CREATE address for Anvil acct0 nonce 0. Got:\n" + json);
        // dry run => no real tx hash yet
        assertTrue(json.contains("\"hash\": null"), json);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private record Result(int exit, String out) {}

    private Result run(String... toolArgs) throws IOException, InterruptedException {
        String cp = System.getProperty("java.class.path");
        String[] full = new String[4 + toolArgs.length];
        full[0] = JAVA;
        full[1] = "-cp";
        full[2] = cp;
        full[3] = "dhrlang.Main";
        System.arraycopy(toolArgs, 0, full, 4, toolArgs.length);

        ProcessBuilder pb = new ProcessBuilder(full);
        // Keep the predicted-address assertion deterministic regardless of the host env.
        pb.environment().remove("DHRLANG_DEPLOYER");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(baos);
        }
        int exit = p.waitFor();
        return new Result(exit, baos.toString(StandardCharsets.UTF_8));
    }
}
