package dhrlang.deploy;

import dhrlang.evm.FunctionSelector;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Offline ERC-4337 account-abstraction helpers.
 *
 * <p>Builds EIP-4337 <strong>v0.6</strong> {@link UserOperation}s and computes the
 * canonical {@code userOpHash} entirely offline — no bundler or RPC node required.
 * The hash matches the on-chain {@code EntryPoint.getUserOpHash} (validated against
 * ethers.js reference vectors), so the value produced here is exactly what a smart
 * account owner signs.</p>
 *
 * <p>The v0.6 EntryPoint derives the hash in two keccak steps:
 * <pre>
 *   pack(op)   = abi.encode(
 *                  sender, nonce,
 *                  keccak256(initCode), keccak256(callData),
 *                  callGasLimit, verificationGasLimit, preVerificationGas,
 *                  maxFeePerGas, maxPriorityFeePerGas,
 *                  keccak256(paymasterAndData))           // 10 words = 320 bytes
 *   userOpHash = keccak256(abi.encode(keccak256(pack(op)), entryPoint, chainId))
 * </pre>
 * The {@code signature} field is intentionally excluded from the hash.</p>
 *
 * <p>Bundler submission ({@code eth_sendUserOperation}) is out of scope — it needs a
 * live bundler. {@link #toJson(UserOperation)} emits the request-body shape so the
 * built UserOperation can be piped to one.</p>
 *
 * <p>Keccak-256 is reused from {@link FunctionSelector#keccak256(byte[])} (the same
 * dependency-free implementation used for function selectors).</p>
 */
public final class AccountAbstraction {

    private AccountAbstraction() {}

    // ── Canonical EntryPoint addresses (identical on every chain via CREATE2) ──

    /** ERC-4337 EntryPoint v0.6 (most widely deployed). */
    public static final String ENTRYPOINT_V0_6 = "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789";

    /** ERC-4337 EntryPoint v0.7. */
    public static final String ENTRYPOINT_V0_7 = "0x0000000071727De22E5E9d8BAf0edAc6f37da032";

    private static final Map<String, String> ENTRYPOINTS = new LinkedHashMap<>();
    static {
        ENTRYPOINTS.put("0.6", ENTRYPOINT_V0_6);
        ENTRYPOINTS.put("0.7", ENTRYPOINT_V0_7);
    }

    /**
     * Resolve the EntryPoint address for a version string.
     *
     * @param version e.g. "0.6", "v0.7", "06", "7"; null defaults to v0.6
     * @return the checksummed EntryPoint address, or null if the version is unknown
     */
    public static String entryPointFor(String version) {
        if (version == null) return ENTRYPOINT_V0_6;
        String v = version.toLowerCase().trim();
        if (v.startsWith("v")) v = v.substring(1);
        return switch (v) {
            case "0.6", "06", "6" -> ENTRYPOINT_V0_6;
            case "0.7", "07", "7" -> ENTRYPOINT_V0_7;
            default -> null;
        };
    }

    /** Canonical "0.6"/"0.7" label for an EntryPoint address (null if not a known EntryPoint). */
    public static String versionLabel(String entryPoint) {
        if (ENTRYPOINT_V0_6.equalsIgnoreCase(entryPoint)) return "0.6";
        if (ENTRYPOINT_V0_7.equalsIgnoreCase(entryPoint)) return "0.7";
        return null;
    }

    /** All known EntryPoint versions → addresses, in version order. */
    public static Map<String, String> entryPoints() {
        return Collections.unmodifiableMap(ENTRYPOINTS);
    }

    // ── UserOperation (v0.6) ──────────────────────────────────────────────

    /**
     * An ERC-4337 v0.6 UserOperation. Numeric fields are non-negative {@code uint256};
     * byte fields default to empty. Fluent setters validate and return {@code this}.
     */
    public static final class UserOperation {
        public String sender = ZERO_ADDRESS;
        public BigInteger nonce = BigInteger.ZERO;
        public byte[] initCode = EMPTY;
        public byte[] callData = EMPTY;
        public BigInteger callGasLimit = BigInteger.ZERO;
        public BigInteger verificationGasLimit = BigInteger.ZERO;
        public BigInteger preVerificationGas = BigInteger.ZERO;
        public BigInteger maxFeePerGas = BigInteger.ZERO;
        public BigInteger maxPriorityFeePerGas = BigInteger.ZERO;
        public byte[] paymasterAndData = EMPTY;
        public byte[] signature = EMPTY;

        public UserOperation sender(String s) { this.sender = normalizeAddress(s); return this; }
        public UserOperation nonce(BigInteger n) { this.nonce = requireUnsigned(n, "nonce"); return this; }
        public UserOperation initCode(byte[] b) { this.initCode = b == null ? EMPTY : b; return this; }
        public UserOperation callData(byte[] b) { this.callData = b == null ? EMPTY : b; return this; }
        public UserOperation callGasLimit(BigInteger v) { this.callGasLimit = requireUnsigned(v, "callGasLimit"); return this; }
        public UserOperation verificationGasLimit(BigInteger v) { this.verificationGasLimit = requireUnsigned(v, "verificationGasLimit"); return this; }
        public UserOperation preVerificationGas(BigInteger v) { this.preVerificationGas = requireUnsigned(v, "preVerificationGas"); return this; }
        public UserOperation maxFeePerGas(BigInteger v) { this.maxFeePerGas = requireUnsigned(v, "maxFeePerGas"); return this; }
        public UserOperation maxPriorityFeePerGas(BigInteger v) { this.maxPriorityFeePerGas = requireUnsigned(v, "maxPriorityFeePerGas"); return this; }
        public UserOperation paymasterAndData(byte[] b) { this.paymasterAndData = b == null ? EMPTY : b; return this; }
        public UserOperation signature(byte[] b) { this.signature = b == null ? EMPTY : b; return this; }
    }

    // ── userOpHash (v0.6) ─────────────────────────────────────────────────

    /** {@code abi.encode} of the 10 packed UserOperation fields (always 320 bytes). */
    public static byte[] pack(UserOperation op) {
        byte[] out = new byte[320];
        int o = 0;
        o = put(out, o, addressWord(op.sender));
        o = put(out, o, uintWord(op.nonce));
        o = put(out, o, FunctionSelector.keccak256(op.initCode));
        o = put(out, o, FunctionSelector.keccak256(op.callData));
        o = put(out, o, uintWord(op.callGasLimit));
        o = put(out, o, uintWord(op.verificationGasLimit));
        o = put(out, o, uintWord(op.preVerificationGas));
        o = put(out, o, uintWord(op.maxFeePerGas));
        o = put(out, o, uintWord(op.maxPriorityFeePerGas));
        put(out, o, FunctionSelector.keccak256(op.paymasterAndData));
        return out;
    }

    /** {@code keccak256(pack(op))} — the inner UserOperation hash. */
    public static byte[] hash(UserOperation op) {
        return FunctionSelector.keccak256(pack(op));
    }

    /**
     * Canonical {@code userOpHash = keccak256(abi.encode(hash(op), entryPoint, chainId))}.
     *
     * @param op         the v0.6 UserOperation (signature is ignored)
     * @param entryPoint the EntryPoint address the bundler will call (v0.6)
     * @param chainId    the target chain id
     * @return the 32-byte userOpHash
     */
    public static byte[] userOpHash(UserOperation op, String entryPoint, BigInteger chainId) {
        byte[] outer = new byte[96];
        int o = 0;
        o = put(outer, o, hash(op));
        o = put(outer, o, addressWord(entryPoint));
        put(outer, o, uintWord(requireUnsigned(chainId, "chainId")));
        return FunctionSelector.keccak256(outer);
    }

    /** {@link #userOpHash} as a 0x-prefixed hex string. */
    public static String userOpHashHex(UserOperation op, String entryPoint, BigInteger chainId) {
        return "0x" + FunctionSelector.bytesToHex(userOpHash(op, entryPoint, chainId));
    }

    // ── JSON (eth_sendUserOperation parameter shape) ──────────────────────

    /** Serialize a UserOperation to the JSON-RPC {@code eth_sendUserOperation} shape. */
    public static String toJson(UserOperation op) {
        return "{\n"
                + "  \"sender\": \"" + op.sender + "\",\n"
                + "  \"nonce\": \"" + hexQuantity(op.nonce) + "\",\n"
                + "  \"initCode\": \"" + hexData(op.initCode) + "\",\n"
                + "  \"callData\": \"" + hexData(op.callData) + "\",\n"
                + "  \"callGasLimit\": \"" + hexQuantity(op.callGasLimit) + "\",\n"
                + "  \"verificationGasLimit\": \"" + hexQuantity(op.verificationGasLimit) + "\",\n"
                + "  \"preVerificationGas\": \"" + hexQuantity(op.preVerificationGas) + "\",\n"
                + "  \"maxFeePerGas\": \"" + hexQuantity(op.maxFeePerGas) + "\",\n"
                + "  \"maxPriorityFeePerGas\": \"" + hexQuantity(op.maxPriorityFeePerGas) + "\",\n"
                + "  \"paymasterAndData\": \"" + hexData(op.paymasterAndData) + "\",\n"
                + "  \"signature\": \"" + hexData(op.signature) + "\"\n"
                + "}";
    }

    // ── ABI / hex helpers ─────────────────────────────────────────────────

    static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";
    private static final byte[] EMPTY = new byte[0];

    private static int put(byte[] dest, int off, byte[] word) {
        System.arraycopy(word, 0, dest, off, 32);
        return off + 32;
    }

    /** Right-align a {@code uint256} into a 32-byte big-endian word. */
    static byte[] uintWord(BigInteger v) {
        requireUnsigned(v, "uint256");
        byte[] word = new byte[32];
        byte[] be = v.toByteArray(); // big-endian, may carry a leading 0x00 sign byte
        int copy = Math.min(be.length, 32);
        System.arraycopy(be, be.length - copy, word, 32 - copy, copy);
        return word;
    }

    /** Encode a 20-byte address right-aligned in a 32-byte word. */
    static byte[] addressWord(String address) {
        byte[] addr = addressBytes(address);
        byte[] word = new byte[32];
        System.arraycopy(addr, 0, word, 12, 20);
        return word;
    }

    /** Parse an address into exactly 20 bytes (left-padded with zeros). */
    static byte[] addressBytes(String address) {
        String h = strip0x(address).toLowerCase();
        if (h.isEmpty()) h = "0";
        if (!h.matches("[0-9a-f]+")) throw new IllegalArgumentException("Invalid address: " + address);
        if (h.length() > 40) throw new IllegalArgumentException("Address too long: " + address);
        h = "0".repeat(40 - h.length()) + h;
        return hexToBytes(h);
    }

    /** Normalize an address to canonical lowercase {@code 0x} + 40 hex form. */
    static String normalizeAddress(String address) {
        return "0x" + FunctionSelector.bytesToHex(addressBytes(address));
    }

    /** Parse a hex byte-string ({@code "0x.."} or bare) into bytes; {@code "0x"}/empty → empty. */
    static byte[] parseBytes(String hex) {
        String h = strip0x(hex);
        if (h.isEmpty()) return EMPTY;
        if ((h.length() & 1) == 1) h = "0" + h;
        if (!h.matches("(?i)[0-9a-f]+")) throw new IllegalArgumentException("Invalid hex: " + hex);
        return hexToBytes(h.toLowerCase());
    }

    /** Parse a {@code uint256} from decimal or {@code 0x}-hex into a non-negative BigInteger. */
    static BigInteger parseUint(String s) {
        if (s == null) return BigInteger.ZERO;
        String t = s.trim();
        if (t.isEmpty()) return BigInteger.ZERO;
        BigInteger v = (t.startsWith("0x") || t.startsWith("0X"))
                ? new BigInteger(t.substring(2), 16)
                : new BigInteger(t, 10);
        return requireUnsigned(v, "value");
    }

    private static String hexQuantity(BigInteger v) {
        return "0x" + v.toString(16);
    }

    private static String hexData(byte[] b) {
        return "0x" + FunctionSelector.bytesToHex(b);
    }

    private static String strip0x(String s) {
        if (s == null) return "";
        String t = s.trim();
        return (t.startsWith("0x") || t.startsWith("0X")) ? t.substring(2) : t;
    }

    private static byte[] hexToBytes(String h) {
        int n = h.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static BigInteger requireUnsigned(BigInteger v, String field) {
        if (v == null) throw new IllegalArgumentException(field + " is null");
        if (v.signum() < 0) throw new IllegalArgumentException(field + " must be non-negative");
        if (v.bitLength() > 256) throw new IllegalArgumentException(field + " exceeds uint256");
        return v;
    }
}
