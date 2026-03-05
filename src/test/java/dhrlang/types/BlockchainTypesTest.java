package dhrlang.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BlockchainTypes utility class.
 * Part of Iteration 1: Foundation - Smart Contract Support.
 */
@DisplayName("Blockchain Types Tests")
class BlockchainTypesTest {

    @Nested
    @DisplayName("Type Name Constants Tests")
    class TypeNameConstantsTests {
        
        @Test
        @DisplayName("Address type name is 'Address'")
        void addressTypeName() {
            assertEquals("Address", BlockchainTypes.ADDRESS);
        }
        
        @Test
        @DisplayName("uint256 type name is 'uint256'")
        void uint256TypeName() {
            assertEquals("uint256", BlockchainTypes.UINT256);
        }
        
        @Test
        @DisplayName("int256 type name is 'int256'")
        void int256TypeName() {
            assertEquals("int256", BlockchainTypes.INT256);
        }
        
        @Test
        @DisplayName("bytes32 type name is 'bytes32'")
        void bytes32TypeName() {
            assertEquals("bytes32", BlockchainTypes.BYTES32);
        }
        
        @Test
        @DisplayName("wei type name is 'wei'")
        void weiTypeName() {
            assertEquals("wei", BlockchainTypes.WEI);
        }
        
        @Test
        @DisplayName("mapping type name is 'mapping'")
        void mappingTypeName() {
            assertEquals("mapping", BlockchainTypes.MAPPING);
        }
    }

    @Nested
    @DisplayName("Size Constants Tests")
    class SizeConstantsTests {
        
        @Test
        @DisplayName("Address is 20 bytes")
        void addressSize() {
            assertEquals(20, BlockchainTypes.ADDRESS_SIZE);
        }
        
        @Test
        @DisplayName("uint256 is 32 bytes")
        void uint256Size() {
            assertEquals(32, BlockchainTypes.UINT256_SIZE);
        }
        
        @Test
        @DisplayName("Storage slot is 32 bytes")
        void storageSlotSize() {
            assertEquals(32, BlockchainTypes.STORAGE_SLOT_SIZE);
        }
        
        @Test
        @DisplayName("Address is 160 bits")
        void addressBits() {
            assertEquals(160, BlockchainTypes.ADDRESS_BITS);
        }
        
        @Test
        @DisplayName("uint256 is 256 bits")
        void uint256Bits() {
            assertEquals(256, BlockchainTypes.UINT256_BITS);
        }
    }

    @Nested
    @DisplayName("isBlockchainType Tests")
    class IsBlockchainTypeTests {
        
        @Test
        @DisplayName("Address is a blockchain type")
        void addressIsBlockchainType() {
            assertTrue(BlockchainTypes.isBlockchainType("Address"));
        }
        
        @Test
        @DisplayName("uint256 is a blockchain type")
        void uint256IsBlockchainType() {
            assertTrue(BlockchainTypes.isBlockchainType("uint256"));
        }
        
        @Test
        @DisplayName("int256 is a blockchain type")
        void int256IsBlockchainType() {
            assertTrue(BlockchainTypes.isBlockchainType("int256"));
        }
        
        @Test
        @DisplayName("bytes32 is a blockchain type")
        void bytes32IsBlockchainType() {
            assertTrue(BlockchainTypes.isBlockchainType("bytes32"));
        }
        
        @Test
        @DisplayName("wei is a blockchain type")
        void weiIsBlockchainType() {
            assertTrue(BlockchainTypes.isBlockchainType("wei"));
        }
        
        @Test
        @DisplayName("mapping is a blockchain type")
        void mappingIsBlockchainType() {
            assertTrue(BlockchainTypes.isBlockchainType("mapping(Address → uint256)"));
        }
        
        @Test
        @DisplayName("num is not a blockchain type")
        void numIsNotBlockchainType() {
            assertFalse(BlockchainTypes.isBlockchainType("num"));
        }
        
        @Test
        @DisplayName("sab is not a blockchain type")
        void sabIsNotBlockchainType() {
            assertFalse(BlockchainTypes.isBlockchainType("sab"));
        }
        
