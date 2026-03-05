package dhrlang.types;

import dhrlang.lexer.TokenType;

/**
 * Defines blockchain-specific types for DhrLang smart contracts.
 * These types map to EVM-compatible data types.
 * 
 * <p>Type mapping to EVM:
 * <ul>
 *   <li>Address → 20 bytes (160 bits) - Ethereum account/contract address</li>
 *   <li>uint256 → 32 bytes (256 bits) - Unsigned integer, most common for balances</li>
 *   <li>int256 → 32 bytes (256 bits) - Signed integer</li>
 *   <li>bytes32 → 32 bytes - Fixed-size byte array, used for hashes</li>
 *   <li>wei → uint256 - ETH denomination (1 ETH = 10^18 wei)</li>
 *   <li>mapping(K → V) → Storage mapping - Key-value storage</li>
 * </ul>
 * 
 * <p>Usage in DhrLang:
 * <pre>
 * @contract
 * class Token {
 *     @storage Address owner;
 *     @storage uint256 totalSupply;
 *     @storage mapping(Address → uint256) balances;
 *     
 *     @constructor
 *     kaam init() {
 *         owner = msg.sender;
 *         totalSupply = 1000000;
 *     }
 * }
 * </pre>
 */
public final class BlockchainTypes {
    
    // Type name constants
    public static final String ADDRESS = "Address";
    public static final String UINT256 = "uint256";
    public static final String INT256 = "int256";
    public static final String BYTES32 = "bytes32";
    public static final String WEI = "wei";
    public static final String MAPPING = "mapping";
    
    // Size constants (in bytes)
    public static final int ADDRESS_SIZE = 20;
    public static final int UINT256_SIZE = 32;
    public static final int INT256_SIZE = 32;
    public static final int BYTES32_SIZE = 32;
    public static final int STORAGE_SLOT_SIZE = 32;
    
    // Bit size constants
    public static final int ADDRESS_BITS = 160;
    public static final int UINT256_BITS = 256;
    public static final int INT256_BITS = 256;
    
    // Value range constants for uint256
    public static final String UINT256_MAX = "115792089237316195423570985008687907853269984665640564039457584007913129639935";
    public static final String UINT256_MIN = "0";
    
    // Value range constants for int256
    public static final String INT256_MAX = "57896044618658097711785492504343953926634992332820282019728792003956564819967";
    public static final String INT256_MIN = "-57896044618658097711785492504343953926634992332820282019728792003956564819968";
    
    private BlockchainTypes() {
        // Prevent instantiation
    }
    
    /**
     * Check if a type name is a blockchain type.
     */
    public static boolean isBlockchainType(String typeName) {
        return typeName != null && (
            typeName.equals(ADDRESS) ||
            typeName.equals(UINT256) ||
            typeName.equals(INT256) ||
            typeName.equals(BYTES32) ||
            typeName.equals(WEI) ||
            typeName.startsWith(MAPPING)
        );
    }
    
    /**
     * Check if a TokenType represents a blockchain type.
     */
    public static boolean isBlockchainType(TokenType tokenType) {
        return tokenType == TokenType.ADDRESS ||
               tokenType == TokenType.UINT256 ||
               tokenType == TokenType.INT256 ||
               tokenType == TokenType.BYTES32 ||
               tokenType == TokenType.WEI ||
               tokenType == TokenType.MAPPING;
    }
    
    /**
     * Get the storage size in bytes for a blockchain type.
     * All types fit in a single 32-byte storage slot.
     */
    public static int getStorageSize(String typeName) {
        if (typeName == null) {
            throw new IllegalArgumentException("Type name cannot be null");
        }
        
        switch (typeName) {
            case ADDRESS:
                return ADDRESS_SIZE;
            case UINT256:
            case INT256:
            case BYTES32:
            case WEI:
                return STORAGE_SLOT_SIZE;
            default:
                if (typeName.startsWith(MAPPING)) {
                    return STORAGE_SLOT_SIZE; // Mappings use a slot for the base
                }
                throw new IllegalArgumentException("Unknown blockchain type: " + typeName);
        }
    }
    
