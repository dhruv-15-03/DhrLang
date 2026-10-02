package dhrlang.deploy;

import dhrlang.evm.FunctionSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the offline ERC-4337 account-abstraction core ({@link AccountAbstraction})
 * and the {@code dhrlang contract account} CLI surface.
 *
 * <p>The hash vectors are independent ground truth produced by the ethers.js v6
 * reference implementation of {@code getUserOpHash} for the EntryPoint v0.6 algorithm,
 * so a passing test proves byte-for-byte agreement with the on-chain EntryPoint.</p>
 */
public class AccountAbstractionTest {

    private static String hex(byte[] b) {
        return "0x" + FunctionSelector.bytesToHex(b);
    }

    /** Vector B UserOperation (sender 0x1234..7890, nonce 1, callData 0xdeadbeef, gas fields set). */
    private static AccountAbstraction.UserOperation vectorB() {
        return new AccountAbstraction.UserOperation()
                .sender("0x1234567890123456789012345678901234567890")
                .nonce(BigInteger.ONE)
                .callData(AccountAbstraction.parseBytes("0xdeadbeef"))
                .callGasLimit(BigInteger.valueOf(100000))
                .verificationGasLimit(BigInteger.valueOf(200000))
                .preVerificationGas(BigInteger.valueOf(21000))
                .maxFeePerGas(BigInteger.valueOf(1_000_000_000L))
                .maxPriorityFeePerGas(BigInteger.valueOf(1_000_000_000L));
    }

    @Nested
    @DisplayName("userOpHash (v0.6) ground-truth vectors")
    class HashVectors {

        @Test
        @DisplayName("pack() is always 320 bytes (10 abi words)")
        void packLength() {
            assertEquals(320, AccountAbstraction.pack(new AccountAbstraction.UserOperation()).length);
            assertEquals(320, AccountAbstraction.pack(vectorB()).length);
        }

        @Test
        @DisplayName("Vector A: empty/zero UserOp")
        void vectorA() {
            AccountAbstraction.UserOperation op = new AccountAbstraction.UserOperation();
            assertEquals("0x81d2c7c775ddd63555316bdd8d024acd395ed412d1346e36e5c0e3e77fd0d6e4",
                    hex(AccountAbstraction.hash(op)), "inner hash(op)");
            assertEquals("0x0c29bee38398d09a3cf750f2176b7f36ace62aca7f1114c9bcd31193d01e0c91",
                    AccountAbstraction.userOpHashHex(op, AccountAbstraction.ENTRYPOINT_V0_6, BigInteger.ONE),
                    "userOpHash @ chainId 1");
        }

        @Test
        @DisplayName("Vector B: populated UserOp, chainId 1 and 137")
        void vectorB_() {
            AccountAbstraction.UserOperation op = vectorB();
            assertEquals("0xa40c1f3c8b6ab66dd5bed83e4ddb9755005634dcf089c8eebe1bb75871dc5f28",
                    hex(AccountAbstraction.hash(op)), "inner hash(op)");
            assertEquals("0x994c271ad39397296afa7d7fb3ced2616998fa39364eb833319f072f930af974",
                    AccountAbstraction.userOpHashHex(op, AccountAbstraction.ENTRYPOINT_V0_6, BigInteger.ONE),
                    "userOpHash @ chainId 1 (mainnet)");
            assertEquals("0xc4791f496a5b3514533481223a75d7cde7eda682f716cb876a76eac81d3d3a9c",
                    AccountAbstraction.userOpHashHex(op, AccountAbstraction.ENTRYPOINT_V0_6, BigInteger.valueOf(137)),
                    "userOpHash @ chainId 137 (polygon)");
        }

        @Test
        @DisplayName("chainId changes the hash (domain separation)")
        void chainIdSeparation() {
            AccountAbstraction.UserOperation op = vectorB();
            String h1 = AccountAbstraction.userOpHashHex(op, AccountAbstraction.ENTRYPOINT_V0_6, BigInteger.ONE);
            String h137 = AccountAbstraction.userOpHashHex(op, AccountAbstraction.ENTRYPOINT_V0_6, BigInteger.valueOf(137));
            assertNotEquals(h1, h137);
        }
    }

