package dhrlang.deploy;

import dhrlang.evm.FunctionSelector;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * EIP-712 Typed Data Signing for DhrLang contracts.
 *
 * <p>Implements the <a href="https://eips.ethereum.org/EIPS/eip-712">EIP-712</a>
 * structured data hashing and signing standard, enabling:</p>
 * <ul>
 *   <li>Gasless permit approvals (EIP-2612)</li>
 *   <li>Meta-transactions</li>
 *   <li>Off-chain order signing (DEX, NFT marketplaces)</li>
 *   <li>Typed message verification on-chain</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * var eip712 = new Eip712TypedDataSigner();
 * eip712.setDomain("MyToken", "1", 1, "0xContractAddress");
 *
 * var data = new LinkedHashMap<String, Object>();
 * data.put("owner", "0xOwner...");
 * data.put("spender", "0xSpender...");
 * data.put("value", BigInteger.valueOf(1000));
 * data.put("nonce", BigInteger.ZERO);
 * data.put("deadline", BigInteger.valueOf(1700000000));
 *
 * byte[] sig = eip712.sign("Permit", permitTypeHash, data, wallet);
 * }</pre>
 */
public final class Eip712TypedDataSigner {

    // ── EIP-712 Constants ────────────────────────────────────────────────

    /** EIP-712 domain separator type hash. */
    private static final byte[] EIP712_DOMAIN_TYPEHASH = FunctionSelector.keccak256(
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"
                    .getBytes(StandardCharsets.UTF_8));

    /** EIP-712 prefix byte. */
    private static final byte[] EIP712_PREFIX = new byte[]{0x19, 0x01};

    // ── Domain Separator ─────────────────────────────────────────────────

    private String domainName;
    private String domainVersion;
    private long chainId;
    private String verifyingContract;
    private byte[] domainSeparator;

    /**
     * Configure the EIP-712 domain separator.
     *
     * @param name              contract/dApp name
     * @param version           signing domain version
     * @param chainId           EIP-155 chain ID
     * @param verifyingContract the contract address that will verify signatures
     */
    public void setDomain(String name, String version, long chainId, String verifyingContract) {
        this.domainName = name;
        this.domainVersion = version;
        this.chainId = chainId;
        this.verifyingContract = verifyingContract;
        this.domainSeparator = computeDomainSeparator();
    }

    /**
     * Get the domain separator hash.
     */
    public byte[] getDomainSeparator() {
        if (domainSeparator == null) {
            throw new IllegalStateException("Domain not configured. Call setDomain() first.");
        }
        return domainSeparator.clone();
    }

    // ── Type Hash Computation ────────────────────────────────────────────

