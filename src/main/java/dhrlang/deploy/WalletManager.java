package dhrlang.deploy;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.HexFormat;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.spec.KeySpec;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;

import dhrlang.evm.FunctionSelector;

/**
 * Manages wallet private keys for contract deployment signing.
 *
 * <p>Supports three key sources:
 * <ol>
 *   <li>Environment variable ({@code DHRLANG_PRIVATE_KEY})</li>
 *   <li>Encrypted keystore file (AES-256-GCM)</li>
 *   <li>Interactive prompt (stdin)</li>
 * </ol>
 *
 * <p>Private keys are never logged or written to disk in plaintext.
 * The keystore uses PBKDF2-HMAC-SHA256 key derivation with 310,000 iterations
 * (OWASP 2023 recommendation).</p>
 *
 * <p><b>User story:</b> As a developer, I want to sign deployment transactions
 * without exposing my private key in CLI arguments.</p>
 */
public final class WalletManager {

    // ── Constants ────────────────────────────────────────────────────────

    private static final String ENV_PRIVATE_KEY = "DHRLANG_PRIVATE_KEY";
    private static final String ENV_KEYSTORE_PASSWORD = "DHRLANG_KEYSTORE_PASSWORD";
    private static final String DEFAULT_KEYSTORE_DIR = ".dhrlang";
    private static final String KEYSTORE_FILENAME = "keystore.enc";
    private static final int PBKDF2_ITERATIONS = 310_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int GCM_NONCE_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int SALT_LENGTH = 32;

    // ── State ────────────────────────────────────────────────────────────

    private byte[] privateKeyBytes;
    private String address;
    private KeySource keySource;

    /**
     * How the private key was loaded.
     */
    public enum KeySource {
        ENVIRONMENT_VARIABLE,
        KEYSTORE_FILE,
        INTERACTIVE_PROMPT,
        EXPLICIT
    }

    // ── Public API: Loading Keys ─────────────────────────────────────────

    /**
     * Load a private key from the environment variable {@code DHRLANG_PRIVATE_KEY}.
     *
     * @return this manager (for chaining)
     * @throws WalletException if the env var is not set or the key is invalid
     */
    public WalletManager loadFromEnv() {
        String hexKey = System.getenv(ENV_PRIVATE_KEY);
        if (hexKey == null || hexKey.isBlank()) {
            throw new WalletException("Environment variable " + ENV_PRIVATE_KEY + " is not set.\n"
                    + "Set it with: export " + ENV_PRIVATE_KEY + "=0xYourPrivateKeyHex");
        }
        setPrivateKey(hexKey, KeySource.ENVIRONMENT_VARIABLE);
        return this;
    }

    /**
     * Load a private key from an encrypted keystore file.
     *
     * @param keystorePath path to the keystore file (or null for default)
     * @param password     decryption password
     * @return this manager
     * @throws WalletException if decryption fails or keystore not found
     */
    public WalletManager loadFromKeystore(Path keystorePath, String password) {
        if (keystorePath == null) {
            keystorePath = Path.of(System.getProperty("user.home"), DEFAULT_KEYSTORE_DIR, KEYSTORE_FILENAME);
        }
        if (!Files.exists(keystorePath)) {
            throw new WalletException("Keystore not found: " + keystorePath
                    + "\nCreate one with: dhrlang wallet create");
        }
        try {
            byte[] encrypted = Files.readAllBytes(keystorePath);
            byte[] decrypted = decryptKeystore(encrypted, password);
            setPrivateKey(new String(decrypted, StandardCharsets.UTF_8), KeySource.KEYSTORE_FILE);
            // Clear decrypted bytes
            java.util.Arrays.fill(decrypted, (byte) 0);
        } catch (IOException e) {
            throw new WalletException("Failed to read keystore: " + e.getMessage());
        }
        return this;
    }