    @Nested
    @DisplayName("keccak256 reuse sanity")
    class KeccakSanity {

        @Test
        void emptyBytes() {
            assertEquals("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470",
                    FunctionSelector.bytesToHex(FunctionSelector.keccak256(new byte[0])));
        }

        @Test
        void deadbeef() {
            assertEquals("d4fd4e189132273036449fc9e11198c739161b4c0116a9a2dccdfa1c492006f1",
                    FunctionSelector.bytesToHex(
                            FunctionSelector.keccak256(AccountAbstraction.parseBytes("0xdeadbeef"))));
        }
    }

    @Nested
    @DisplayName("EntryPoint registry")
    class EntryPointRegistry {

        @Test
        void resolvesKnownVersions() {
            assertEquals(AccountAbstraction.ENTRYPOINT_V0_6, AccountAbstraction.entryPointFor("0.6"));
            assertEquals(AccountAbstraction.ENTRYPOINT_V0_6, AccountAbstraction.entryPointFor("06"));
            assertEquals(AccountAbstraction.ENTRYPOINT_V0_6, AccountAbstraction.entryPointFor("6"));
            assertEquals(AccountAbstraction.ENTRYPOINT_V0_6, AccountAbstraction.entryPointFor("v0.6"));
            assertEquals(AccountAbstraction.ENTRYPOINT_V0_7, AccountAbstraction.entryPointFor("0.7"));
            assertEquals(AccountAbstraction.ENTRYPOINT_V0_7, AccountAbstraction.entryPointFor("V0.7"));
        }

        @Test
        void nullDefaultsToV06() {
            assertEquals(AccountAbstraction.ENTRYPOINT_V0_6, AccountAbstraction.entryPointFor(null));
        }

        @Test
        void unknownVersionIsNull() {
            assertNull(AccountAbstraction.entryPointFor("0.8"));
            assertNull(AccountAbstraction.entryPointFor("garbage"));
        }

        @Test
        void versionLabelRoundTrips() {
            assertEquals("0.6", AccountAbstraction.versionLabel(AccountAbstraction.ENTRYPOINT_V0_6));
            assertEquals("0.7", AccountAbstraction.versionLabel(AccountAbstraction.ENTRYPOINT_V0_7));
            assertEquals("0.6", AccountAbstraction.versionLabel(
                    AccountAbstraction.ENTRYPOINT_V0_6.toLowerCase()));
            assertNull(AccountAbstraction.versionLabel("0xdeadbeef"));
        }

        @Test
        void entryPointsMapHasBothVersions() {
            var map = AccountAbstraction.entryPoints();
            assertEquals(2, map.size());
            assertEquals(AccountAbstraction.ENTRYPOINT_V0_6, map.get("0.6"));
            assertEquals(AccountAbstraction.ENTRYPOINT_V0_7, map.get("0.7"));
            assertThrows(UnsupportedOperationException.class, () -> map.put("0.8", "0x0"));
        }
    }

    @Nested
    @DisplayName("parsing & normalization helpers")
    class Parsing {

        @Test
        void parseBytesHandlesEdges() {
            assertEquals(0, AccountAbstraction.parseBytes("0x").length);
            assertEquals(0, AccountAbstraction.parseBytes("").length);
            assertEquals(0, AccountAbstraction.parseBytes(null).length);
            assertArrayEquals(new byte[]{(byte) 0xde, (byte) 0xad},
                    AccountAbstraction.parseBytes("0xdead"));
            // odd-length hex is left-padded with a leading zero nibble
            assertArrayEquals(new byte[]{0x0f}, AccountAbstraction.parseBytes("0xf"));
            assertThrows(IllegalArgumentException.class, () -> AccountAbstraction.parseBytes("0xzz"));
        }

