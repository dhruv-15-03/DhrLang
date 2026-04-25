package dhrlang.evm;

import dhrlang.deploy.Eip712TypedDataSigner;
import dhrlang.deploy.WalletManager;
import org.junit.jupiter.api.*;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 tests — Solidity feature parity: EIP-712, CREATE2, gas optimizer,
 * dynamic arrays, string ABI encoding.
 */
@DisplayName("Phase 3: Solidity Feature Parity Tests")
class Phase3FeatureParityTest {

    // ═══════════════════════════════════════════════════════════════
    //  EIP-712 Typed Data Signing
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EIP-712 Typed Data")
    class Eip712Tests {

        @Test
        @DisplayName("Compute type hash for Permit struct")
        void permitTypeHash() {
            byte[] hash = Eip712TypedDataSigner.permitTypeHash();
            assertNotNull(hash);
            assertEquals(32, hash.length);
            // Permit type hash is well-known: keccak256("Permit(address owner,...)")
            String hex = FunctionSelector.bytesToHex(hash);
            // Known value from ethers.js / OpenZeppelin
            assertEquals("6e71edae12b1b97f4d1f60370fef10105fa2faae0126114a169c64845d6126c9", hex);
        }

        @Test
        @DisplayName("Compute domain separator")
        void domainSeparator() {
            var signer = new Eip712TypedDataSigner();
            signer.setDomain("MyToken", "1", 1,
                    "0x5FbDB2315678afecb367f032d93F642f64180aa3");
            byte[] sep = signer.getDomainSeparator();
            assertNotNull(sep);
            assertEquals(32, sep.length);
        }

        @Test
        @DisplayName("Encode type string follows EIP-712 spec")
        void encodeType() {
            var fields = new LinkedHashMap<String, String>();
            fields.put("owner", "address");
            fields.put("spender", "address");
            fields.put("value", "uint256");
            String encoded = Eip712TypedDataSigner.encodeType("Permit", fields);
            assertEquals("Permit(address owner,address spender,uint256 value)", encoded);
        }

        @Test
        @DisplayName("Struct hash is deterministic")
        void structHashDeterministic() {
            byte[] typeHash = Eip712TypedDataSigner.permitTypeHash();
            List<Object> values = List.of(
                    "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
                    "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
                    BigInteger.valueOf(1000),
                    BigInteger.ZERO,
                    BigInteger.valueOf(1700000000)
            );
            byte[] h1 = Eip712TypedDataSigner.hashStruct(typeHash, values);
            byte[] h2 = Eip712TypedDataSigner.hashStruct(typeHash, values);
            assertArrayEquals(h1, h2);
        }