    /**
     * Load a private key from interactive stdin prompt.
     *
     * @return this manager
     * @throws WalletException if reading from stdin fails
     */
    public WalletManager loadFromPrompt() {
        Console console = System.console();
        if (console != null) {
            char[] key = console.readPassword("Enter private key (hex): ");
            if (key == null || key.length == 0) {
                throw new WalletException("No private key entered.");
            }
            setPrivateKey(new String(key), KeySource.INTERACTIVE_PROMPT);
            java.util.Arrays.fill(key, '\0');
        } else {
            // Fall back to stdin if no console (e.g., piped input)
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                System.err.print("Enter private key (hex): ");
                String line = reader.readLine();
                if (line == null || line.isBlank()) {
                    throw new WalletException("No private key entered.");
                }
                setPrivateKey(line.trim(), KeySource.INTERACTIVE_PROMPT);
            } catch (IOException e) {
                throw new WalletException("Failed to read from stdin: " + e.getMessage());
            }
        }
        return this;
    }

    /**
     * Set the private key explicitly (e.g., from a config file).
     *
     * @param hexKey hex-encoded private key (with or without 0x prefix)
     * @return this manager
     */
    public WalletManager setExplicitKey(String hexKey) {
        setPrivateKey(hexKey, KeySource.EXPLICIT);
        return this;
    }

    /**
     * Auto-detect the best available key source.
     * Priority: env var → keystore (with env password) → interactive prompt.
     *
     * @return this manager
     */
    public WalletManager autoLoad() {
        // 1. Try environment variable
        String envKey = System.getenv(ENV_PRIVATE_KEY);
        if (envKey != null && !envKey.isBlank()) {
            return loadFromEnv();
        }

        // 2. Try keystore with env password
        Path defaultKeystore = Path.of(System.getProperty("user.home"), DEFAULT_KEYSTORE_DIR, KEYSTORE_FILENAME);
        String envPass = System.getenv(ENV_KEYSTORE_PASSWORD);
        if (Files.exists(defaultKeystore) && envPass != null && !envPass.isBlank()) {
            return loadFromKeystore(defaultKeystore, envPass);
        }

        // 3. Interactive prompt
        return loadFromPrompt();
    }

    // ── Public API: Keystore Management ──────────────────────────────────

    /**
     * Create an encrypted keystore file from a private key.
     *
     * @param keystorePath output path (or null for default)
     * @param hexKey       the private key in hex
     * @param password     encryption password
     * @throws WalletException if encryption or file write fails
     */
    public static void createKeystore(Path keystorePath, String hexKey, String password) {
        validateHexKey(hexKey);
        if (keystorePath == null) {
            keystorePath = Path.of(System.getProperty("user.home"), DEFAULT_KEYSTORE_DIR, KEYSTORE_FILENAME);
        }
        try {
            Files.createDirectories(keystorePath.getParent());
            byte[] keyBytes = normalizeHex(hexKey).getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = encryptKeystore(keyBytes, password);
            Files.write(keystorePath, encrypted);
            java.util.Arrays.fill(keyBytes, (byte) 0);
        } catch (IOException e) {
            throw new WalletException("Failed to write keystore: " + e.getMessage());
        }
    }

    // ── Public API: Accessors ────────────────────────────────────────────

    /**
     * Get the loaded private key as a hex string (without 0x prefix).
     */
    public String getPrivateKeyHex() {
        if (privateKeyBytes == null) {
            throw new WalletException("No private key loaded. Call loadFromEnv(), loadFromKeystore(), or loadFromPrompt() first.");
        }
        return HexFormat.of().formatHex(privateKeyBytes);
    }

    /**
     * Get the derived Ethereum address (checksummed).
     */
    public String getAddress() {
        if (address == null) {
            throw new WalletException("No private key loaded.");
        }
        return address;
    }

    /**
     * Get how the key was loaded.
     */
    public KeySource getKeySource() {
        return keySource;
    }

    /**
     * Check if a private key is currently loaded.
     */
    public boolean isLoaded() {
        return privateKeyBytes != null;
    }

    /**
     * Clear the private key from memory.
     */
    public void clear() {
        if (privateKeyBytes != null) {
            java.util.Arrays.fill(privateKeyBytes, (byte) 0);
            privateKeyBytes = null;
        }
        address = null;
        keySource = null;
    }

    // ── Transaction Signing (Stub — EIP-1559) ────────────────────────────

    /**
     * Sign a deployment transaction (EIP-1559 type-2).
     *
     * <p>Returns the signed transaction as a hex-encoded byte array suitable for
     * {@code eth_sendRawTransaction}. Implements serialization of:
     * <ul>
     *   <li>Chain ID, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit</li>
     *   <li>Empty {@code to} address (contract creation)</li>
     *   <li>Value (0 for non-payable)</li>
     *   <li>Data (creation bytecode)</li>
     * </ul>
     *
     * @param tx the unsigned deployment transaction
     * @return hex-encoded signed transaction (prefixed with "0x")
     */
    public String signDeployTx(DeploymentManager.DeploymentTx tx) {
        if (privateKeyBytes == null) {
            throw new WalletException("No private key loaded.");
        }

        // Build unsigned EIP-1559 fields
        byte[] chainIdBytes = bigIntBytes(new BigInteger(tx.getChainId()));
        byte[] nonceBytes = bigIntBytes(BigInteger.valueOf(tx.getNonce()));
        byte[] maxPriorityBytes = bigIntBytes(BigInteger.valueOf(tx.getMaxPriorityFeePerGas()));
        byte[] maxFeeBytes = bigIntBytes(BigInteger.valueOf(tx.getMaxFeePerGas()));
        byte[] gasLimitBytes = bigIntBytes(BigInteger.valueOf(tx.getGasLimit()));
        byte[] toBytes = new byte[0]; // empty = contract creation
        byte[] valueBytes = new byte[]{0}; // 0 ETH
        byte[] dataBytes = HexFormat.of().parseHex(tx.getCreationBytecodeHex());
        byte[] accessList = new byte[]{(byte) 0xc0}; // empty RLP list

        // RLP-encode the unsigned fields (simplified RLP)
        byte[] unsignedPayload = rlpEncodeList(
                chainIdBytes, nonceBytes, maxPriorityBytes, maxFeeBytes,
                gasLimitBytes, toBytes, valueBytes, dataBytes, accessList
        );

        // EIP-1559 prefix byte
        byte[] toHash = new byte[1 + unsignedPayload.length];
        toHash[0] = 0x02; // type 2 transaction
        System.arraycopy(unsignedPayload, 0, toHash, 1, unsignedPayload.length);

        // Hash with Keccak-256 (Ethereum's hash function)
        byte[] txHash = FunctionSelector.keccak256(toHash);

        // ECDSA sign with secp256k1 (RFC 6979 deterministic k)
        byte[] signature = ecdsaSign(txHash, privateKeyBytes);

        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(signature, 0, r, 0, 32);
        System.arraycopy(signature, 32, s, 0, 32);
        byte v = signature[64];

        byte[] signedPayload = rlpEncodeList(
                chainIdBytes, nonceBytes, maxPriorityBytes, maxFeeBytes,
                gasLimitBytes, toBytes, valueBytes, dataBytes, accessList,
                new byte[]{v}, r, s
        );

        byte[] signedTx = new byte[1 + signedPayload.length];
        signedTx[0] = 0x02;
        System.arraycopy(signedPayload, 0, signedTx, 1, signedPayload.length);

        return "0x" + HexFormat.of().formatHex(signedTx);
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    private void setPrivateKey(String hexKey, KeySource source) {
        String normalized = normalizeHex(hexKey);
        validateHexKey(normalized);
        this.privateKeyBytes = HexFormat.of().parseHex(normalized);
        this.keySource = source;
        this.address = deriveAddress(privateKeyBytes);
    }

    private static String normalizeHex(String hex) {
        String trimmed = hex.trim();
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
            trimmed = trimmed.substring(2);
        }
        return trimmed.toLowerCase();
    }

    private static void validateHexKey(String hexKey) {
        String clean = hexKey.startsWith("0x") ? hexKey.substring(2) : hexKey;
        if (clean.length() != 64) {
            throw new WalletException("Private key must be 32 bytes (64 hex chars), got " + clean.length() + " chars.");
        }
        if (!clean.matches("[0-9a-fA-F]+")) {
            throw new WalletException("Private key contains invalid characters. Expected hex (0-9, a-f).");
        }
        // Ensure key is in valid secp256k1 range (non-zero, less than curve order)
        BigInteger key = new BigInteger(clean, 16);
        BigInteger curveOrder = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
        if (key.signum() <= 0 || key.compareTo(curveOrder) >= 0) {
            throw new WalletException("Private key is outside the valid secp256k1 range.");
        }
    }

    // ── secp256k1 curve parameters (cached) ─────────────────────────────

    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters CURVE = new ECDomainParameters(
            CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH());
    private static final BigInteger HALF_CURVE_ORDER = CURVE_PARAMS.getN().shiftRight(1);

    /**
     * Derive an Ethereum address from a private key using real secp256k1.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>EC point multiply: {@code publicKey = privateKey * G} (on secp256k1)</li>
     *   <li>Encode as uncompressed point (65 bytes: 0x04 ∥ x ∥ y)</li>
     *   <li>Keccak-256 hash the 64-byte public key (drop the 0x04 prefix)</li>
     *   <li>Take the last 20 bytes as the address</li>
     * </ol>
     */
    private static String deriveAddress(byte[] privateKey) {
        BigInteger privKeyInt = new BigInteger(1, privateKey);
        ECPoint pubKeyPoint = new FixedPointCombMultiplier().multiply(CURVE_PARAMS.getG(), privKeyInt);
        // Normalize and get uncompressed encoding (04 ∥ x[32] ∥ y[32] = 65 bytes)
        byte[] uncompressed = pubKeyPoint.getEncoded(false);
        // Hash the 64-byte public key (skip the 0x04 prefix byte)
        byte[] pubKeyForHash = new byte[64];
        System.arraycopy(uncompressed, 1, pubKeyForHash, 0, 64);
        byte[] hash = FunctionSelector.keccak256(pubKeyForHash);
        // Address = last 20 bytes of Keccak-256 hash
        byte[] addressBytes = new byte[20];
        System.arraycopy(hash, 12, addressBytes, 0, 20);
        return "0x" + HexFormat.of().formatHex(addressBytes);
    }

    /**
     * Compute the contract address a CREATE deployment will produce.
     *
     * <p>For a legacy {@code CREATE} opcode (the path used by plain contract-creation
     * transactions), the new contract's address is deterministic and depends only on
     * the deployer (sender) and its transaction nonce - <b>not</b> on the bytecode:</p>
     *
     * <pre>address = keccak256(rlp([sender, nonce]))[12:]</pre>
     *
     * <p>This lets DhrLang predict the deployed address offline (e.g. for dry-run
     * broadcast artifacts) before a single byte hits the wire. Matches
     * {@code ethers.getCreateAddress} / Foundry's predicted address.</p>
     *
     * @param sender the deployer's 20-byte address (with or without {@code 0x})
     * @param nonce  the deployer's transaction nonce for this deployment ({@code >= 0})
     * @return the predicted contract address (lowercase, {@code 0x}-prefixed)
     */
    public static String computeCreateAddress(String sender, long nonce) {
        if (sender == null) {
            throw new WalletException("Sender address must not be null.");
        }
        if (nonce < 0) {
            throw new WalletException("Nonce must be non-negative, got " + nonce + ".");
        }
        String hex = normalizeHex(sender);
        if (hex.length() != 40 || !hex.matches("[0-9a-f]+")) {
            throw new WalletException("Sender must be a 20-byte hex address, got '" + sender + "'.");
        }
        byte[] senderBytes = HexFormat.of().parseHex(hex);
        byte[] nonceBytes = bigIntBytes(BigInteger.valueOf(nonce));
        byte[] rlp = rlpEncodeList(senderBytes, nonceBytes);
        byte[] hash = FunctionSelector.keccak256(rlp);
        byte[] addressBytes = new byte[20];
        System.arraycopy(hash, 12, addressBytes, 0, 20);
        return "0x" + HexFormat.of().formatHex(addressBytes);
    }

    /**
     * Get the uncompressed public key for the loaded private key.
     * @return 65-byte uncompressed public key (0x04 ∥ x ∥ y)
     */
    public byte[] getPublicKey() {
        if (privateKeyBytes == null) {
            throw new WalletException("No private key loaded.");
        }
        BigInteger privKeyInt = new BigInteger(1, privateKeyBytes);
        ECPoint pubKeyPoint = new FixedPointCombMultiplier().multiply(CURVE_PARAMS.getG(), privKeyInt);
        return pubKeyPoint.getEncoded(false);
    }

    /**
     * Sign an arbitrary 32-byte digest using the loaded private key.
     * Returns a 65-byte signature: r[32] + s[32] + v[1].
     *
     * <p>Used by EIP-712 typed data signing and other non-transaction signatures.</p>
     */
    public byte[] signDigest(byte[] digest) {
        if (privateKeyBytes == null) {
            throw new WalletException("No private key loaded.");
        }
        if (digest.length != 32) {
            throw new WalletException("Digest must be exactly 32 bytes.");
        }
        return ecdsaSign(digest, privateKeyBytes);
    }

    /**
     * ECDSA sign a 32-byte hash using secp256k1 with RFC 6979 deterministic k.
     * Returns a 65-byte signature: r[32] + s[32] + v[1] (recovery id).
     */
    static byte[] ecdsaSign(byte[] hash, byte[] privateKey) {
        BigInteger privKeyInt = new BigInteger(1, privateKey);
        ECPrivateKeyParameters privParams = new ECPrivateKeyParameters(privKeyInt, CURVE);

        // RFC 6979 deterministic k-value (prevents nonce reuse attacks)
        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        signer.init(true, privParams);
        BigInteger[] sigComponents = signer.generateSignature(hash);
        BigInteger r = sigComponents[0];
        BigInteger s = sigComponents[1];

        // Enforce low-S (EIP-2) to prevent malleability
        if (s.compareTo(HALF_CURVE_ORDER) > 0) {
            s = CURVE_PARAMS.getN().subtract(s);
        }

        // Compute recovery id (v = 0 or 1)
        // Try both recovery IDs and see which one recovers to our public key
        byte v = computeRecoveryId(hash, r, s, privateKey);

        byte[] rBytes = bigIntTo32Bytes(r);
        byte[] sBytes = bigIntTo32Bytes(s);

        byte[] sig = new byte[65];
        System.arraycopy(rBytes, 0, sig, 0, 32);
        System.arraycopy(sBytes, 0, sig, 32, 32);
        sig[64] = v;
        return sig;
    }

    /**
     * Compute the recovery ID (0 or 1) for an ECDSA signature.
     */
    private static byte computeRecoveryId(byte[] hash, BigInteger r, BigInteger s, byte[] privateKey) {
        BigInteger privKeyInt = new BigInteger(1, privateKey);
        ECPoint expectedPubKey = new FixedPointCombMultiplier().multiply(CURVE_PARAMS.getG(), privKeyInt);
        byte[] expectedEncoded = expectedPubKey.getEncoded(false);

        for (int recId = 0; recId < 2; recId++) {
            ECPoint recovered = recoverPublicKey(hash, r, s, recId);
            if (recovered != null) {
                byte[] recoveredEncoded = recovered.getEncoded(false);
                if (java.util.Arrays.equals(expectedEncoded, recoveredEncoded)) {
                    return (byte) recId;
                }
            }
        }
        return 0; // fallback
    }

    /**
     * Recover the public key from an ECDSA signature (ECRECOVER).
     */
    private static ECPoint recoverPublicKey(byte[] hash, BigInteger r, BigInteger s, int recId) {
        BigInteger n = CURVE_PARAMS.getN();
        BigInteger e = new BigInteger(1, hash);

        // Calculate the point R
        BigInteger x = r.add(BigInteger.valueOf(recId / 2).multiply(n));
        if (x.compareTo(CURVE_PARAMS.getCurve().getField().getCharacteristic()) >= 0) {
            return null;
        }

        // Decompress the point
        ECPoint rPoint;
        try {
            byte[] compressedPoint = new byte[33];
            compressedPoint[0] = (byte) (0x02 + (recId & 1));
            byte[] xBytes = bigIntTo32Bytes(x);
            System.arraycopy(xBytes, 0, compressedPoint, 1, 32);
            rPoint = CURVE_PARAMS.getCurve().decodePoint(compressedPoint);
        } catch (Exception ex) {
            return null;
        }

        // Q = r^(-1) * (s * R - e * G)
        BigInteger rInv = r.modInverse(n);
        ECPoint q = rPoint.multiply(s).subtract(CURVE_PARAMS.getG().multiply(e)).multiply(rInv);
        return q.normalize();
    }

    /**
     * Convert a BigInteger to exactly 32 bytes (left-padded with zeros).
     */
    private static byte[] bigIntTo32Bytes(BigInteger val) {
        byte[] bytes = val.toByteArray();
        if (bytes.length == 32) return bytes;
        if (bytes.length > 32) {
            // Strip leading zero byte
            byte[] trimmed = new byte[32];
            System.arraycopy(bytes, bytes.length - 32, trimmed, 0, 32);
            return trimmed;
        }
        // Left-pad
        byte[] padded = new byte[32];
        System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length);
        return padded;
    }

    // ── Keystore Encryption (AES-256-GCM) ────────────────────────────────

    private static byte[] encryptKeystore(byte[] plaintext, String password) {
        try {
            SecureRandom random = SecureRandom.getInstanceStrong();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            random.nextBytes(nonce);

            SecretKey key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));

            byte[] ciphertext = cipher.doFinal(plaintext);

            // Format: salt(32) + nonce(12) + ciphertext
            byte[] result = new byte[salt.length + nonce.length + ciphertext.length];
            System.arraycopy(salt, 0, result, 0, salt.length);
            System.arraycopy(nonce, 0, result, salt.length, nonce.length);
            System.arraycopy(ciphertext, 0, result, salt.length + nonce.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new WalletException("Keystore encryption failed: " + e.getMessage());
        }
    }

    private static byte[] decryptKeystore(byte[] encrypted, String password) {
        try {
            if (encrypted.length < SALT_LENGTH + GCM_NONCE_LENGTH + 1) {
                throw new WalletException("Invalid keystore file (too small).");
            }
            byte[] salt = new byte[SALT_LENGTH];
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            System.arraycopy(encrypted, 0, salt, 0, SALT_LENGTH);
            System.arraycopy(encrypted, SALT_LENGTH, nonce, 0, GCM_NONCE_LENGTH);

            byte[] ciphertext = new byte[encrypted.length - SALT_LENGTH - GCM_NONCE_LENGTH];
            System.arraycopy(encrypted, SALT_LENGTH + GCM_NONCE_LENGTH, ciphertext, 0, ciphertext.length);

            SecretKey key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));

            return cipher.doFinal(ciphertext);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new WalletException("Wrong password or corrupted keystore.");
        } catch (WalletException e) {
            throw e;
        } catch (Exception e) {
            throw new WalletException("Keystore decryption failed: " + e.getMessage());
        }
    }

    private static SecretKey deriveKey(String password, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new WalletException("Key derivation failed: " + e.getMessage());
        }
    }

    // ── Minimal RLP Encoding ─────────────────────────────────────────────

    private static byte[] rlpEncodeList(byte[]... items) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (byte[] item : items) {
            byte[] encoded = rlpEncodeBytes(item);
            payload.write(encoded, 0, encoded.length);
        }
        byte[] payloadBytes = payload.toByteArray();
        return rlpLengthPrefix(payloadBytes, 0xc0);
    }

    private static byte[] rlpEncodeBytes(byte[] data) {
        if (data.length == 1 && (data[0] & 0xFF) < 0x80) {
            return data;
        }
        return rlpLengthPrefix(data, 0x80);
    }

    private static byte[] rlpLengthPrefix(byte[] data, int offset) {
        if (data.length < 56) {
            byte[] result = new byte[1 + data.length];
            result[0] = (byte) (offset + data.length);
            System.arraycopy(data, 0, result, 1, data.length);
            return result;
        }
        byte[] lenBytes = bigIntBytes(BigInteger.valueOf(data.length));
        byte[] result = new byte[1 + lenBytes.length + data.length];
        result[0] = (byte) (offset + 55 + lenBytes.length);
        System.arraycopy(lenBytes, 0, result, 1, lenBytes.length);
        System.arraycopy(data, 0, result, 1 + lenBytes.length, data.length);
        return result;
    }

    private static byte[] bigIntBytes(BigInteger val) {
        if (val.signum() == 0) return new byte[0];
        byte[] bytes = val.toByteArray();
        // Strip leading zero byte if present
        if (bytes[0] == 0 && bytes.length > 1) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    // ── Exception ────────────────────────────────────────────────────────

    /**
     * Exception for wallet-related errors.
     */
    public static class WalletException extends RuntimeException {
        public WalletException(String message) {
            super(message);
        }
    }
}
