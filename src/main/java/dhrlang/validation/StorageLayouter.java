package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.types.BlockchainTypes;

import java.util.*;

/**
 * Assigns deterministic storage slot indices to {@code @storage} fields
 * in {@code @contract} classes.
 * 
 * <p>Each {@code @storage} field is assigned a sequential slot number starting
 * from 0. In EVM, every storage slot is 32 bytes (256 bits). This layouter
 * assigns one slot per field (no packing), matching Solidity's layout for
 * 256-bit types.</p>
 * 
 * <p>Mappings and arrays each consume one base slot; their actual data is
 * hashed to different locations at runtime (standard EVM storage model).</p>
 * 
 * <p>The computed layout can be queried after calling {@link #layoutAll(Program)}.
 * </p>
 */
public class StorageLayouter {
    
    /** Maximum number of storage slots per contract before warning (DHR-E535). */
    public static final int MAX_STORAGE_SLOTS = 2048;
    
    /**
     * Information about a single storage slot assignment.
     */
    public static class SlotInfo {
        private final String fieldName;
        private final String fieldType;
        private final int slotIndex;
        private final int sizeInBytes;
        
        public SlotInfo(String fieldName, String fieldType, int slotIndex, int sizeInBytes) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.slotIndex = slotIndex;
            this.sizeInBytes = sizeInBytes;
        }
        
        public String getFieldName() { return fieldName; }
        public String getFieldType() { return fieldType; }
        public int getSlotIndex() { return slotIndex; }
        public int getSizeInBytes() { return sizeInBytes; }
        
        @Override
        public String toString() {
            return "Slot[" + slotIndex + "] " + fieldType + " " + fieldName + " (" + sizeInBytes + " bytes)";
        }
    }
    
    /**
     * The complete storage layout for a single contract.
     */
    public static class ContractLayout {
        private final String contractName;
        private final List<SlotInfo> slots;
        private final int totalSlots;
        
        public ContractLayout(String contractName, List<SlotInfo> slots) {
            this.contractName = contractName;
            this.slots = Collections.unmodifiableList(slots);
            this.totalSlots = slots.size();
        }
        
        public String getContractName() { return contractName; }
        public List<SlotInfo> getSlots() { return slots; }
        public int getTotalSlots() { return totalSlots; }
        
        /**
         * Get the slot info for a specific field, or null if not found.
         */
        public SlotInfo getSlotFor(String fieldName) {
            for (SlotInfo slot : slots) {
                if (slot.getFieldName().equals(fieldName)) {
                    return slot;
                }
            }
            return null;
        }
        
        /**
         * Check if a field has been assigned a storage slot.
         */
        public boolean hasSlot(String fieldName) {
            return getSlotFor(fieldName) != null;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("StorageLayout[").append(contractName).append("] {\n");
            for (SlotInfo slot : slots) {
                sb.append("  ").append(slot).append("\n");
            }
            sb.append("} total=").append(totalSlots).append(" slots");
            return sb.toString();
        }
    }
    
    /** Stores the computed layout per contract name. */
    private final Map<String, ContractLayout> layouts = new LinkedHashMap<>();
    
    /** Contracts that exceeded the storage slot limit. */
    private final List<String> overflowContracts = new ArrayList<>();
    
    /**
     * Compute storage layouts for all contracts in the program.
     */
    public void layoutAll(Program program) {
        layouts.clear();
        overflowContracts.clear();
        
        for (ClassDecl classDecl : program.getClasses()) {
            if (classDecl.isContract()) {
                ContractLayout layout = layoutContract(classDecl);
                layouts.put(classDecl.getName(), layout);
                
                if (layout.getTotalSlots() > MAX_STORAGE_SLOTS) {
                    overflowContracts.add(classDecl.getName());
                }
            }
        }
    }
    
    /**
     * Compute the storage layout for a single contract.
     */
    private ContractLayout layoutContract(ClassDecl contract) {
        List<SlotInfo> slots = new ArrayList<>();
        int slotIndex = 0;
        
        for (VarDecl field : contract.getVariables()) {
            if (field.isStorage()) {
                int size = getFieldStorageSize(field.getType());
                slots.add(new SlotInfo(field.getName(), field.getType(), slotIndex, size));
                slotIndex++;
            }
        }
        
        return new ContractLayout(contract.getName(), slots);
    }
    
    /**
     * Determine the storage size for a field type. All types get one 32-byte slot.
     * 
     * @param type the DhrLang type name
     * @return size in bytes (always 32 for a slot-based layout)
     */
    private int getFieldStorageSize(String type) {
        try {
            return BlockchainTypes.getStorageSize(type);
        } catch (IllegalArgumentException e) {
            // Non-blockchain types (num, sab, kya, etc.) also get one slot
            return BlockchainTypes.STORAGE_SLOT_SIZE;
        }
    }
    
    /**
     * Get the layout for a specific contract, or null if not computed.
     */
    public ContractLayout getLayout(String contractName) {
        return layouts.get(contractName);
    }
    
    /**
     * Get all computed layouts.
     */
    public Map<String, ContractLayout> getAllLayouts() {
        return Collections.unmodifiableMap(layouts);
    }
    
    /**
     * Check if any contracts exceeded the storage slot limit.
     */
    public boolean hasOverflow() {
        return !overflowContracts.isEmpty();
    }
    
    /**
     * Get the names of contracts that exceeded the storage slot limit.
     */
    public List<String> getOverflowContracts() {
        return Collections.unmodifiableList(overflowContracts);
    }
}
