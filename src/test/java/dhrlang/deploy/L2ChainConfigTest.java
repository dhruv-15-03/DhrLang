package dhrlang.deploy;

import dhrlang.deploy.L2ChainConfig.ChainType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the {@link L2ChainConfig} registry — the set of supported chains,
 * their canonical chain IDs / types, the {@link BlockchainCLI#resolveChain}
 * alias resolver, and that the {@code contract networks} CLI surfaces every
 * registered chain. The ZK rollups (zkSync Era, Polygon zkEVM, Scroll, Linea)
 * are the first users of {@link ChainType#L2_ZK}.
 */
public class L2ChainConfigTest {

    @Nested
    @DisplayName("Registry membership")
    class Registry {

        @Test
        @DisplayName("registry holds the full supported-chain set")
        void registrySize() {
            assertEquals(21, L2ChainConfig.allChains().size(),
                    "expected 9 original chains + 12 added (OP/Polygon testnets, 4 ZK ecosystems, Blast)");
        }

        @Test
        @DisplayName("byChainId resolves every newly added chain to the right name")
        void newChainsByChainId() {
            assertEquals("zkSync Era", L2ChainConfig.byChainId("324").getName());
            assertEquals("zkSync Sepolia", L2ChainConfig.byChainId("300").getName());
            assertEquals("Polygon zkEVM", L2ChainConfig.byChainId("1101").getName());
            assertEquals("Polygon zkEVM Cardona", L2ChainConfig.byChainId("2442").getName());
            assertEquals("Scroll", L2ChainConfig.byChainId("534352").getName());
            assertEquals("Scroll Sepolia", L2ChainConfig.byChainId("534351").getName());
            assertEquals("Linea", L2ChainConfig.byChainId("59144").getName());
            assertEquals("Linea Sepolia", L2ChainConfig.byChainId("59141").getName());
            assertEquals("Blast", L2ChainConfig.byChainId("81457").getName());
            assertEquals("Blast Sepolia", L2ChainConfig.byChainId("168587773").getName());
            assertEquals("Optimism Sepolia", L2ChainConfig.byChainId("11155420").getName());
            assertEquals("Polygon Amoy", L2ChainConfig.byChainId("80002").getName());
        }

        @Test
        @DisplayName("byName is case-insensitive for a new chain")
        void byNameCaseInsensitive() {
            assertSame(L2ChainConfig.SCROLL, L2ChainConfig.byName("scroll"));
            assertSame(L2ChainConfig.LINEA, L2ChainConfig.byName("LINEA"));
        }

        @Test
        @DisplayName("every registered chain has a non-blank id, name and RPC template")
        void registryWellFormed() {
            for (L2ChainConfig c : L2ChainConfig.allChains()) {
                assertNotNull(c.getChainId());
                assertFalse(c.getChainId().isBlank(), "blank chainId");
                assertFalse(c.getName().isBlank(), "blank name for " + c.getChainId());
                assertNotNull(c.getRpcUrlTemplate(), "null RPC for " + c.getName());
                assertFalse(c.getRpcUrlTemplate().isBlank(), "blank RPC for " + c.getName());
            }
        }

        @Test
        @DisplayName("chain IDs are unique across the registry")
        void chainIdsUnique() {
            long distinct = L2ChainConfig.allChains().stream()
                    .map(L2ChainConfig::getChainId).distinct().count();
            assertEquals(L2ChainConfig.allChains().size(), distinct);
        }
    }

    @Nested
    @DisplayName("ZK rollup classification")
    class ZkRollups {

        @Test
        @DisplayName("the four ZK rollups carry ChainType.L2_ZK")
        void zkRollupsAreZkType() {
            assertEquals(ChainType.L2_ZK, L2ChainConfig.ZKSYNC_ERA.getChainType());
            assertEquals(ChainType.L2_ZK, L2ChainConfig.POLYGON_ZKEVM.getChainType());
            assertEquals(ChainType.L2_ZK, L2ChainConfig.SCROLL.getChainType());
            assertEquals(ChainType.L2_ZK, L2ChainConfig.LINEA.getChainType());
        }

        @Test
        @DisplayName("ZK rollups count as production L2s")
        void zkRollupsAreProductionL2() {
            for (L2ChainConfig c : new L2ChainConfig[]{
                    L2ChainConfig.ZKSYNC_ERA, L2ChainConfig.POLYGON_ZKEVM,
                    L2ChainConfig.SCROLL, L2ChainConfig.LINEA}) {
                assertTrue(c.isL2(), c.getName() + " should be an L2");
                assertTrue(c.isProduction(), c.getName() + " should be production");
                assertFalse(c.isTestNetwork(), c.getName() + " is not a test network");
            }
        }

        @Test
        @DisplayName("l2Chains() now includes the ZK rollups")
        void l2ChainsIncludesZk() {
            var l2 = L2ChainConfig.l2Chains();
            assertTrue(l2.contains(L2ChainConfig.SCROLL));
            assertTrue(l2.contains(L2ChainConfig.LINEA));
            assertTrue(l2.contains(L2ChainConfig.ZKSYNC_ERA));
            assertTrue(l2.contains(L2ChainConfig.POLYGON_ZKEVM));
            assertTrue(l2.contains(L2ChainConfig.BLAST));
        }

        @Test
        @DisplayName("new testnets are classified as test networks, not production")
        void newTestnetsClassified() {
            for (L2ChainConfig c : new L2ChainConfig[]{
                    L2ChainConfig.ZKSYNC_SEPOLIA, L2ChainConfig.SCROLL_SEPOLIA,
                    L2ChainConfig.LINEA_SEPOLIA, L2ChainConfig.BLAST_SEPOLIA,
                    L2ChainConfig.OPTIMISM_SEPOLIA, L2ChainConfig.POLYGON_AMOY,
                    L2ChainConfig.POLYGON_ZKEVM_CARDONA}) {
                assertTrue(c.isTestNetwork(), c.getName() + " should be a test network");
                assertFalse(c.isProduction(), c.getName() + " is not production");
                assertEquals(ChainType.TESTNET, c.getChainType());
            }
        }
    }

    @Nested
    @DisplayName("CLI alias resolver")
    class Resolver {

        @Test
        @DisplayName("friendly aliases resolve to the right chain")
        void friendlyAliases() {
            assertSame(L2ChainConfig.ZKSYNC_ERA, BlockchainCLI.resolveChain("zksync"));
            assertSame(L2ChainConfig.ZKSYNC_ERA, BlockchainCLI.resolveChain("era"));
            assertSame(L2ChainConfig.POLYGON_ZKEVM, BlockchainCLI.resolveChain("zkevm"));
            assertSame(L2ChainConfig.SCROLL, BlockchainCLI.resolveChain("scroll"));
            assertSame(L2ChainConfig.LINEA, BlockchainCLI.resolveChain("linea"));
            assertSame(L2ChainConfig.BLAST, BlockchainCLI.resolveChain("blast"));
            assertSame(L2ChainConfig.POLYGON_AMOY, BlockchainCLI.resolveChain("amoy"));
            assertSame(L2ChainConfig.OPTIMISM_SEPOLIA, BlockchainCLI.resolveChain("op-sepolia"));
        }

        @Test
        @DisplayName("numeric chain IDs resolve")
        void numericResolves() {
            assertSame(L2ChainConfig.SCROLL, BlockchainCLI.resolveChain("534352"));
            assertSame(L2ChainConfig.LINEA_SEPOLIA, BlockchainCLI.resolveChain("59141"));
            assertSame(L2ChainConfig.BLAST_SEPOLIA, BlockchainCLI.resolveChain("168587773"));
        }

        @Test
        @DisplayName("separators and case are normalized")
        void normalization() {
            assertSame(L2ChainConfig.POLYGON_ZKEVM_CARDONA, BlockchainCLI.resolveChain("Polygon_zkEVM_Cardona"));
            assertSame(L2ChainConfig.SCROLL_SEPOLIA, BlockchainCLI.resolveChain("Scroll Sepolia"));
        }

        @Test
        @DisplayName("unknown chain still resolves to null")
        void unknownNull() {
            assertNull(BlockchainCLI.resolveChain("dogechain"));
        }
    }

    @Nested
    @DisplayName("networks CLI surface")
    class NetworksCli {

        private static final String JAVA =
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

        private String run(String... toolArgs) throws Exception {
            String cp = System.getProperty("java.class.path");
            String[] full = new String[4 + toolArgs.length];
            full[0] = JAVA;
            full[1] = "-cp";
            full[2] = cp;
            full[3] = "dhrlang.Main";
            System.arraycopy(toolArgs, 0, full, 4, toolArgs.length);
            ProcessBuilder pb = new ProcessBuilder(full);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                in.transferTo(baos);
            }
            p.waitFor();
            return baos.toString(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("contract networks lists the new ZK rollups and Blast")
        void networksListsNewChains() throws Exception {
            String out = run("contract", "networks");
            assertTrue(out.contains("zkSync Era"), "missing zkSync Era. Got:\n" + out);
            assertTrue(out.contains("Polygon zkEVM"), "missing Polygon zkEVM");
            assertTrue(out.contains("Scroll"), "missing Scroll");
            assertTrue(out.contains("Linea"), "missing Linea");
            assertTrue(out.contains("Blast"), "missing Blast");
            assertTrue(out.contains("L2_ZK"), "missing L2_ZK type label");
            assertTrue(out.contains("chainId=324"), "missing zkSync Era chainId");
        }
    }
}