        @Test
        void parseUintAcceptsDecimalAndHex() {
            assertEquals(BigInteger.ZERO, AccountAbstraction.parseUint("0"));
            assertEquals(BigInteger.ZERO, AccountAbstraction.parseUint(null));
            assertEquals(BigInteger.valueOf(255), AccountAbstraction.parseUint("255"));
            assertEquals(BigInteger.valueOf(255), AccountAbstraction.parseUint("0xff"));
            assertThrows(NumberFormatException.class, () -> AccountAbstraction.parseUint("12x"));
        }

        @Test
        void normalizeAddressPadsAndLowercases() {
            assertEquals("0x0000000000000000000000000000000000000000",
                    AccountAbstraction.normalizeAddress("0x0"));
            assertEquals("0x1234567890123456789012345678901234567890",
                    AccountAbstraction.normalizeAddress("0x1234567890123456789012345678901234567890"));
            assertThrows(IllegalArgumentException.class,
                    () -> AccountAbstraction.normalizeAddress("0x" + "1".repeat(41)));
        }

        @Test
        void negativeFieldsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AccountAbstraction.UserOperation().nonce(BigInteger.valueOf(-1)));
        }
    }

    @Nested
    @DisplayName("toJson (eth_sendUserOperation shape)")
    class Json {

        @Test
        void emitsHexQuantitiesAndData() {
            String json = AccountAbstraction.toJson(vectorB());
            assertTrue(json.contains("\"sender\": \"0x1234567890123456789012345678901234567890\""), json);
            assertTrue(json.contains("\"nonce\": \"0x1\""), json);
            assertTrue(json.contains("\"callData\": \"0xdeadbeef\""), json);
            assertTrue(json.contains("\"callGasLimit\": \"0x186a0\""), json); // 100000
            assertTrue(json.contains("\"initCode\": \"0x\""), json);
            assertTrue(json.contains("\"paymasterAndData\": \"0x\""), json);
            assertTrue(json.contains("\"signature\": \"0x\""), json);
        }
    }

    // ── CLI end-to-end (fresh JVM on the test classpath) ──────────────────

    @Nested
    @DisplayName("contract account CLI")
    class Cli {

        private static final String JAVA =
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

        @Test
        void entrypointPrintsV06Address() throws Exception {
            Result r = run("contract", "account", "entrypoint");
            assertEquals(0, r.exit, r.out);
            assertTrue(r.out.contains(AccountAbstraction.ENTRYPOINT_V0_6),
                    "entrypoint should print the v0.6 address. Got: " + r.out);
        }

        @Test
        void entrypointV07PrintsV07Address() throws Exception {
            Result r = run("contract", "account", "entrypoint", "--version=0.7");
            assertEquals(0, r.exit, r.out);
            assertTrue(r.out.contains(AccountAbstraction.ENTRYPOINT_V0_7),
                    "entrypoint --version=0.7 should print the v0.7 address. Got: " + r.out);
        }

        @Test
        void useropPrintsCanonicalHash() throws Exception {
            Result r = run("contract", "account", "userop",
                    "--sender=0x1234567890123456789012345678901234567890",
                    "--nonce=1", "--call-data=0xdeadbeef",
                    "--call-gas=100000", "--verification-gas=200000",
                    "--pre-verification-gas=21000",
                    "--max-fee=1000000000", "--max-priority-fee=1000000000",
                    "--network=mainnet");
            assertEquals(0, r.exit, r.out);
            assertTrue(r.out.contains("0x994c271ad39397296afa7d7fb3ced2616998fa39364eb833319f072f930af974"),
                    "userop should print the canonical userOpHash. Got: " + r.out);
        }

        @Test
        void useropRejectsV07() throws Exception {
            Result r = run("contract", "account", "userop",
                    "--sender=0x1234567890123456789012345678901234567890",
                    "--version=0.7", "--network=mainnet");
            assertNotEquals(0, r.exit, "v0.7 userop hashing is unsupported and must be rejected. Got: " + r.out);
        }

        @Test
        void useropRequiresSender() throws Exception {
            Result r = run("contract", "account", "userop", "--network=mainnet");
            assertNotEquals(0, r.exit, "missing --sender should fail. Got: " + r.out);
        }

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
}
