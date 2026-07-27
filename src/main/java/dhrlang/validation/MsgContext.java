package dhrlang.validation;

import dhrlang.types.BlockchainTypes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Defines the blockchain context globals available within smart contracts.
 * 
 * <p>In Ethereum/EVM smart contracts, the transaction context is accessible via:
 * <ul>
 *   <li>{@code msg.sender} - Address of the account that called the contract</li>
 *   <li>{@code msg.value} - Amount of ETH (in wei) sent with the transaction</li>
 *   <li>{@code msg.data} - Complete calldata; supports {@code msg.data.length}</li>
 *   <li>{@code msg.sig} - First four bytes of the calldata (the function selector)</li>
 *   <li>{@code block.timestamp} - Current block timestamp (seconds since epoch)</li>
 *   <li>{@code block.number} - Current block number</li>
 * </ul>
 * 
 * <p>DhrLang usage:
 * <pre>
 * &#64;contract
 * class Token {
 *     &#64;storage Address owner;
 *     
 *     &#64;constructor
 *     kaam init() {
 *         owner = msg.sender;
 *     }
 *     
 *     &#64;payable
 *     kaam deposit() {
 *         // msg.value contains the ETH amount
 *     }
 * }
 * </pre>
 */
public final class MsgContext {
    
    /**
     * Synthetic type name for the 'msg' global object.
     * This is not a real DhrLang type but used internally by the type checker.
     */
    public static final String MSG_TYPE = "$MsgContext";
    
    /**
     * Synthetic type name for the 'block' global object.
     */
    public static final String BLOCK_TYPE = "$BlockContext";

    /**
     * Synthetic type name for the value of {@code msg.data} (the transaction
     * calldata). It is not a real DhrLang value type: only {@code .length}
     * (a uint256) may be read from it. Using {@code msg.data} as a bare scalar
     * is rejected by the type checker.
     */
    public static final String CALLDATA_TYPE = "$CallData";
    
    // msg properties and their types
    private static final Map<String, String> MSG_PROPERTIES;
    static {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("sender", BlockchainTypes.ADDRESS);
        props.put("value", BlockchainTypes.UINT256);
        props.put("data", CALLDATA_TYPE);
        props.put("sig", BlockchainTypes.UINT256);
        MSG_PROPERTIES = Collections.unmodifiableMap(props);
    }

    // msg.data properties and their types
    private static final Map<String, String> CALLDATA_PROPERTIES;
    static {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("length", BlockchainTypes.UINT256);
        CALLDATA_PROPERTIES = Collections.unmodifiableMap(props);
    }
    
    // block properties and their types
    private static final Map<String, String> BLOCK_PROPERTIES;
    static {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("timestamp", BlockchainTypes.UINT256);
        props.put("number", BlockchainTypes.UINT256);
        BLOCK_PROPERTIES = Collections.unmodifiableMap(props);
    }
    
    private MsgContext() {
        // Prevent instantiation
    }
    
    /**
     * Get the type of a msg property (e.g., "sender" → "Address").
     * @return the type string, or null if the property doesn't exist
     */
    public static String getMsgPropertyType(String propertyName) {
        return MSG_PROPERTIES.get(propertyName);
    }

    /**
     * Get the type of a msg.data property (e.g., "length" → "uint256").
     * @return the type string, or null if the property doesn't exist
     */
    public static String getCallDataPropertyType(String propertyName) {
        return CALLDATA_PROPERTIES.get(propertyName);
    }
    
    /**
     * Get the type of a block property (e.g., "timestamp" → "uint256").
     * @return the type string, or null if the property doesn't exist
     */
    public static String getBlockPropertyType(String propertyName) {
        return BLOCK_PROPERTIES.get(propertyName);
    }
    
    /**
     * Get all available msg property names.
     */
    public static Set<String> getMsgPropertyNames() {
        return MSG_PROPERTIES.keySet();
    }
    
    /**
     * Get all available block property names.
     */
    public static Set<String> getBlockPropertyNames() {
        return BLOCK_PROPERTIES.keySet();
    }
    
    /**
     * Check if a name is a contract global (msg or block).
     */
    public static boolean isContractGlobal(String name) {
        return "msg".equals(name) || "block".equals(name);
    }
    
    /**
     * Check if a property access is a msg property.
     */
    public static boolean isMsgProperty(String objectType, String propertyName) {
        return MSG_TYPE.equals(objectType) && MSG_PROPERTIES.containsKey(propertyName);
    }
    
    /**
     * Check if a property access is a block property.
     */
    public static boolean isBlockProperty(String objectType, String propertyName) {
        return BLOCK_TYPE.equals(objectType) && BLOCK_PROPERTIES.containsKey(propertyName);
    }
}