        @Test
        @DisplayName("null returns false")
        void nullIsNotBlockchainType() {
            assertFalse(BlockchainTypes.isBlockchainType((String) null));
        }
    }

    @Nested
    @DisplayName("Numeric Type Tests")
    class NumericTypeTests {
        
        @Test
        @DisplayName("uint256 is numeric")
        void uint256IsNumeric() {
            assertTrue(BlockchainTypes.isNumericType("uint256"));
        }
        
        @Test
        @DisplayName("int256 is numeric")
        void int256IsNumeric() {
            assertTrue(BlockchainTypes.isNumericType("int256"));
        }
        
        @Test
        @DisplayName("wei is numeric")
        void weiIsNumeric() {
            assertTrue(BlockchainTypes.isNumericType("wei"));
        }
        
        @Test
        @DisplayName("Address is not numeric")
        void addressIsNotNumeric() {
            assertFalse(BlockchainTypes.isNumericType("Address"));
        }
        
        @Test
        @DisplayName("bytes32 is not numeric")
        void bytes32IsNotNumeric() {
            assertFalse(BlockchainTypes.isNumericType("bytes32"));
        }
    }

    @Nested
    @DisplayName("Signed Type Tests")
    class SignedTypeTests {
        
        @Test
        @DisplayName("int256 is signed")
        void int256IsSigned() {
            assertTrue(BlockchainTypes.isSignedType("int256"));
        }
        
        @Test
        @DisplayName("uint256 is not signed")
        void uint256IsNotSigned() {
            assertFalse(BlockchainTypes.isSignedType("uint256"));
        }
    }

    @Nested
    @DisplayName("Address Validation Tests")
    class AddressValidationTests {
        
        @Test
        @DisplayName("Valid address is accepted")
        void validAddressAccepted() {
            assertTrue(BlockchainTypes.isValidAddress("0x742d35Cc6634C0532925a3b844Bc9e7595f8fE00"));
        }
        
        @Test
        @DisplayName("Address with all lowercase is valid")
        void lowercaseAddressValid() {
            assertTrue(BlockchainTypes.isValidAddress("0x742d35cc6634c0532925a3b844bc9e7595f8fe00"));
        }
        
        @Test
        @DisplayName("Address with all uppercase is valid")
        void uppercaseAddressValid() {
            assertTrue(BlockchainTypes.isValidAddress("0X742D35CC6634C0532925A3B844BC9E7595F8FE00"));
        }
        
        @Test
        @DisplayName("Too short address is rejected")
        void tooShortAddressRejected() {
            assertFalse(BlockchainTypes.isValidAddress("0x123"));
        }
        
        @Test
        @DisplayName("Too long address is rejected")
        void tooLongAddressRejected() {
            assertFalse(BlockchainTypes.isValidAddress("0x742d35Cc6634C0532925a3b844Bc9e7595f8fE00AA"));
        }
        
        @Test
        @DisplayName("Missing 0x prefix is rejected")
        void missingPrefixRejected() {
            assertFalse(BlockchainTypes.isValidAddress("742d35Cc6634C0532925a3b844Bc9e7595f8fE00"));
        }
        
        @Test
        @DisplayName("Invalid hex character is rejected")
        void invalidHexCharRejected() {
            assertFalse(BlockchainTypes.isValidAddress("0x742d35Cg6634C0532925a3b844Bc9e7595f8fE00"));
        }
        
        @Test
        @DisplayName("Null address is rejected")
        void nullAddressRejected() {
            assertFalse(BlockchainTypes.isValidAddress(null));
        }
    }

    @Nested
    @DisplayName("Bytes32 Validation Tests")
    class Bytes32ValidationTests {
        
        @Test
        @DisplayName("Valid bytes32 is accepted")
        void validBytes32Accepted() {
            assertTrue(BlockchainTypes.isValidBytes32(
                "0x0000000000000000000000000000000000000000000000000000000000000001"));
        }
        
        @Test
        @DisplayName("Too short bytes32 is rejected")
        void tooShortBytes32Rejected() {
            assertFalse(BlockchainTypes.isValidBytes32("0x0001"));
        }
        