    /**
     * Check if a type is numeric (can be used in arithmetic).
     */
    public static boolean isNumericType(String typeName) {
        return UINT256.equals(typeName) || INT256.equals(typeName) || WEI.equals(typeName);
    }
    
    /**
     * Check if a type is signed.
     */
    public static boolean isSignedType(String typeName) {
        return INT256.equals(typeName);
    }
    
    /**
     * Check if a type is a mapping type.
     */
    public static boolean isMappingType(String typeName) {
        return typeName != null && typeName.startsWith(MAPPING);
    }
    
    /**
     * Convert a DhrLang type to its Solidity equivalent.
     */
    public static String toSolidityType(String typeName) {
        if (typeName == null) {
            return null;
        }
        
        switch (typeName) {
            case ADDRESS:
                return "address";
            case UINT256:
            case WEI:
                return "uint256";
            case INT256:
                return "int256";
            case BYTES32:
                return "bytes32";
            default:
                if (typeName.startsWith(MAPPING)) {
                    // Convert mapping(Address → uint256) to mapping(address => uint256)
                    return typeName.replace("Address", "address")
                                  .replace("uint256", "uint256")
                                  .replace("→", "=>");
                }
                return typeName; // Return as-is for non-blockchain types
        }
    }
    
    /**
     * Validate an Address literal.
     * Address must be 40 hex characters prefixed with 0x.
     */
    public static boolean isValidAddress(String address) {
        if (address == null || address.length() != 42) {
            return false;
        }
        if (!address.startsWith("0x") && !address.startsWith("0X")) {
            return false;
        }
        // Check all characters are valid hex
        for (int i = 2; i < address.length(); i++) {
            char c = address.charAt(i);
            if (!isHexDigit(c)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Validate a bytes32 literal.
     * bytes32 must be 64 hex characters prefixed with 0x.
     */
    public static boolean isValidBytes32(String bytes32) {
        if (bytes32 == null || bytes32.length() != 66) {
            return false;
        }
        if (!bytes32.startsWith("0x") && !bytes32.startsWith("0X")) {
            return false;
        }
        for (int i = 2; i < bytes32.length(); i++) {
            char c = bytes32.charAt(i);
            if (!isHexDigit(c)) {
                return false;
            }
        }
        return true;
    }
    
    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') ||
               (c >= 'a' && c <= 'f') ||
               (c >= 'A' && c <= 'F');
    }
    
    /**
     * Represents a parsed mapping type.
     */
    public static class MappingType {
        private final String keyType;
        private final String valueType;
        
        public MappingType(String keyType, String valueType) {
            this.keyType = keyType;
            this.valueType = valueType;
        }
        
        public String getKeyType() {
            return keyType;
        }
        
        public String getValueType() {
            return valueType;
        }
        
        @Override
        public String toString() {
            return "mapping(" + keyType + " → " + valueType + ")";
        }
    }
    
    /**
     * Parse a mapping type string into its components.
     * Example: "mapping(Address → uint256)" → MappingType("Address", "uint256")
     */
    public static MappingType parseMappingType(String typeName) {
        if (!isMappingType(typeName)) {
            throw new IllegalArgumentException("Not a mapping type: " + typeName);
        }
        
        // Remove "mapping(" prefix and ")" suffix
        String inner = typeName.substring(8, typeName.length() - 1);
        
        // Split by arrow (→ or ->)
        String[] parts;
        if (inner.contains("→")) {
            parts = inner.split("\\s*→\\s*");
        } else if (inner.contains("->")) {
            parts = inner.split("\\s*->\\s*");
        } else {
            throw new IllegalArgumentException("Invalid mapping syntax: " + typeName);
        }
        
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid mapping syntax: " + typeName);
        }
        
        return new MappingType(parts[0].trim(), parts[1].trim());
    }
}