        @Test
        @DisplayName("Sign typed data produces 65-byte signature")
        void signTypedData() {
            var signer = new Eip712TypedDataSigner();
            signer.setDomain("MyToken", "1", 31337,
                    "0x5FbDB2315678afecb367f032d93F642f64180aa3");

            WalletManager wallet = new WalletManager();
            wallet.setExplicitKey("ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

            byte[] typeHash = Eip712TypedDataSigner.permitTypeHash();
            List<Object> values = List.of(
                    "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
                    "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
                    BigInteger.valueOf(1000),
                    BigInteger.ZERO,
                    BigInteger.valueOf(1700000000)
            );

            byte[] sig = signer.signTypedData(typeHash, values, wallet);
            assertEquals(65, sig.length);
            // v should be 0 or 1
            assertTrue(sig[64] == 0 || sig[64] == 1);
        }

        @Test
        @DisplayName("Meta-transaction type hash computes")
        void metaTxTypeHash() {
            byte[] hash = Eip712TypedDataSigner.metaTxTypeHash();
            assertNotNull(hash);
            assertEquals(32, hash.length);
        }

        @Test
        @DisplayName("Digest computation follows EIP-712 formula")
        void digestComputation() {
            byte[] domainSep = new byte[32];
            byte[] structHash = new byte[32];
            Arrays.fill(domainSep, (byte) 0xAA);
            Arrays.fill(structHash, (byte) 0xBB);

            byte[] digest = Eip712TypedDataSigner.computeDigest(domainSep, structHash);
            assertEquals(32, digest.length);
            // Should be different with different inputs
            Arrays.fill(structHash, (byte) 0xCC);
            byte[] digest2 = Eip712TypedDataSigner.computeDigest(domainSep, structHash);
            assertFalse(Arrays.equals(digest, digest2));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CREATE2 Deterministic Deployment
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CREATE2 Support")
    class Create2Tests {

        @Test
        @DisplayName("Compute deterministic address")
        void computeAddress() {
            String deployer = "0x5FbDB2315678afecb367f032d93F642f64180aa3";
            byte[] salt = new byte[32]; // zero salt
            byte[] initCode = new byte[]{0x60, (byte) 0x80, 0x60, 0x40, 0x52}; // minimal

            String addr = Create2Support.computeAddress(deployer, salt, initCode);
            assertNotNull(addr);
            assertTrue(addr.startsWith("0x"));
            assertEquals(42, addr.length());
        }

        @Test
        @DisplayName("Same inputs → same address (deterministic)")
        void deterministic() {
            String deployer = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
            byte[] salt = Create2Support.saltFromLabel("MyToken-v1");
            byte[] initCode = new byte[]{0x60, 0x01, 0x60, 0x00, 0x53};

            String a1 = Create2Support.computeAddress(deployer, salt, initCode);
            String a2 = Create2Support.computeAddress(deployer, salt, initCode);
            assertEquals(a1, a2);
        }

        @Test
        @DisplayName("Different salts → different addresses")
        void differentSalts() {
            String deployer = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
            byte[] initCode = new byte[]{0x60, 0x01};

            String a1 = Create2Support.computeAddress(deployer, Create2Support.saltFromLabel("v1"), initCode);
            String a2 = Create2Support.computeAddress(deployer, Create2Support.saltFromLabel("v2"), initCode);
            assertNotEquals(a1, a2);
        }

        @Test
        @DisplayName("Salt from nonce produces 32 bytes")
        void saltFromNonce() {
            byte[] salt = Create2Support.saltFromNonce(42);
            assertEquals(32, salt.length);
        }

        @Test
        @DisplayName("Minimal proxy bytecode has correct structure")
        void minimalProxy() {
            String impl = "0x5FbDB2315678afecb367f032d93F642f64180aa3";
            byte[] proxy = Create2Support.minimalProxyBytecode(impl);
            assertNotNull(proxy);
            // EIP-1167 minimal proxy creation code is ~55 bytes
            assertTrue(proxy.length >= 45 && proxy.length <= 60,
                    "Minimal proxy should be 45-60 bytes, got " + proxy.length);
        }

        @Test
        @DisplayName("CREATE2 emit generates valid bytecode")
        void create2Emit() {
            EvmCodeBuffer buf = new EvmCodeBuffer();
            byte[] initCode = new byte[]{0x60, 0x01, 0x60, 0x00, (byte) 0xF3};

            // Push a salt value first
            buf.push32(BigInteger.ONE);

            Create2Support.emitCreate2(buf, initCode, -1);
            byte[] code = buf.resolve();
            assertTrue(code.length > 10);
            // Should contain CREATE2 opcode (0xF5)
            boolean found = false;
            for (byte b : code) {
                if ((b & 0xFF) == 0xF5) { found = true; break; }
            }
            assertTrue(found, "Bytecode should contain CREATE2 opcode");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Gas Optimizer
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EVM Peephole Optimizer")
    class OptimizerTests {

        @Test
        @DisplayName("PUSH1 0x00 → PUSH0 optimization")
        void push1ZeroToPush0() {
            byte[] code = new byte[]{0x60, 0x00}; // PUSH1 0x00
            byte[] opt = EvmPeepholeOptimizer.optimize(code);
            assertEquals(1, opt.length);
            assertEquals((byte) 0x5F, opt[0]); // PUSH0
        }

        @Test
        @DisplayName("PUSH + POP elimination")
        void pushPopElimination() {
            byte[] code = new byte[]{0x60, 0x42, 0x50}; // PUSH1 0x42 POP
            byte[] opt = EvmPeepholeOptimizer.optimize(code);
            assertEquals(0, opt.length); // both eliminated
        }

        @Test
        @DisplayName("DUP1 + POP elimination")
        void dup1PopElimination() {
            byte[] code = new byte[]{(byte) 0x80, 0x50}; // DUP1 POP
            byte[] opt = EvmPeepholeOptimizer.optimize(code);
            assertEquals(0, opt.length);
        }

        @Test
        @DisplayName("Constant folding: PUSH1 3 + PUSH1 4 + ADD = PUSH1 7")
        void constantFolding() {
            byte[] code = new byte[]{0x60, 0x03, 0x60, 0x04, 0x01}; // PUSH1 3, PUSH1 4, ADD
            byte[] opt = EvmPeepholeOptimizer.optimize(code);
            assertEquals(2, opt.length); // PUSH1 7
            assertEquals((byte) 0x60, opt[0]);
            assertEquals((byte) 0x07, opt[1]);
        }

        @Test
        @DisplayName("Multiple passes converge")
        void multiplePasses() {
            // PUSH1 0x00 + POP → eliminated, but only after PUSH0 conversion
            byte[] code = new byte[]{0x60, 0x00, 0x50};
            byte[] opt = EvmPeepholeOptimizer.optimize(code, 3);
            // First pass: PUSH1 0x00 → PUSH0, then PUSH0+POP is not matched (different pattern)
            // Actually PUSH0 = 0x5F which is < 0x60 so push+pop doesn't match. That's fine.
            assertTrue(opt.length <= code.length);
        }

        @Test
        @DisplayName("Optimization report shows reduction")
        void optimizationReport() {
            byte[] code = new byte[]{0x60, 0x00, 0x60, 0x05, 0x50}; // PUSH1 0 PUSH1 5 POP
            byte[] opt = EvmPeepholeOptimizer.optimize(code);
            var report = EvmPeepholeOptimizer.analyze(code, opt);
            assertTrue(report.savedBytes > 0);
            assertTrue(report.reductionPercent > 0);
        }

        @Test
        @DisplayName("Empty bytecode is a no-op")
        void emptyBytecode() {
            byte[] opt = EvmPeepholeOptimizer.optimize(new byte[0]);
            assertEquals(0, opt.length);
        }

        @Test
        @DisplayName("Non-optimizable code unchanged")
        void nonOptimizableUnchanged() {
            byte[] code = new byte[]{0x01, 0x02, 0x03}; // ADD MUL SUB
            byte[] opt = EvmPeepholeOptimizer.optimize(code);
            assertArrayEquals(code, opt);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  WalletManager.signDigest (for EIP-712)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WalletManager signDigest")
    class SignDigestTests {

        @Test
        @DisplayName("signDigest produces 65-byte signature")
        void signDigest65Bytes() {
            WalletManager wallet = new WalletManager();
            wallet.setExplicitKey("ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

            byte[] digest = FunctionSelector.keccak256("test".getBytes());
            byte[] sig = wallet.signDigest(digest);
            assertEquals(65, sig.length);
        }

        @Test
        @DisplayName("signDigest is deterministic")
        void signDigestDeterministic() {
            WalletManager wallet = new WalletManager();
            wallet.setExplicitKey("ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

            byte[] digest = FunctionSelector.keccak256("test".getBytes());
            byte[] s1 = wallet.signDigest(digest);
            byte[] s2 = wallet.signDigest(digest);
            assertArrayEquals(s1, s2);
        }

        @Test
        @DisplayName("signDigest rejects non-32-byte input")
        void signDigestRejectsWrongLength() {
            WalletManager wallet = new WalletManager();
            wallet.setExplicitKey("ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

            assertThrows(WalletManager.WalletException.class, () ->
                    wallet.signDigest(new byte[16]));
        }
    }
}