        @Test
        @DisplayName("Missing prefix bytes32 is rejected")
        void missingPrefixBytes32Rejected() {
            assertFalse(BlockchainTypes.isValidBytes32(
                "0000000000000000000000000000000000000000000000000000000000000001"));
        }
    }

    @Nested
    @DisplayName("Mapping Type Tests")
    class MappingTypeTests {
        
        @Test
        @DisplayName("mapping(Address → uint256) is a mapping type")
        void mappingTypeDetected() {
            assertTrue(BlockchainTypes.isMappingType("mapping(Address → uint256)"));
        }
        
        @Test
        @DisplayName("uint256 is not a mapping type")
        void nonMappingTypeRejected() {
            assertFalse(BlockchainTypes.isMappingType("uint256"));
        }
        
        @Test
        @DisplayName("Parse mapping type with arrow")
        void parseMappingWithArrow() {
            BlockchainTypes.MappingType mt = BlockchainTypes.parseMappingType("mapping(Address → uint256)");
            assertEquals("Address", mt.getKeyType());
            assertEquals("uint256", mt.getValueType());
        }
        
        @Test
        @DisplayName("Parse mapping type with ASCII arrow")
        void parseMappingWithAsciiArrow() {
            BlockchainTypes.MappingType mt = BlockchainTypes.parseMappingType("mapping(Address -> uint256)");
            assertEquals("Address", mt.getKeyType());
            assertEquals("uint256", mt.getValueType());
        }
        
        @Test
        @DisplayName("Mapping toString produces correct format")
        void mappingToString() {
            BlockchainTypes.MappingType mt = new BlockchainTypes.MappingType("Address", "uint256");
            assertEquals("mapping(Address → uint256)", mt.toString());
        }
    }

    @Nested
    @DisplayName("Solidity Type Conversion Tests")
    class SolidityTypeConversionTests {
        
        @Test
        @DisplayName("Address converts to address")
        void addressToSolidity() {
            assertEquals("address", BlockchainTypes.toSolidityType("Address"));
        }
        
        @Test
        @DisplayName("uint256 converts to uint256")
        void uint256ToSolidity() {
            assertEquals("uint256", BlockchainTypes.toSolidityType("uint256"));
        }
        
        @Test
        @DisplayName("int256 converts to int256")
        void int256ToSolidity() {
            assertEquals("int256", BlockchainTypes.toSolidityType("int256"));
        }
        
        @Test
        @DisplayName("wei converts to uint256")
        void weiToSolidity() {
            assertEquals("uint256", BlockchainTypes.toSolidityType("wei"));
        }
        
        @Test
        @DisplayName("bytes32 converts to bytes32")
        void bytes32ToSolidity() {
            assertEquals("bytes32", BlockchainTypes.toSolidityType("bytes32"));
        }
        
        @Test
        @DisplayName("Non-blockchain type returns as-is")
        void nonBlockchainTypeReturnedAsIs() {
            assertEquals("num", BlockchainTypes.toSolidityType("num"));
        }
        
        @Test
        @DisplayName("null returns null")
        void nullReturnsNull() {
            assertNull(BlockchainTypes.toSolidityType(null));
        }
    }

    @Nested
    @DisplayName("Storage Size Tests")
    class StorageSizeTests {
        
        @Test
        @DisplayName("Address storage size is 20")
        void addressStorageSize() {
            assertEquals(20, BlockchainTypes.getStorageSize("Address"));
        }
        
        @Test
        @DisplayName("uint256 storage size is 32")
        void uint256StorageSize() {
            assertEquals(32, BlockchainTypes.getStorageSize("uint256"));
        }
        
        @Test
        @DisplayName("mapping storage size is 32")
        void mappingStorageSize() {
            assertEquals(32, BlockchainTypes.getStorageSize("mapping(Address → uint256)"));
        }
        
        @Test
        @DisplayName("Unknown type throws exception")
        void unknownTypeThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                BlockchainTypes.getStorageSize("unknown");
            });
        }
        
        @Test
        @DisplayName("Null type throws exception")
        void nullTypeThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                BlockchainTypes.getStorageSize(null);
            });
        }
    }
}
