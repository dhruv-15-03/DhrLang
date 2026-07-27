package dhrlang.evm;

import dhrlang.types.BlockchainTypes;

import java.math.BigInteger;

/**
 * Encodes storage slot positions for contract state variables.
 * 
 * <p>Follows the Solidity storage layout model:
 * <ul>
 *   <li>Simple value types: sequential slots starting at 0</li>
 *   <li>Mappings: base slot is the declared position; actual storage is at
 *       {@code keccak256(key . baseSlot)}</li>
 *   <li>Dynamic arrays: base slot stores the length; elements start at
 *       {@code keccak256(baseSlot)}</li>
 * </ul>
 */
public final class StorageEncoder {

    private StorageEncoder() {}

    /**
     * Compute the storage slot for a mapping entry: keccak256(abi.encode(key, baseSlot)).
     * 
     * @param key     the mapping key as a 32-byte big-endian value
     * @param baseSlot the base slot assigned to the mapping variable
     * @return the 32-byte slot identifier
     */
    public static byte[] mappingSlot(byte[] key, int baseSlot) {
        byte[] data = new byte[64];
        // First 32 bytes: key (left-padded)
        System.arraycopy(leftPad32(key), 0, data, 0, 32);
        // Second 32 bytes: base slot (left-padded)
        byte[] slotBytes = BigInteger.valueOf(baseSlot).toByteArray();
        System.arraycopy(leftPad32(slotBytes), 0, data, 32, 32);
        return FunctionSelector.keccak256(data);
    }

    /**
     * Compute the mapping slot for an address key.
     */
    public static byte[] mappingSlotForAddress(String address, int baseSlot) {
        byte[] addrBytes = hexToBytes(address.startsWith("0x") ? address.substring(2) : address);
        return mappingSlot(addrBytes, baseSlot);
    }

    /**
     * Compute the mapping slot for a uint256 key.
     */
    public static byte[] mappingSlotForUint(BigInteger key, int baseSlot) {
        return mappingSlot(uint256ToBytes(key), baseSlot);
    }

    /**
     * Compute the storage slot for a dynamic array element.
     * Elements start at keccak256(baseSlot), each element at +index.
     *
     * @param baseSlot the slot storing the array length
     * @param index    the array element index
     * @return the 32-byte slot for the element
     */
    public static byte[] dynamicArraySlot(int baseSlot, long index) {
        byte[] slotBytes = leftPad32(BigInteger.valueOf(baseSlot).toByteArray());
        byte[] baseHash = FunctionSelector.keccak256(slotBytes);
        BigInteger base = new BigInteger(1, baseHash);
        BigInteger elementSlot = base.add(BigInteger.valueOf(index));
        return uint256ToBytes(elementSlot);
    }

    /**
     * Encode a uint256 value to 32-byte big-endian representation.
     */
    public static byte[] uint256ToBytes(BigInteger value) {
        byte[] raw = value.toByteArray();
        return leftPad32(raw);
    }

    /**
     * Encode an address to 32-byte representation (20-byte address left-padded to 32).
     */
    public static byte[] addressToBytes32(String address) {
        String hex = address.startsWith("0x") ? address.substring(2) : address;
        byte[] addrBytes = hexToBytes(hex);
        return leftPad32(addrBytes);
    }

    /**
     * ABI-encode a single uint256 value (32 bytes, big-endian).
     */
    public static byte[] abiEncodeUint256(BigInteger value) {
        return uint256ToBytes(value);
    }

    /**
     * ABI-encode an address (padded to 32 bytes).
     */
    public static byte[] abiEncodeAddress(String address) {
        return addressToBytes32(address);
    }

    /**
     * ABI-encode multiple values (concatenate 32-byte chunks).
     */
    public static byte[] abiEncode(byte[]... values) {
        byte[] result = new byte[values.length * 32];
        for (int i = 0; i < values.length; i++) {
            byte[] padded = leftPad32(values[i]);
            System.arraycopy(padded, 0, result, i * 32, 32);
        }
        return result;
    }

    /**
     * Compute the gas cost for an SSTORE operation.
     * Simplified model:
     * - 0 → non-zero: 20,000 gas
     * - non-zero → non-zero: 5,000 gas
     * - non-zero → 0: 5,000 gas (with 15,000 refund)
     */
    public static int sstoreGasCost(boolean isZeroToNonZero) {
        return isZeroToNonZero ? 20000 : 5000;
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    static byte[] leftPad32(byte[] input) {
        if (input.length == 32) return input;
        byte[] padded = new byte[32];
        if (input.length > 32) {
            // Take rightmost 32 bytes (BigInteger can prepend a sign byte)
            System.arraycopy(input, input.length - 32, padded, 0, 32);
        } else {
            System.arraycopy(input, 0, padded, 32 - input.length, input.length);
        }
        return padded;
    }

    static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