    /**
     * Compute the type hash for a struct type.
     *
     * @param typeName   the struct type name (e.g., "Permit")
     * @param fieldTypes ordered map of field name → Solidity type (e.g., "address", "uint256")
     * @return keccak256 of the type encoding string
     */
    public static byte[] computeTypeHash(String typeName, LinkedHashMap<String, String> fieldTypes) {
        String typeString = encodeType(typeName, fieldTypes);
        return FunctionSelector.keccak256(typeString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encode a type string per EIP-712 spec.
     * Example: "Permit(address owner,address spender,uint256 value,uint256 nonce,uint256 deadline)"
     */
    public static String encodeType(String typeName, LinkedHashMap<String, String> fieldTypes) {
        StringBuilder sb = new StringBuilder(typeName).append('(');
        int i = 0;
        for (var entry : fieldTypes.entrySet()) {
            if (i > 0) sb.append(',');
            sb.append(entry.getValue()).append(' ').append(entry.getKey());
            i++;
        }
        sb.append(')');
        return sb.toString();
    }

    // ── Struct Hash ──────────────────────────────────────────────────────

    /**
     * Compute the struct hash for a typed data message.
     *
     * @param typeHash the type hash (from {@link #computeTypeHash})
     * @param values   the field values in order (String for address, BigInteger for uint256, etc.)
     * @return keccak256(typeHash ‖ encodeData)
     */
    public static byte[] hashStruct(byte[] typeHash, List<Object> values) {
        byte[] encoded = new byte[32 + values.size() * 32];
        System.arraycopy(typeHash, 0, encoded, 0, 32);

        for (int i = 0; i < values.size(); i++) {
            byte[] word = encodeValue(values.get(i));
            System.arraycopy(word, 0, encoded, 32 + i * 32, 32);
        }

        return FunctionSelector.keccak256(encoded);
    }

    // ── Signing ──────────────────────────────────────────────────────────

    /**
     * Sign a typed data message per EIP-712.
     *
     * <p>Computes: {@code sign(keccak256("\x19\x01" ‖ domainSeparator ‖ structHash))}</p>
     *
     * @param typeHash the type hash for the struct
     * @param values   the field values in order
     * @param wallet   the wallet to sign with (must have a loaded key)
     * @return 65-byte signature (r[32] + s[32] + v[1])
     */
    public byte[] signTypedData(byte[] typeHash, List<Object> values, WalletManager wallet) {
        if (domainSeparator == null) {
            throw new IllegalStateException("Domain not configured. Call setDomain() first.");
        }

        byte[] structHash = hashStruct(typeHash, values);
        byte[] digest = computeDigest(domainSeparator, structHash);

        // Use the wallet's ECDSA signer
        return signDigest(digest, wallet);
    }

    /**
     * Compute the EIP-712 digest: keccak256("\x19\x01" ‖ domainSeparator ‖ structHash)
     */
    public static byte[] computeDigest(byte[] domainSep, byte[] structHash) {
        byte[] message = new byte[2 + 32 + 32];
        message[0] = 0x19;
        message[1] = 0x01;
        System.arraycopy(domainSep, 0, message, 2, 32);
        System.arraycopy(structHash, 0, message, 34, 32);
        return FunctionSelector.keccak256(message);
    }

    // ── Pre-built Type Hashes for Common Standards ───────────────────────

    /**
     * EIP-2612 Permit type hash.
     * Permit(address owner,address spender,uint256 value,uint256 nonce,uint256 deadline)
     */
    public static byte[] permitTypeHash() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("owner", "address");
        fields.put("spender", "address");
        fields.put("value", "uint256");
        fields.put("nonce", "uint256");
        fields.put("deadline", "uint256");
        return computeTypeHash("Permit", fields);
    }

    /**
     * Meta-transaction type hash.
     * ForwardRequest(address from,address to,uint256 value,uint256 gas,uint256 nonce,bytes data)
     */
    public static byte[] metaTxTypeHash() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("from", "address");
        fields.put("to", "address");
        fields.put("value", "uint256");
        fields.put("gas", "uint256");
        fields.put("nonce", "uint256");
        fields.put("data", "bytes");
        return computeTypeHash("ForwardRequest", fields);
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    private byte[] computeDomainSeparator() {
        byte[] nameHash = FunctionSelector.keccak256(domainName.getBytes(StandardCharsets.UTF_8));
        byte[] versionHash = FunctionSelector.keccak256(domainVersion.getBytes(StandardCharsets.UTF_8));

        byte[] encoded = new byte[5 * 32]; // typeHash + nameHash + versionHash + chainId + address
        System.arraycopy(EIP712_DOMAIN_TYPEHASH, 0, encoded, 0, 32);
        System.arraycopy(nameHash, 0, encoded, 32, 32);
        System.arraycopy(versionHash, 0, encoded, 64, 32);

        byte[] chainIdBytes = padTo32(BigInteger.valueOf(chainId).toByteArray());
        System.arraycopy(chainIdBytes, 0, encoded, 96, 32);

        byte[] addressBytes = padTo32(parseAddress(verifyingContract));
        System.arraycopy(addressBytes, 0, encoded, 128, 32);

        return FunctionSelector.keccak256(encoded);
    }

    private static byte[] encodeValue(Object value) {
        if (value instanceof BigInteger bi) {
            return padTo32(bi.toByteArray());
        } else if (value instanceof Long l) {
            return padTo32(BigInteger.valueOf(l).toByteArray());
        } else if (value instanceof Integer i) {
            return padTo32(BigInteger.valueOf(i).toByteArray());
        } else if (value instanceof String s) {
            if (s.startsWith("0x") && s.length() == 42) {
                // Address
                return padTo32(parseAddress(s));
            } else {
                // Dynamic type — hash it
                return FunctionSelector.keccak256(s.getBytes(StandardCharsets.UTF_8));
            }
        } else if (value instanceof byte[] b) {
            // bytes/bytes32
            return FunctionSelector.keccak256(b);
        } else if (value instanceof Boolean bool) {
            return padTo32(new byte[]{(byte) (bool ? 1 : 0)});
        }
        return new byte[32]; // zero
    }

    private static byte[] parseAddress(String address) {
        String hex = address.startsWith("0x") ? address.substring(2) : address;
        byte[] bytes = new byte[20];
        for (int i = 0; i < 20 && i * 2 < hex.length(); i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static byte[] padTo32(byte[] input) {
        byte[] result = new byte[32];
        if (input.length <= 32) {
            System.arraycopy(input, 0, result, 32 - input.length, input.length);
        } else {
            System.arraycopy(input, input.length - 32, result, 0, 32);
        }
        return result;
    }

    private byte[] signDigest(byte[] digest, WalletManager wallet) {
        return wallet.signDigest(digest);
    }
}
