package dhrlang.evm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Computes Solidity-compatible 4-byte function selectors.
 *
 * <p>The selector is the first 4 bytes of the Keccak-256 hash of the
 * canonical function signature, e.g. {@code transfer(address,uint256)}.</p>
 *
 * <p>Since the JDK ships only SHA-256, we use a built-in Keccak-256
 * implementation rather than pulling in Bouncy Castle. For function selectors
 * (short inputs) this is perfectly adequate.</p>
 */
public final class FunctionSelector {

    private FunctionSelector() {}

    /**
     * Compute the 4-byte function selector for a canonical signature.
     * 
     * @param signature e.g. "transfer(address,uint256)"
     * @return 4-byte selector
     */
    public static byte[] compute(String signature) {
        byte[] hash = keccak256(signature.getBytes(StandardCharsets.UTF_8));
        return Arrays.copyOf(hash, 4);
    }

    /**
     * Compute selector and return as hex string (no 0x prefix).
     */
    public static String computeHex(String signature) {
        byte[] sel = compute(signature);
        return bytesToHex(sel);
    }

    /**
     * Build the canonical function signature from a name and parameter types.
     *
     * @param name function name
     * @param paramTypes Solidity-style parameter types (e.g. "address", "uint256")
     * @return e.g. "transfer(address,uint256)"
     */
    public static String canonicalSignature(String name, String... paramTypes) {
        StringBuilder sb = new StringBuilder(name).append('(');
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(paramTypes[i]);
        }
        sb.append(')');
        return sb.toString();
    }

    // ── Keccak-256 (Ethereum's hash function) ──────────────────────────

    /**
     * Keccak-256 implementation (NOT SHA3-256 — uses 0x01 padding, not 0x06).
     * Rate = 136 bytes (1088 bits), capacity = 64 bytes (512 bits), output = 32 bytes.
     */
    public static byte[] keccak256(byte[] input) {
        int rate = 136;
        long[] state = new long[25];

        // Absorb phase
        byte[] padded = padKeccak(input, rate);
        for (int offset = 0; offset < padded.length; offset += rate) {
            for (int i = 0; i < rate / 8; i++) {
                state[i] ^= littleEndianLong(padded, offset + i * 8);
            }
            keccakF1600(state);
        }

        // Squeeze phase (only need 32 bytes = 4 lanes)
        byte[] output = new byte[32];
        for (int i = 0; i < 4; i++) {
            long lane = state[i];
            for (int j = 0; j < 8; j++) {
                output[i * 8 + j] = (byte) (lane >>> (j * 8));
            }
        }
        return output;
    }

    private static byte[] padKeccak(byte[] input, int rate) {
        // Keccak pad10*1: append 0x01, then zeros, then XOR 0x80 on last byte
        int q = rate - (input.length % rate);
        byte[] padded = new byte[input.length + q];
        System.arraycopy(input, 0, padded, 0, input.length);
        padded[input.length] = 0x01;
        padded[padded.length - 1] |= (byte) 0x80;
        return padded;
    }

    private static long littleEndianLong(byte[] data, int offset) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result |= (long) (data[offset + i] & 0xFF) << (i * 8);
        }
        return result;
    }

    /** Keccak-f[1600] permutation — 24 rounds. */
    private static void keccakF1600(long[] state) {
        for (int round = 0; round < 24; round++) {
            // θ step
            long[] c = new long[5];
            for (int x = 0; x < 5; x++) {
                c[x] = state[x] ^ state[x + 5] ^ state[x + 10] ^ state[x + 15] ^ state[x + 20];
            }
            long[] d = new long[5];
            for (int x = 0; x < 5; x++) {
                d[x] = c[(x + 4) % 5] ^ Long.rotateLeft(c[(x + 1) % 5], 1);
            }
            for (int i = 0; i < 25; i++) {
                state[i] ^= d[i % 5];
            }

            // ρ and π steps (computed directly, no lookup table)
            long[] b = new long[25];
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    int src = x + 5 * y;
                    int dst = y + 5 * ((2 * x + 3 * y) % 5);
                    b[dst] = Long.rotateLeft(state[src], RHO[src]);
                }
            }

            // χ step
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    int idx = x + 5 * y;
                    state[idx] = b[idx] ^ (~b[((x + 1) % 5) + 5 * y] & b[((x + 2) % 5) + 5 * y]);
                }
            }

            // ι step
            state[0] ^= RC[round];
        }
    }

    // Rotation offsets (ρ) indexed by x + 5*y
    private static final int[] RHO = {
         0,  1, 62, 28, 27,
        36, 44,  6, 55, 20,
         3, 10, 43, 25, 39,
        41, 45, 15, 21,  8,
        18,  2, 61, 56, 14
    };

    // Round constants (ι)
    private static final long[] RC = {
        0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL, 0x8000000080008000L,
        0x000000000000808BL, 0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L,
        0x000000000000008AL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
        0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L, 0x8000000000008003L,
        0x8000000000008002L, 0x8000000000000080L, 0x000000000000800AL, 0x800000008000000AL,
        0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
    };

    /** Convert byte array to hex string. */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
